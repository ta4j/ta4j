/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.event.EventMutualInformationConfig;
import org.ta4j.core.analysis.event.EventMutualInformationEvaluator;
import org.ta4j.core.analysis.event.EventMutualInformationResult;
import org.ta4j.core.indicators.statistics.DynamicTimeWarpingDistanceIndicator;
import org.ta4j.core.indicators.statistics.LeadLagCorrelationIndicator;
import org.ta4j.core.indicators.statistics.event.EventSynchronizationIndicator;
import org.ta4j.core.num.Num;

/**
 * Objective functions integrating the advanced relationship metrics (event
 * synchronization, lead/lag correlation, dynamic time warping, and event mutual
 * information) with the unified parameter search workflow.
 *
 * <p>
 * The searched indicator arrives from the workflow's candidate factory; each
 * factory only takes the {@link Function} that builds the reference (or target)
 * indicator from a window sub-series. Reference builders are applied to exactly
 * the evaluation window's sub-series, so no evaluation can read bars outside
 * its window (leakage isolation). Undefined results are reported as failed
 * evaluations with a factual reason instead of silently ranking {@code NaN}
 * values.
 * </p>
 *
 * @since 0.24.2
 */
final class RelationshipObjectives {

    private RelationshipObjectives() {
    }

    /**
     * Maximizes the event-synchronization F1 score between a predicted event stream
     * and a reference event stream over a rolling window.
     *
     * @param referenceBuilder builds the reference-event indicator from a window
     *                         sub-series
     * @param barCount         rolling window size in bars
     * @param toleranceBars    symmetric event-matching tolerance in bars
     * @return objective function over the predicted-event indicator
     * @throws NullPointerException     if the reference builder is null
     * @throws IllegalArgumentException if {@code barCount} or {@code toleranceBars}
     *                                  is not positive
     * @since 0.24.2
     */
    static ParameterResearch.ObjectiveFunction<Indicator<Boolean>> eventSynchronizationF1(
            Function<BarSeries, Indicator<Boolean>> referenceBuilder, int barCount, int toleranceBars) {
        Objects.requireNonNull(referenceBuilder, "referenceBuilder");
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be > 0");
        }
        if (toleranceBars <= 0) {
            throw new IllegalArgumentException("toleranceBars must be > 0");
        }
        return (predicted, window) -> {
            try {
                Indicator<Boolean> reference = referenceBuilder.apply(window.series());
                EventSynchronizationIndicator synchronization = new EventSynchronizationIndicator(predicted, reference,
                        barCount, toleranceBars);
                EventSynchronizationIndicator.Result result = synchronization.getResult(window.series().getEndIndex());
                if (result.predictedCount() == 0 && result.referenceCount() == 0) {
                    return ParameterResearch.ObjectiveEvaluation.failed("no events in the evaluation window");
                }
                Num f1Score = result.f1Score();
                if (!Num.isFinite(f1Score)) {
                    return ParameterResearch.ObjectiveEvaluation
                            .failed("F1 score is undefined in the evaluation window");
                }
                return ParameterResearch.ObjectiveEvaluation.of(f1Score,
                        finiteMetrics("precision", result.precision(), "recall", result.recall()));
            } catch (RuntimeException ex) {
                return ParameterResearch.ObjectiveEvaluation
                        .failed("event synchronization evaluation failed" + message(ex));
            }
        };
    }

    /**
     * Maximizes the absolute lead/lag correlation between a searched indicator and
     * a reference indicator over a rolling window.
     *
     * <p>
     * The lag is selected by
     * {@link LeadLagCorrelationIndicator.LagSelectionPolicy#MAXIMUM_ABSOLUTE_CORRELATION};
     * the reported correlation keeps its original sign.
     * </p>
     *
     * @param referenceBuilder builds the reference indicator from a window
     *                         sub-series
     * @param barCount         aligned-sample window size in bars
     * @param minimumLag       inclusive lower lag bound
     * @param maximumLag       inclusive upper lag bound
     * @return objective function over the searched indicator
     * @throws NullPointerException     if the reference builder is null
     * @throws IllegalArgumentException if {@code barCount <= 0} or
     *                                  {@code minimumLag > maximumLag}
     * @since 0.24.2
     */
    static ParameterResearch.ObjectiveFunction<Indicator<Num>> leadLagCorrelation(
            Function<BarSeries, Indicator<Num>> referenceBuilder, int barCount, int minimumLag, int maximumLag) {
        Objects.requireNonNull(referenceBuilder, "referenceBuilder");
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be > 0");
        }
        if (minimumLag > maximumLag) {
            throw new IllegalArgumentException("minimumLag cannot be greater than maximumLag");
        }
        return (candidate, window) -> {
            try {
                Indicator<Num> reference = referenceBuilder.apply(window.series());
                LeadLagCorrelationIndicator indicator = new LeadLagCorrelationIndicator(candidate, reference, barCount,
                        minimumLag, maximumLag,
                        LeadLagCorrelationIndicator.LagSelectionPolicy.MAXIMUM_ABSOLUTE_CORRELATION);
                LeadLagCorrelationIndicator.Profile profile = indicator.getProfile(window.series().getEndIndex());
                if (profile.selectedLag().isEmpty()) {
                    return ParameterResearch.ObjectiveEvaluation.failed("no defined lag in the evaluation window");
                }
                Num correlation = profile.selectedCorrelation();
                if (correlation == null || !Num.isFinite(correlation)) {
                    return ParameterResearch.ObjectiveEvaluation
                            .failed("selected correlation is undefined in the evaluation window");
                }
                int selectedLag = profile.selectedLag().getAsInt();
                int sampleCount = profile.points()
                        .stream()
                        .filter(point -> point.lag() == selectedLag)
                        .findFirst()
                        .map(LeadLagCorrelationIndicator.Point::sampleCount)
                        .orElse(0);
                Map<String, Num> metrics = new LinkedHashMap<>();
                metrics.put("selectedLag", window.series().numFactory().numOf(selectedLag));
                metrics.put("sampleCount", window.series().numFactory().numOf(sampleCount));
                return ParameterResearch.ObjectiveEvaluation.of(correlation, metrics);
            } catch (RuntimeException ex) {
                return ParameterResearch.ObjectiveEvaluation
                        .failed("lead/lag correlation evaluation failed" + message(ex));
            }
        };
    }

    /**
     * Minimizes the dynamic time warping distance between a searched indicator and
     * a reference indicator over a rolling window.
     *
     * @param referenceBuilder builds the reference indicator from a window
     *                         sub-series
     * @param barCount         rolling window size in bars
     * @param config           distance configuration
     * @return objective function over the searched indicator
     * @throws NullPointerException     if the reference builder or the config is
     *                                  null
     * @throws IllegalArgumentException if {@code barCount <= 0}
     * @since 0.24.2
     */
    static ParameterResearch.ObjectiveFunction<Indicator<Num>> dynamicTimeWarpingDistance(
            Function<BarSeries, Indicator<Num>> referenceBuilder, int barCount,
            DynamicTimeWarpingDistanceIndicator.Config config) {
        Objects.requireNonNull(referenceBuilder, "referenceBuilder");
        Objects.requireNonNull(config, "config");
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be > 0");
        }
        return (candidate, window) -> {
            try {
                Indicator<Num> reference = referenceBuilder.apply(window.series());
                DynamicTimeWarpingDistanceIndicator indicator = new DynamicTimeWarpingDistanceIndicator(candidate,
                        reference, barCount, config);
                Num distance = indicator.getValue(window.series().getEndIndex());
                if (!Num.isFinite(distance)) {
                    return ParameterResearch.ObjectiveEvaluation
                            .failed("distance is undefined in the evaluation window");
                }
                return ParameterResearch.ObjectiveEvaluation.of(distance);
            } catch (RuntimeException ex) {
                return ParameterResearch.ObjectiveEvaluation
                        .failed("dynamic time warping evaluation failed" + message(ex));
            }
        };
    }

    /**
     * Maximizes the mutual information between a searched continuous predictor and
     * a Boolean event target over an explicit bar window.
     *
     * @param targetBuilder builds the Boolean event-target indicator from a window
     *                      sub-series
     * @param config        binning and target-window configuration
     * @param useNormalized whether to score the normalized mutual information
     *                      instead of the raw mutual information in nats
     * @return objective function over the predictor indicator
     * @throws NullPointerException if the target builder or the config is null
     * @since 0.24.2
     */
    static ParameterResearch.ObjectiveFunction<Indicator<Num>> eventMutualInformation(
            Function<BarSeries, Indicator<Boolean>> targetBuilder, EventMutualInformationConfig config,
            boolean useNormalized) {
        Objects.requireNonNull(targetBuilder, "targetBuilder");
        Objects.requireNonNull(config, "config");
        return (predictor, window) -> {
            try {
                Indicator<Boolean> target = targetBuilder.apply(window.series());
                EventMutualInformationResult result = new EventMutualInformationEvaluator().evaluate(predictor, target,
                        window.series().getBeginIndex(), window.series().getEndIndex(), config);
                Num score = useNormalized ? result.normalizedMutualInformation() : result.mutualInformationNats();
                if (!Num.isFinite(score)) {
                    return ParameterResearch.ObjectiveEvaluation
                            .failed("mutual information is undefined in the evaluation window");
                }
                Map<String, Num> metrics = new LinkedHashMap<>();
                metrics.put("mutualInformationNats", result.mutualInformationNats());
                metrics.put("normalizedMutualInformation", result.normalizedMutualInformation());
                metrics.put("targetEntropyNats", result.targetEntropyNats());
                metrics.put("sampleCount", window.series().numFactory().numOf(result.sampleCount()));
                metrics.put("positiveTargetRate", result.positiveTargetRate());
                return ParameterResearch.ObjectiveEvaluation.of(score, metrics);
            } catch (RuntimeException ex) {
                return ParameterResearch.ObjectiveEvaluation
                        .failed("event mutual information evaluation failed" + message(ex));
            }
        };
    }

    private static Map<String, Num> finiteMetrics(String nameA, Num valueA, String nameB, Num valueB) {
        Map<String, Num> metrics = new LinkedHashMap<>();
        if (Num.isFinite(valueA)) {
            metrics.put(nameA, valueA);
        }
        if (Num.isFinite(valueB)) {
            metrics.put(nameB, valueB);
        }
        return metrics;
    }

    private static String message(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null ? "" : ": " + message;
    }
}
