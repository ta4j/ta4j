/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.ta4j.core.num.NaN.NaN;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceModel;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceProfiles;
import org.ta4j.core.indicators.elliott.confidence.ElliottConfidenceBreakdown;
import org.ta4j.core.indicators.elliott.swing.SwingDetector;
import org.ta4j.core.indicators.elliott.swing.SwingDetectorResult;
import org.ta4j.core.indicators.elliott.swing.SwingFilter;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Orchestrates Elliott Wave analysis using pluggable detectors and profiles.
 *
 * <p>
 * Use this analyzer when you want a single, end-to-end analysis run (for
 * charting, reporting, or batch workflows) rather than per-bar indicator
 * outputs. The analyzer wires together a {@link SwingDetector}, optional
 * {@link SwingFilter}, optional {@link ElliottSwingCompressor}, and a
 * confidence model to produce an {@link ElliottAnalysisResult} containing
 * scenarios, confidence breakdowns, channels, and trend bias.
 *
 * <p>
 * For indicator-style access, use {@link ElliottWaveFacade} instead.
 *
 * @since 0.22.2
 */
public final class ElliottWaveAnalyzer {

    private static final int DEFAULT_SCENARIO_SWING_WINDOW = 5;

    private final SwingDetector swingDetector;
    private final SwingFilter swingFilter;
    private final ElliottSwingCompressor compressor;
    private final Function<NumFactory, ConfidenceModel> confidenceModelFactory;
    private final PatternSet patternSet;
    private final ElliottDegree degree;
    private final double minConfidence;
    private final int maxScenarios;
    private final int scenarioSwingWindow;

    private ElliottWaveAnalyzer(final Builder builder) {
        this.swingDetector = Objects.requireNonNull(builder.swingDetector, "swingDetector");
        this.swingFilter = builder.swingFilter;
        this.compressor = builder.compressor;
        this.confidenceModelFactory = builder.confidenceModelFactory == null ? ConfidenceProfiles::defaultModel
                : builder.confidenceModelFactory;
        this.patternSet = builder.patternSet == null ? PatternSet.all() : builder.patternSet;
        this.degree = Objects.requireNonNull(builder.degree, "degree");
        this.minConfidence = builder.minConfidence;
        this.maxScenarios = builder.maxScenarios;
        this.scenarioSwingWindow = builder.scenarioSwingWindow;
    }

    /**
     * Creates a new builder for the analyzer.
     *
     * @return builder
     * @since 0.22.2
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Runs the analysis on the supplied series.
     *
     * @param series bar series
     * @return analysis result
     * @since 0.22.2
     */
    public ElliottAnalysisResult analyze(final BarSeries series) {
        Objects.requireNonNull(series, "series");
        if (series.isEmpty()) {
            throw new IllegalArgumentException("series cannot be empty");
        }

        int endIndex = series.getEndIndex();
        SwingDetectorResult detection = Objects.requireNonNull(swingDetector.detect(series, endIndex, degree),
                "swingDetector.detect");
        List<ElliottSwing> rawSwings = detection.swings();

        List<ElliottSwing> processed = rawSwings;
        if (swingFilter != null) {
            List<ElliottSwing> filtered = swingFilter.filter(processed);
            processed = filtered == null ? List.of() : filtered;
        }
        if (compressor != null) {
            List<ElliottSwing> compressed = compressor.compress(processed);
            processed = compressed == null ? List.of() : compressed;
        }

        List<ElliottSwing> scenarioSwings = recentSwings(processed, scenarioSwingWindow);
        ElliottChannel channel = computeChannel(series.numFactory(), scenarioSwings, endIndex);

        ConfidenceModel confidenceModel = Objects.requireNonNull(confidenceModelFactory.apply(series.numFactory()),
                "confidenceModelFactory");
        ElliottScenarioGenerator generator = new ElliottScenarioGenerator(series.numFactory(), minConfidence,
                maxScenarios, confidenceModel, patternSet);
        ElliottScenarioSet scenarios = generator.generate(scenarioSwings, degree, channel, endIndex);
        ElliottTrendBias trendBias = scenarios.trendBias();

        Map<String, ElliottConfidenceBreakdown> breakdowns = new HashMap<>();
        for (ElliottScenario scenario : scenarios.all()) {
            ElliottConfidenceBreakdown breakdown = confidenceModel.score(scenario.swings(), scenario.currentPhase(),
                    channel, scenario.type());
            breakdowns.put(scenario.id(), breakdown);
        }

        return new ElliottAnalysisResult(degree, endIndex, rawSwings, scenarioSwings, scenarios, breakdowns, channel,
                trendBias);
    }

