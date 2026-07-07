/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.projection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

import org.ta4j.core.analysis.frequency.SampleSummary;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Forecast summary captured at one decision index.
 *
 * <p>
 * Forecast projection indicators return this value model so simulated,
 * regression, or other projection techniques can expose the same mean, median,
 * standard deviation, quantile, horizon, sample count, and stable-state
 * contract.
 *
 * @param <T> forecast value type
 * @since 0.22.9
 */
public final class Forecast<T> {

    /**
     * Default quantiles included by sample-based forecast factories.
     *
     * @since 0.22.9
     */
    public static final List<Double> DEFAULT_QUANTILE_PROBABILITIES = List.of(0.05, 0.25, 0.5, 0.75, 0.95);

    private final int decisionIndex;
    private final int horizon;
    private final int sampleCount;
    private final boolean isStable;
    private final T mean;
    private final T median;
    private final T standardDeviation;
    private final Map<Double, T> quantiles;

    private Forecast(int decisionIndex, int horizon, int sampleCount, boolean isStable, T mean, T median,
            T standardDeviation, Map<Double, T> quantiles) {
        if (decisionIndex < 0) {
            throw new IllegalArgumentException("decisionIndex must be >= 0");
        }
        if (horizon <= 0) {
            throw new IllegalArgumentException("horizon must be > 0");
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be >= 0");
        }
        if (isStable && sampleCount == 0) {
            throw new IllegalArgumentException("stable forecasts must summarize at least one sample");
        }
        if (!isStable && sampleCount != 0) {
            throw new IllegalArgumentException("unstable forecasts must have zero samples");
        }
        this.decisionIndex = decisionIndex;
        this.horizon = horizon;
        this.sampleCount = sampleCount;
        this.isStable = isStable;
        this.mean = Objects.requireNonNull(mean, "mean must not be null");
        this.median = Objects.requireNonNull(median, "median must not be null");
        this.standardDeviation = Objects.requireNonNull(standardDeviation, "standardDeviation must not be null");
        this.quantiles = validateQuantiles(quantiles);
    }

    /**
     * Returns the decision index where the forecast was made.
     *
     * @return decision index
     * @since 0.22.9
     */
    public int decisionIndex() {
        return decisionIndex;
    }

    /**
     * Returns the forecast horizon in bars.
     *
     * @return horizon
     * @since 0.22.9
     */
    public int horizon() {
        return horizon;
    }

    /**
     * Returns the number of samples summarized by this forecast.
     *
     * @return sample count
     * @since 0.22.9
     */
    public int sampleCount() {
        return sampleCount;
    }

    /**
     * Returns whether the forecast is stable and usable.
     *
     * @return stable state
     * @since 0.22.9
     */
    public boolean isStable() {
        return isStable;
    }

    /**
     * Returns the forecast mean.
     *
     * @return mean value
     * @since 0.22.9
     */
    public T mean() {
        return mean;
    }

    /**
     * Returns the forecast median.
     *
     * @return median value
     * @since 0.22.9
     */
    public T median() {
        return median;
    }

    /**
     * Returns the population standard deviation of summarized samples.
     *
     * @return standard deviation
     * @since 0.22.9
     */
    public T standardDeviation() {
        return standardDeviation;
    }

