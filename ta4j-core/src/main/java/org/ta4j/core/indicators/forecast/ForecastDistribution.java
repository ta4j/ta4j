/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Immutable summary of a forecast distribution produced at one decision index.
 *
 * @param index             decision index where the forecast was made
 * @param horizon           forecast horizon in bars
 * @param sampleCount       number of samples summarized by this distribution
 * @param isStable          whether the forecast is stable and usable
 * @param mean              distribution mean, or an unstable value when not
 *                          stable
 * @param median            distribution median, or an unstable value when not
 *                          stable
 * @param standardDeviation population standard deviation of the summarized
 *                          samples
 * @param quantiles         quantile probability to forecast value
 * @param <T>               forecast value type
 * @since 0.22.9
 */
public record ForecastDistribution<T>(int index, int horizon, int sampleCount, boolean isStable, T mean, T median,
        T standardDeviation, Map<Double, T> quantiles) {

    /**
     * Default quantiles included by sample-based factories.
     *
     * @since 0.22.9
     */
    public static final List<Double> DEFAULT_QUANTILE_PROBABILITIES = List.of(0.05, 0.25, 0.5, 0.75, 0.95);

    /**
     * Creates a forecast distribution.
     *
     * @since 0.22.9
     */
    public ForecastDistribution {
        if (index < 0) {
            throw new IllegalArgumentException("index must be >= 0");
        }
        if (horizon <= 0) {
            throw new IllegalArgumentException("horizon must be > 0");
        }
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be >= 0");
        }
        if (isStable && sampleCount == 0) {
            throw new IllegalArgumentException("stable distributions must summarize at least one sample");
        }
        if (!isStable && sampleCount != 0) {
            throw new IllegalArgumentException("unstable distributions must have zero samples");
        }
        mean = Objects.requireNonNull(mean, "mean must not be null");
        median = Objects.requireNonNull(median, "median must not be null");
        standardDeviation = Objects.requireNonNull(standardDeviation, "standardDeviation must not be null");
        quantiles = validateQuantiles(quantiles);
    }

    /**
     * Returns a defensive quantile map copy.
     *
     * @return quantile probability to forecast value
     * @since 0.22.9
     */
    @Override
    public Map<Double, T> quantiles() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(quantiles));
    }

    /**
     * Creates an unstable {@link Num}-based distribution.
     *
     * @param index   decision index
     * @param horizon forecast horizon in bars
     * @return unstable forecast distribution
     * @since 0.22.9
     */
    public static ForecastDistribution<Num> unstable(int index, int horizon) {
        return unstable(index, horizon, NaN.NaN);
    }

    /**
     * Creates an unstable distribution using the provided unstable value.
     *
     * @param index         decision index
     * @param horizon       forecast horizon in bars
     * @param unstableValue value used for summary fields
     * @param <T>           forecast value type
     * @return unstable forecast distribution
     * @since 0.22.9
     */
    public static <T> ForecastDistribution<T> unstable(int index, int horizon, T unstableValue) {
        T value = Objects.requireNonNull(unstableValue, "unstableValue must not be null");
        return new ForecastDistribution<>(index, horizon, 0, false, value, value, value, Map.of());
    }

    /**
     * Summarizes samples using the default forecast quantiles.
     *
     * @param index   decision index
     * @param horizon forecast horizon in bars
     * @param samples sample values to summarize
     * @return stable distribution, or unstable when no valid samples are present
     * @since 0.22.9
     */
    public static ForecastDistribution<Num> ofSamples(int index, int horizon, List<Num> samples) {
        return ofSamples(index, horizon, samples, DEFAULT_QUANTILE_PROBABILITIES);
    }

    /**
     * Summarizes samples using the requested quantiles.
     *
     * @param index                 decision index
     * @param horizon               forecast horizon in bars
     * @param samples               sample values to summarize
     * @param quantileProbabilities quantile probabilities in {@code [0, 1]}
     * @return stable distribution, or unstable when no valid samples are present
     * @since 0.22.9
     */
    public static ForecastDistribution<Num> ofSamples(int index, int horizon, List<Num> samples,
            List<Double> quantileProbabilities) {
        List<Num> validSamples = validSamples(samples);
        if (validSamples.isEmpty()) {
            return unstable(index, horizon);
        }

        NumFactory numFactory = validSamples.get(0).getNumFactory();
        List<Num> sortedSamples = new ArrayList<>(validSamples);
        sortedSamples.sort(Num::compareTo);
        Num mean = mean(numFactory, validSamples);
        Num median = percentile(numFactory, sortedSamples, 0.5);
        Num standardDeviation = standardDeviation(numFactory, validSamples, mean);
        Map<Double, Num> quantiles = new LinkedHashMap<>();
        for (Double probability : validateProbabilities(quantileProbabilities)) {
            quantiles.put(probability, percentile(numFactory, sortedSamples, probability));
        }

        return new ForecastDistribution<>(index, horizon, validSamples.size(), true, mean, median, standardDeviation,
                quantiles);
    }

    /**
     * Returns the value for an included quantile probability.
     *
     * @param probability quantile probability in {@code [0, 1]}
     * @return quantile value
     * @throws IllegalArgumentException if this distribution does not include the
     *                                  requested probability
     * @since 0.22.9
     */
    public T quantile(double probability) {
        validateProbability(probability);
        T value = quantiles.get(probability);
        if (value == null) {
            throw new IllegalArgumentException("Quantile " + probability + " is not available");
        }
        return value;
    }

    /**
     * Maps all summary values while preserving index, horizon, sample count, and
     * stable state.
     *
     * @param mapper value mapper
     * @param <R>    mapped forecast value type
     * @return mapped forecast distribution
     * @since 0.22.9
     */
    public <R> ForecastDistribution<R> map(Function<? super T, ? extends R> mapper) {
        Function<? super T, ? extends R> valueMapper = Objects.requireNonNull(mapper, "mapper must not be null");
        Map<Double, R> mappedQuantiles = new LinkedHashMap<>();
        for (Map.Entry<Double, T> entry : quantiles.entrySet()) {
            mappedQuantiles.put(entry.getKey(),
                    Objects.requireNonNull(valueMapper.apply(entry.getValue()), "mapped quantile must not be null"));
        }
        return new ForecastDistribution<>(index, horizon, sampleCount, isStable,
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
            if (!ForecastNumerics.isInvalid(sample)) {
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

    private static Num mean(NumFactory numFactory, List<Num> samples) {
        Num sum = numFactory.zero();
        for (Num sample : samples) {
            sum = sum.plus(sample);
        }
        return sum.dividedBy(numFactory.numOf(samples.size()));
    }

    private static Num standardDeviation(NumFactory numFactory, List<Num> samples, Num mean) {
        Num variance = numFactory.zero();
        for (Num sample : samples) {
            Num deviation = sample.minus(mean);
            variance = variance.plus(deviation.multipliedBy(deviation));
        }
        return variance.dividedBy(numFactory.numOf(samples.size())).sqrt();
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