    private List<ElliottSwing> recentSwings(final List<ElliottSwing> swings, final int window) {
        if (swings == null || swings.isEmpty()) {
            return List.of();
        }
        if (window <= 0 || swings.size() <= window) {
            return List.copyOf(swings);
        }
        return List.copyOf(swings.subList(swings.size() - window, swings.size()));
    }

    private ElliottChannel computeChannel(final NumFactory numFactory, final List<ElliottSwing> swings,
            final int index) {
        if (swings == null || swings.size() < 4) {
            return new ElliottChannel(NaN, NaN, NaN);
        }

        List<ElliottSwing> rising = latestSwingsByDirection(swings, true);
        List<ElliottSwing> falling = latestSwingsByDirection(swings, false);
        if (rising.size() < 2 || falling.size() < 2) {
            return new ElliottChannel(NaN, NaN, NaN);
        }

        PivotLine upper = projectLine(numFactory, rising.get(rising.size() - 2), rising.get(rising.size() - 1), index);
        PivotLine lower = projectLine(numFactory, falling.get(falling.size() - 2), falling.get(falling.size() - 1),
                index);
        if (!upper.isValid() || !lower.isValid()) {
            return new ElliottChannel(NaN, NaN, NaN);
        }

        Num median = upper.value.plus(lower.value).dividedBy(numFactory.two());
        return new ElliottChannel(upper.value, lower.value, median);
    }

    private List<ElliottSwing> latestSwingsByDirection(final List<ElliottSwing> swings, final boolean rising) {
        List<ElliottSwing> filtered = new ArrayList<>(2);
        for (int i = swings.size() - 1; i >= 0 && filtered.size() < 2; i--) {
            ElliottSwing swing = swings.get(i);
            if (swing.isRising() == rising) {
                filtered.add(swing);
            }
        }
        Collections.reverse(filtered);
        return filtered;
    }

    private PivotLine projectLine(final NumFactory numFactory, final ElliottSwing older, final ElliottSwing newer,
            final int index) {
        if (older == null || newer == null) {
            return PivotLine.invalid();
        }
        int span = newer.toIndex() - older.toIndex();
        if (span == 0) {
            return PivotLine.invalid();
        }
        Num spanNum = numFactory.numOf(span);
        if (spanNum.isZero()) {
            return PivotLine.invalid();
        }
        Num slope = newer.toPrice().minus(older.toPrice()).dividedBy(spanNum);
        int distance = index - newer.toIndex();
        Num projected = newer.toPrice().plus(slope.multipliedBy(numFactory.numOf(distance)));
        if (Num.isNaNOrNull(projected)) {
            return PivotLine.invalid();
        }
        return new PivotLine(projected);
    }

    private record PivotLine(Num value) {

        private static PivotLine invalid() {
            return new PivotLine(NaN);
        }

        private boolean isValid() {
            return Num.isValid(value);
        }
    }

    /**
     * Builder for {@link ElliottWaveAnalyzer}.
     *
     * @since 0.22.2
     */
    public static final class Builder {

