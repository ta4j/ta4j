/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.projection;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.ta4j.core.analysis.frequency.SampleSummary;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Immutable numeric forecast distribution captured at one decision index.
 *
 * <p>
 * Use {@link #ofSamples(int, int, List)} for empirical distributions and
 * {@link #builder(int, int, NumFactory, ForecastSupport)} for model-produced
 * summaries. All stable values belong to one {@link NumFactory}, and
 * {@link #support()} makes empirical versus analytic provenance explicit.
 *
 * @since 0.22.9
 */
public final class Forecast {

    /** Default quantiles included by sample-based factories. */
    public static final List<Double> DEFAULT_QUANTILE_PROBABILITIES = List.of(0.05, 0.25, 0.5, 0.75, 0.95);

    private final int decisionIndex;
    private final int horizon;
    private final ForecastSupport support;
    private final Num mean;
    private final Num median;
    private final Num standardDeviation;
    private final Map<Double, Num> quantiles;

    private Forecast(int decisionIndex, int horizon, ForecastSupport support, Num mean, Num median,
            Num standardDeviation, Map<Double, Num> quantiles) {
        validateMetadata(decisionIndex, horizon);
        this.decisionIndex = decisionIndex;
        this.horizon = horizon;
        this.support = Objects.requireNonNull(support, "support must not be null");
        this.mean = Objects.requireNonNull(mean, "mean must not be null");
        this.median = Objects.requireNonNull(median, "median must not be null");
        this.standardDeviation = Objects.requireNonNull(standardDeviation, "standardDeviation must not be null");
        this.quantiles = immutableQuantiles(quantiles);
    }

    /** @return decision index where the forecast was made */
    public int decisionIndex() {
        return decisionIndex;
    }

    /** @return forecast horizon in bars */
    public int horizon() {
        return horizon;
    }

    /**
     * Returns distribution provenance.
     *
     * @return unavailable, empirical, or analytic support
     * @since 0.23.1
     */
    public ForecastSupport support() {
        return support;
    }

    /**
     * Compatibility accessor for empirical support size.
     *
     * @return empirical represented value count, or zero for analytic and
     *         unavailable forecasts
     */
    public int sampleCount() {
        return support instanceof ForecastSupport.Empirical empirical ? empirical.count() : 0;
    }

    /** @return whether the forecast is stable and usable */
    public boolean isStable() {
        return !(support instanceof ForecastSupport.Unavailable);
    }

    /** @return forecast mean */
    public Num mean() {
        return mean;
    }

    /** @return forecast median */
    public Num median() {
        return median;
    }

    /** @return population standard deviation */
    public Num standardDeviation() {
        return standardDeviation;
    }

    /** @return immutable probability-to-quantile map */
    public Map<Double, Num> quantiles() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(quantiles));
    }

    /** @return alias for {@link #decisionIndex()} */
    public int index() {
        return decisionIndex;
    }

    /**
     * Creates an unstable forecast.
     *
     * @param decisionIndex decision index
     * @param horizon       horizon in bars
     * @return unstable forecast
     */
    public static Forecast unstable(int decisionIndex, int horizon) {
        return new Forecast(decisionIndex, horizon, ForecastSupport.unavailable(), NaN.NaN, NaN.NaN, NaN.NaN, Map.of());
    }

    /**
     * Starts a validated stable-summary builder.
     *
     * @param decisionIndex decision index
     * @param horizon       horizon in bars
     * @param numFactory    owning numeric factory
     * @param support       empirical or analytic support
     * @return summary builder
     * @since 0.23.1
     */
    public static Builder builder(int decisionIndex, int horizon, NumFactory numFactory, ForecastSupport support) {
        return new Builder(decisionIndex, horizon, numFactory, support);
    }

    /** Summarizes samples using {@link #DEFAULT_QUANTILE_PROBABILITIES}. */
    public static Forecast ofSamples(int decisionIndex, int horizon, List<Num> samples) {
        return ofSamples(decisionIndex, horizon, samples, DEFAULT_QUANTILE_PROBABILITIES);
    }

    /**
     * Summarizes finite samples using the requested quantiles.
     *
     * <p>
     * Every retained sample is coerced through the first finite sample's factory.
     * Returns an unstable forecast when no finite samples remain.
     */
    public static Forecast ofSamples(int decisionIndex, int horizon, List<Num> samples,
            List<Double> quantileProbabilities) {
        List<Num> input = Objects.requireNonNull(samples, "samples must not be null");
        NumFactory numFactory = null;
        List<Num> normalized = new ArrayList<>(input.size());
        for (Num sample : input) {
            if (!Num.isFinite(sample)) {
                continue;
            }
            if (numFactory == null) {
                numFactory = sample.getNumFactory();
            }
            Num value = coerce(sample, numFactory, "sample");
            normalized.add(value);
        }
        if (normalized.isEmpty()) {
            return unstable(decisionIndex, horizon);
        }

        List<Num> sortedSamples = new ArrayList<>(normalized);
        sortedSamples.sort(Num::compareTo);
        SampleSummary summary = SampleSummary.fromValues(normalized.stream(), numFactory);
        Num variance = summary.m2().dividedBy(numFactory.numOf(normalized.size()));
        Num standardDeviation = variance.isZero() ? numFactory.zero() : variance.sqrt();
        Map<Double, Num> quantiles = new LinkedHashMap<>();
        for (Double probability : validateProbabilities(quantileProbabilities)) {
            quantiles.put(probability, percentile(numFactory, sortedSamples, probability));
        }
        return builder(decisionIndex, horizon, numFactory, ForecastSupport.empirical(normalized.size()))
                .mean(summary.mean())
                .median(percentile(numFactory, sortedSamples, 0.5))
                .standardDeviation(standardDeviation)
                .quantiles(quantiles)
                .build();
    }

    /** @return whether the requested valid quantile is available */
    public boolean hasQuantile(double probability) {
        validateProbability(probability);
        return quantiles.containsKey(probability);
    }

    /**
     * Returns an available quantile or {@link NaN#NaN}.
     *
     * @param probability probability in {@code [0, 1]}
     * @return quantile value, or {@code NaN.NaN} when absent
     */
    public Num quantile(double probability) {
        validateProbability(probability);
        return quantiles.getOrDefault(probability, NaN.NaN);
    }

    /**
     * Scales the distribution through the origin.
     *
     * @param factor scale factor
     * @return scaled distribution
     * @since 0.23.1
     */
    public Forecast scale(Num factor) {
        return affine(factor, mean.getNumFactory().zero());
    }

    /**
     * Applies {@code scale * value + offset} with correct dispersion and quantile
     * semantics.
     *
     * @param scale  finite scale
     * @param offset finite offset
     * @return transformed distribution
     * @since 0.23.1
     */
    public Forecast affine(Num scale, Num offset) {
        if (!isStable()) {
            return unstable(decisionIndex, horizon);
        }
        NumFactory numFactory = mean.getNumFactory();
        Num normalizedScale = coerce(scale, numFactory, "scale");
        Num normalizedOffset = coerce(offset, numFactory, "offset");
        Map<Double, Num> transformedQuantiles = new LinkedHashMap<>();
        for (Map.Entry<Double, Num> entry : quantiles.entrySet()) {
            double probability = normalizedScale.isNegative() ? complement(entry.getKey()) : entry.getKey();
            transformedQuantiles.put(probability,
                    entry.getValue().multipliedBy(normalizedScale).plus(normalizedOffset));
        }
        return builder(decisionIndex, horizon, numFactory, support)
                .mean(mean.multipliedBy(normalizedScale).plus(normalizedOffset))
                .median(median.multipliedBy(normalizedScale).plus(normalizedOffset))
                .standardDeviation(standardDeviation.multipliedBy(normalizedScale.abs()))
                .quantiles(transformedQuantiles)
                .build();
    }

    private static void validateMetadata(int decisionIndex, int horizon) {
        if (decisionIndex < 0) {
            throw new IllegalArgumentException("decisionIndex must be >= 0");
        }
        if (horizon <= 0) {
            throw new IllegalArgumentException("horizon must be > 0");
        }
    }

    private static Num coerce(Num value, NumFactory numFactory, String fieldName) {
        Num input = Objects.requireNonNull(value, fieldName + " must not be null");
        if (!Num.isFinite(input)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        Num normalized = numFactory.numOf(input.bigDecimalValue());
        if (!Num.isFinite(normalized)) {
            throw new IllegalArgumentException(fieldName + " cannot be represented by the target NumFactory");
        }
        if (normalized.isZero() && !input.isZero()) {
            throw new IllegalArgumentException(fieldName + " underflows the target NumFactory");
        }
        return normalized;
    }

    private static Map<Double, Num> immutableQuantiles(Map<Double, Num> quantiles) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(quantiles));
    }

    private static List<Double> validateProbabilities(List<Double> probabilities) {
        List<Double> input = Objects.requireNonNull(probabilities, "quantileProbabilities must not be null");
        if (input.isEmpty()) {
            throw new IllegalArgumentException("quantileProbabilities must not be empty");
        }
        TreeMap<Double, Double> sorted = new TreeMap<>();
        for (Double probability : input) {
            Double value = Objects.requireNonNull(probability, "quantile probability must not be null");
            validateProbability(value);
            sorted.put(value, value);
        }
        return List.copyOf(sorted.values());
    }

    private static void validateProbability(double probability) {
        if (Double.isNaN(probability) || probability < 0d || probability > 1d) {
            throw new IllegalArgumentException("quantile probability must be in [0, 1]");
        }
    }

    private static double complement(double probability) {
        return BigDecimal.ONE.subtract(BigDecimal.valueOf(probability)).doubleValue();
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
        return lower.plus(upper.minus(lower).multipliedBy(numFactory.numOf(position - lowerIndex)));
    }

    /**
     * Builder for validated stable summaries.
     *
     * @since 0.23.1
     */
    public static final class Builder {

        private final int decisionIndex;
        private final int horizon;
        private final NumFactory numFactory;
        private final ForecastSupport support;
        private Num mean;
        private Num median;
        private Num standardDeviation;
        private Map<Double, Num> quantiles = Map.of();

        private Builder(int decisionIndex, int horizon, NumFactory numFactory, ForecastSupport support) {
            validateMetadata(decisionIndex, horizon);
            this.decisionIndex = decisionIndex;
            this.horizon = horizon;
            this.numFactory = Objects.requireNonNull(numFactory, "numFactory must not be null");
            this.support = Objects.requireNonNull(support, "support must not be null");
            if (support instanceof ForecastSupport.Unavailable) {
                throw new IllegalArgumentException("stable summaries cannot use unavailable support");
            }
        }

        /** Sets the required distribution mean. */
        public Builder mean(Num mean) {
            this.mean = mean;
            return this;
        }

        /** Sets the required distribution median. */
        public Builder median(Num median) {
            this.median = median;
            return this;
        }

        /** Sets the required non-negative population standard deviation. */
        public Builder standardDeviation(Num standardDeviation) {
            this.standardDeviation = standardDeviation;
            return this;
        }

        /**
         * Sets optional probability-to-value quantiles; the map is copied at build
         * time.
         */
        public Builder quantiles(Map<Double, Num> quantiles) {
            this.quantiles = Objects.requireNonNull(quantiles, "quantiles must not be null");
            return this;
        }

        /** Builds the normalized, validated forecast. */
        public Forecast build() {
            Num normalizedMean = coerce(mean, numFactory, "mean");
            Num normalizedMedian = coerce(median, numFactory, "median");
            Num normalizedStandardDeviation = coerce(standardDeviation, numFactory, "standardDeviation");
            if (normalizedStandardDeviation.isNegative()) {
                throw new IllegalArgumentException("standardDeviation must be >= 0");
            }
            TreeMap<Double, Num> sorted = new TreeMap<>();
            for (Map.Entry<Double, Num> entry : quantiles.entrySet()) {
                Double probability = Objects.requireNonNull(entry.getKey(), "quantile probability must not be null");
                validateProbability(probability);
                sorted.put(probability, coerce(entry.getValue(), numFactory, "quantile value"));
            }
            validateCoherence(normalizedMean, normalizedMedian, normalizedStandardDeviation, sorted);
            return new Forecast(decisionIndex, horizon, support, normalizedMean, normalizedMedian,
                    normalizedStandardDeviation, sorted);
        }

        private void validateCoherence(Num normalizedMean, Num normalizedMedian, Num normalizedStandardDeviation,
                TreeMap<Double, Num> sortedQuantiles) {
            Num previous = null;
            for (Num quantile : sortedQuantiles.values()) {
                if (previous != null && quantile.isLessThan(previous)) {
                    throw new IllegalArgumentException("quantile values must be nondecreasing");
                }
                previous = quantile;
            }
            Num p50 = sortedQuantiles.get(0.5d);
            if (p50 != null && !p50.isEqual(normalizedMedian)) {
                throw new IllegalArgumentException("median must equal the 0.5 quantile");
            }
            for (Map.Entry<Double, Num> entry : sortedQuantiles.entrySet()) {
                if (entry.getKey() < 0.5d && entry.getValue().isGreaterThan(normalizedMedian)) {
                    throw new IllegalArgumentException("lower quantiles must not exceed the median");
                }
                if (entry.getKey() > 0.5d && entry.getValue().isLessThan(normalizedMedian)) {
                    throw new IllegalArgumentException("upper quantiles must not be below the median");
                }
            }
            boolean singleValueSupport = support instanceof ForecastSupport.Empirical empirical
                    && empirical.count() == 1;
            if (normalizedStandardDeviation.isZero() || singleValueSupport) {
                if (!normalizedMean.isEqual(normalizedMedian)
                        || sortedQuantiles.values().stream().anyMatch(value -> !value.isEqual(normalizedMean))) {
                    throw new IllegalArgumentException("zero-dispersion summaries must contain one location value");
                }
                if (singleValueSupport && !normalizedStandardDeviation.isZero()) {
                    throw new IllegalArgumentException("one-value empirical support must have zero dispersion");
                }
            }
        }
    }
}