    /**
     * Returns a defensive quantile map copy.
     *
     * @return quantile probability to forecast value
     * @since 0.22.9
     */
    public Map<Double, T> quantiles() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(quantiles));
    }

    /**
     * Alias for {@link #decisionIndex()} used by indicator-style code.
     *
     * @return decision index
     * @since 0.22.9
     */
    public int index() {
        return decisionIndex;
    }

    /**
     * Creates an unstable {@link Num}-based forecast.
     *
     * @param decisionIndex decision index
     * @param horizon       forecast horizon in bars
     * @return unstable forecast summary
     * @since 0.22.9
     */
    public static Forecast<Num> unstable(int decisionIndex, int horizon) {
        return unstable(decisionIndex, horizon, NaN.NaN);
    }

    /**
     * Creates an unstable forecast using the provided unstable value.
     *
     * @param decisionIndex decision index
     * @param horizon       forecast horizon in bars
     * @param unstableValue value used for summary fields
     * @param <T>           forecast value type
     * @return unstable forecast summary
     * @since 0.22.9
     */
    public static <T> Forecast<T> unstable(int decisionIndex, int horizon, T unstableValue) {
        T value = Objects.requireNonNull(unstableValue, "unstableValue must not be null");
        return new Forecast<>(decisionIndex, horizon, 0, false, value, value, value, Map.of());
    }

    /**
     * Summarizes samples using the default forecast quantiles.
     *
     * @param decisionIndex decision index
     * @param horizon       forecast horizon in bars
     * @param samples       sample values to summarize
     * @return stable forecast, or unstable when no valid samples are present
     * @since 0.22.9
     */
    public static Forecast<Num> ofSamples(int decisionIndex, int horizon, List<Num> samples) {
        return ofSamples(decisionIndex, horizon, samples, DEFAULT_QUANTILE_PROBABILITIES);
    }

    /**
     * Summarizes samples using the requested quantiles.
     *
     * @param decisionIndex         decision index
     * @param horizon               forecast horizon in bars
     * @param samples               sample values to summarize
     * @param quantileProbabilities quantile probabilities in {@code [0, 1]}
     * @return stable forecast, or unstable when no valid samples are present
     * @since 0.22.9
     */
    public static Forecast<Num> ofSamples(int decisionIndex, int horizon, List<Num> samples,
            List<Double> quantileProbabilities) {
        List<Num> validSamples = validSamples(samples);
        if (validSamples.isEmpty()) {
            return unstable(decisionIndex, horizon);
        }

        NumFactory numFactory = validSamples.get(0).getNumFactory();
        List<Num> sortedSamples = new ArrayList<>(validSamples);
        sortedSamples.sort(Num::compareTo);
        SampleSummary summary = SampleSummary.fromValues(validSamples.stream(), numFactory);
        Num mean = summary.mean();
        Num standardDeviation = summary.m2().dividedBy(numFactory.numOf(validSamples.size())).sqrt();
        Num median = percentile(numFactory, sortedSamples, 0.5);
        Map<Double, Num> quantiles = new LinkedHashMap<>();
        for (Double probability : validateProbabilities(quantileProbabilities)) {
            quantiles.put(probability, percentile(numFactory, sortedSamples, probability));
        }

        return new Forecast<>(decisionIndex, horizon, validSamples.size(), true, mean, median, standardDeviation,
                quantiles);
    }

    /**
     * Returns whether this forecast includes the requested quantile probability.
     *
     * @param probability quantile probability in {@code [0, 1]}
     * @return true if the quantile is available, false otherwise
     * @since 0.22.9
     */
    public boolean hasQuantile(double probability) {
        validateProbability(probability);
        return quantiles.containsKey(probability);
    }

    /**
     * Returns the value for an included quantile probability.
     *
     * @param probability quantile probability in {@code [0, 1]}
     * @return quantile value, or {@code null} when the valid probability was not
     *         configured for this forecast
     * @since 0.22.9
     */
    public T quantile(double probability) {
        validateProbability(probability);
        return quantiles.get(probability);
    }

    /**
     * Maps all summary values while preserving decision index, horizon, sample
     * count, and stable state.
     *
     * @param mapper value mapper
     * @param <R>    mapped forecast value type
     * @return mapped forecast summary
     * @since 0.22.9
     */
    public <R> Forecast<R> map(Function<? super T, ? extends R> mapper) {
        Function<? super T, ? extends R> valueMapper = Objects.requireNonNull(mapper, "mapper must not be null");
        Map<Double, R> mappedQuantiles = new LinkedHashMap<>();
        for (Map.Entry<Double, T> entry : quantiles.entrySet()) {
            mappedQuantiles.put(entry.getKey(),
                    Objects.requireNonNull(valueMapper.apply(entry.getValue()), "mapped quantile must not be null"));
        }
        return new Forecast<>(decisionIndex, horizon, sampleCount, isStable,
                Objects.requireNonNull(valueMapper.apply(mean), "mapped mean must not be null"),
                Objects.requireNonNull(valueMapper.apply(median), "mapped median must not be null"),
                Objects.requireNonNull(valueMapper.apply(standardDeviation),
                        "mapped standardDeviation must not be null"),
                mappedQuantiles);
    }

    private static <T> Map<Double, T> validateQuantiles(Map<Double, T> quantiles) {
        Map<Double, T> input = Objects.requireNonNull(quantiles, "quantiles must not be null");
        TreeMap<Double, T> sorted = new TreeMap<>();
        for (Map.Entry<Double, T> entry : input.entrySet()) {
            Double probability = Objects.requireNonNull(entry.getKey(), "quantile probability must not be null");
            validateProbability(probability);
            sorted.put(probability, Objects.requireNonNull(entry.getValue(), "quantile value must not be null"));
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    private static List<Num> validSamples(List<Num> samples) {
        List<Num> input = Objects.requireNonNull(samples, "samples must not be null");
        List<Num> validSamples = new ArrayList<>(input.size());
        for (Num sample : input) {
            if (Num.isFinite(sample)) {
                validSamples.add(sample);
            }
        }
        return validSamples;
    }

    private static List<Double> validateProbabilities(List<Double> probabilities) {
        List<Double> input = Objects.requireNonNull(probabilities, "quantileProbabilities must not be null");
        if (input.isEmpty()) {
            throw new IllegalArgumentException("quantileProbabilities must not be empty");
        }
        TreeMap<Double, Double> sorted = new TreeMap<>();
        for (Double probability : input) {
            Double validated = Objects.requireNonNull(probability, "quantile probability must not be null");
            validateProbability(validated);
            sorted.put(validated, validated);
        }
        return List.copyOf(sorted.values());
    }

    private static void validateProbability(double probability) {
        if (Double.isNaN(probability) || probability < 0d || probability > 1d) {
            throw new IllegalArgumentException("quantile probability must be in [0, 1]");
        }
    }

    private static Num percentile(NumFactory numFactory, List<Num> sortedSamples, double probability) {
        if (sortedSamples.size() == 1) {
            return sortedSamples.get(0);
        }
        double position = probability * (sortedSamples.size() - 1);
        int lowerIndex = (int) Math.floor(position);
        int upperIndex = (int) Math.ceil(position);
        Num lower = sortedSamples.get(lowerIndex);
        Num upper = sortedSamples.get(upperIndex);
        if (lowerIndex == upperIndex) {
            return lower;
        }
        Num fraction = numFactory.numOf(position - lowerIndex);
        return lower.plus(upper.minus(lower).multipliedBy(fraction));
    }
}