        private SwingDetector swingDetector;
        private SwingFilter swingFilter;
        private ElliottSwingCompressor compressor;
        private Function<NumFactory, ConfidenceModel> confidenceModelFactory;
        private PatternSet patternSet;
        private ElliottDegree degree;
        private double minConfidence = ElliottScenarioGenerator.DEFAULT_MIN_CONFIDENCE;
        private int maxScenarios = ElliottScenarioGenerator.DEFAULT_MAX_SCENARIOS;
        private int scenarioSwingWindow = DEFAULT_SCENARIO_SWING_WINDOW;

        private Builder() {
        }

        /**
         * @param swingDetector swing detector implementation
         * @return builder
         * @since 0.22.2
         */
        public Builder swingDetector(final SwingDetector swingDetector) {
            this.swingDetector = Objects.requireNonNull(swingDetector, "swingDetector");
            return this;
        }

        /**
         * @param swingFilter swing filter to apply after detection
         * @return builder
         * @since 0.22.2
         */
        public Builder swingFilter(final SwingFilter swingFilter) {
            this.swingFilter = swingFilter;
            return this;
        }

        /**
         * @param compressor swing compressor for noise filtering
         * @return builder
         * @since 0.22.2
         */
        public Builder compressor(final ElliottSwingCompressor compressor) {
            this.compressor = compressor;
            return this;
        }

        /**
         * @param confidenceModel confidence model to use
         * @return builder
         * @since 0.22.2
         */
        public Builder confidenceModel(final ConfidenceModel confidenceModel) {
            Objects.requireNonNull(confidenceModel, "confidenceModel");
            this.confidenceModelFactory = unused -> confidenceModel;
            return this;
        }

        /**
         * @param confidenceModelFactory factory for confidence models
         * @return builder
         * @since 0.22.2
         */
        public Builder confidenceModelFactory(final Function<NumFactory, ConfidenceModel> confidenceModelFactory) {
            this.confidenceModelFactory = Objects.requireNonNull(confidenceModelFactory, "confidenceModelFactory");
            return this;
        }

        /**
         * @param patternSet enabled pattern set
         * @return builder
         * @since 0.22.2
         */
        public Builder patternSet(final PatternSet patternSet) {
            this.patternSet = Objects.requireNonNull(patternSet, "patternSet");
            return this;
        }

        /**
         * @param degree Elliott wave degree
         * @return builder
         * @since 0.22.2
         */
        public Builder degree(final ElliottDegree degree) {
            this.degree = Objects.requireNonNull(degree, "degree");
            return this;
        }

        /**
         * @param minConfidence minimum confidence threshold
         * @return builder
         * @since 0.22.2
         */
        public Builder minConfidence(final double minConfidence) {
            if (minConfidence < 0.0 || minConfidence > 1.0) {
                throw new IllegalArgumentException("minConfidence must be in [0.0, 1.0]");
            }
            this.minConfidence = minConfidence;
            return this;
        }

        /**
         * @param maxScenarios maximum scenarios to retain
         * @return builder
         * @since 0.22.2
         */
        public Builder maxScenarios(final int maxScenarios) {
            if (maxScenarios <= 0) {
                throw new IllegalArgumentException("maxScenarios must be positive");
            }
            this.maxScenarios = maxScenarios;
            return this;
        }

        /**
         * @param scenarioSwingWindow number of swings passed to scenario generation
         * @return builder
         * @since 0.22.2
         */
        public Builder scenarioSwingWindow(final int scenarioSwingWindow) {
            if (scenarioSwingWindow < 0) {
                throw new IllegalArgumentException("scenarioSwingWindow must be >= 0");
            }
            this.scenarioSwingWindow = scenarioSwingWindow;
            return this;
        }

        /**
         * Builds the analyzer.
         *
         * @return analyzer instance
         * @since 0.22.2
         */
        public ElliottWaveAnalyzer build() {
            if (swingDetector == null) {
                throw new IllegalStateException("swingDetector must be configured");
            }
            if (degree == null) {
                throw new IllegalStateException("degree must be configured");
            }
            return new ElliottWaveAnalyzer(this);
        }
    }
}
