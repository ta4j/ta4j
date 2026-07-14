/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import org.apache.commons.math3.ml.distance.EuclideanDistance;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.forecast.projection.ForecastSupport;
import org.ta4j.core.indicators.forecast.projection.ReturnForecastProjectionIndicator;
import org.ta4j.core.indicators.forecast.state.ForecastFeatureExtractor;
import org.ta4j.core.indicators.forecast.state.ForecastFeatureExtractors;
import org.ta4j.core.indicators.forecast.state.ForecastFeatureSchema;
import org.ta4j.core.indicators.forecast.state.ForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMomentState;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Forecasts cumulative log returns from historically similar return states.
 *
 * <p>
 * Each decision compares the current feature vector with eligible historical
 * states, where a candidate is eligible only after its complete forward return
 * has matured. Candidate features are standardized using historical candidates
 * only, then ranked by Euclidean distance and source index. Selected neighbors
 * receive normalized exponential distance weights and produce an empirical
 * {@link Forecast} whose support count is the selected-neighbor count.
 *
 * <p>
 * The shortest path uses a return-derived state source and the
 * {@code [mean, volatility]} log-return schema:
 *
 * <pre>{@code
 * var states = new EwmaReturnForecastStateIndicator(logReturns);
 * var projection = new AnalogReturnProjectionIndicator<>(states);
 * }</pre>
 *
 * @param <S> return-moment state type
 * @since 0.23.1
 */
public final class AnalogReturnProjectionIndicator<S extends ReturnMomentState> extends CachedIndicator<Forecast>
        implements ReturnForecastProjectionIndicator {

    private static final EuclideanDistance DISTANCE = new EuclideanDistance();

    private final ForecastStateIndicator<S> stateIndicator;
    private final ReturnIndicator returnIndicator;
    private final ForecastFeatureExtractor<? super S> featureExtractor;
    private final ForecastFeatureSchema featureSchema;
    private final int horizon;
    private final int lookbackBarCount;
    private final int neighborCount;
    private final int minimumNeighborCount;
    private final boolean standardizeFeatures;
    private final List<Double> quantileProbabilities;

    /**
     * Creates a one-bar analog projection with operator defaults.
     *
     * @param stateIndicator log-return state source
     * @since 0.23.1
     */
    public AnalogReturnProjectionIndicator(ReturnForecastStateIndicator<S> stateIndicator) {
        this(builder(stateIndicator));
    }

    /**
     * Creates an analog projection with operator defaults for the requested
     * horizon.
     *
     * @param stateIndicator log-return state source
     * @param horizon        positive forecast horizon in bars
     * @since 0.23.1
     */
    public AnalogReturnProjectionIndicator(ReturnForecastStateIndicator<S> stateIndicator, int horizon) {
        this(builder(stateIndicator).horizon(horizon));
    }

    private AnalogReturnProjectionIndicator(Builder<S> builder) {
        super(validatedSeries(builder));
        this.stateIndicator = builder.stateIndicator;
        this.returnIndicator = builder.returnIndicator;
        this.featureExtractor = builder.featureExtractor;
        this.featureSchema = builder.featureExtractor.schema();
        this.horizon = builder.horizon;
        this.lookbackBarCount = builder.lookbackBarCount;
        this.neighborCount = builder.neighborCount;
        this.minimumNeighborCount = builder.minimumNeighborCount;
        this.standardizeFeatures = builder.standardizeFeatures;
        this.quantileProbabilities = builder.quantileProbabilities;
    }

    /**
     * Starts an advanced builder and infers the source return stream.
     *
     * @param stateIndicator return-derived state source
     * @param <S>            state type
     * @return analog projection builder
     * @since 0.23.1
     */
    public static <S extends ReturnMomentState> Builder<S> builder(ReturnForecastStateIndicator<S> stateIndicator) {
        ReturnForecastStateIndicator<S> validated = Objects.requireNonNull(stateIndicator,
                "stateIndicator must not be null");
        ReturnIndicator returns = Objects.requireNonNull(validated.getReturnIndicator(),
                "stateIndicator returnIndicator must not be null");
        if (validated.getReturnRepresentation() != returns.getReturnRepresentation()) {
            throw new IllegalArgumentException("stateIndicator representation must match its return indicator");
        }
        return new Builder<>(validated, returns);
    }

    /**
     * Starts an advanced builder for a state source that does not expose its return
     * stream.
     *
     * @param stateIndicator  state source
     * @param returnIndicator forward-return source from the same series
     * @param <S>             state type
     * @return analog projection builder
     * @since 0.23.1
     */
    public static <S extends ReturnMomentState> Builder<S> builder(ForecastStateIndicator<S> stateIndicator,
            ReturnIndicator returnIndicator) {
        return new Builder<>(stateIndicator, returnIndicator);
    }

    @Override
    protected Forecast calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return Forecast.unstable(index, horizon);
        }
        S currentState = stateIndicator.getValue(index);
        double[] currentFeatures = usableFeatures(currentState, index);
        if (currentFeatures == null) {
            return Forecast.unstable(index, horizon);
        }

        int lastCandidate = index - horizon;
        int firstCandidate = Math.max(getBarSeries().getBeginIndex(), lastCandidate - lookbackBarCount + 1);
        List<Candidate> candidates = new ArrayList<>(lookbackBarCount);
        for (int candidateIndex = firstCandidate; candidateIndex <= lastCandidate; candidateIndex++) {
            S candidateState = stateIndicator.getValue(candidateIndex);
            double[] candidateFeatures = usableFeatures(candidateState, candidateIndex);
            Num realizedReturn = candidateFeatures == null ? null : cumulativeReturn(candidateIndex);
            if (candidateFeatures != null && realizedReturn != null) {
                candidates.add(new Candidate(candidateIndex, candidateFeatures, realizedReturn));
            }
        }
        if (candidates.size() < minimumNeighborCount) {
            return Forecast.unstable(index, horizon);
        }

        if (!assignDistances(candidates, currentFeatures)) {
            return Forecast.unstable(index, horizon);
        }
        candidates.sort(Comparator.comparingDouble((Candidate candidate) -> candidate.distance())
                .thenComparingInt(Candidate::index));
        int selectedCount = Math.min(neighborCount, candidates.size());
        if (selectedCount < minimumNeighborCount) {
            return Forecast.unstable(index, horizon);
        }
        return summarize(index, candidates.subList(0, selectedCount));
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public Forecast getValue(int index) {
        if (index >= 0 && index < getBarSeries().getRemovedBarsCount()) {
            return Forecast.unstable(index, horizon);
        }
        return super.getValue(index);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public int getCountOfUnstableBars() {
        int firstStateCandidate = stateIndicator.getCountOfUnstableBars();
        int firstReturnCandidate = Math.max(0, returnIndicator.getCountOfUnstableBars() - 1);
        return Math.max(firstStateCandidate, firstReturnCandidate) + minimumNeighborCount - 1 + horizon;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public int getHorizon() {
        return horizon;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public ReturnRepresentation getReturnRepresentation() {
        return ReturnRepresentation.LOG;
    }

    private double[] usableFeatures(S state, int expectedIndex) {
        if (state == null) {
            return null;
        }
        ReturnMoments moments = state.moments();
        if (moments == null || !moments.isStable() || moments.index() != expectedIndex
                || moments.representation() != ReturnRepresentation.LOG
                || featureSchema.representation() != moments.representation()) {
            return null;
        }
        try {
            double[] features = featureExtractor.features(state);
            if (features.length != featureSchema.dimension()) {
                return null;
            }
            for (double feature : features) {
                if (!Double.isFinite(feature)) {
                    return null;
                }
            }
            return features;
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return null;
        }
    }

    private Num cumulativeReturn(int candidateIndex) {
        if (candidateIndex + 1 < returnIndicator.getCountOfUnstableBars()) {
            return null;
        }
        NumFactory numFactory = getBarSeries().numFactory();
        Num cumulative = numFactory.zero();
        for (int offset = 1; offset <= horizon; offset++) {
            Num value = normalize(returnIndicator.getValue(candidateIndex + offset), numFactory);
            if (value == null) {
                return null;
            }
            cumulative = cumulative.plus(value);
            if (!Num.isFinite(cumulative)) {
                return null;
            }
        }
        return cumulative;
    }

    private boolean assignDistances(List<Candidate> candidates, double[] currentFeatures) {
        int dimension = featureSchema.dimension();
        double[] means = new double[dimension];
        double[] sumSquaredDifferences = new double[dimension];
        int count = 0;
        for (Candidate candidate : candidates) {
            count++;
            for (int featureIndex = 0; featureIndex < dimension; featureIndex++) {
                double value = candidate.features()[featureIndex];
                double difference = value - means[featureIndex];
                means[featureIndex] += difference / count;
                sumSquaredDifferences[featureIndex] += difference * (value - means[featureIndex]);
                if (!Double.isFinite(means[featureIndex]) || !Double.isFinite(sumSquaredDifferences[featureIndex])) {
                    return false;
                }
            }
        }

        double[] scales = new double[dimension];
        for (int featureIndex = 0; featureIndex < dimension; featureIndex++) {
            scales[featureIndex] = Math.sqrt(sumSquaredDifferences[featureIndex] / candidates.size());
            if (!Double.isFinite(scales[featureIndex])) {
                return false;
            }
        }
        for (Candidate candidate : candidates) {
            double[] candidateVector = new double[dimension];
            double[] currentVector = new double[dimension];
            for (int featureIndex = 0; featureIndex < dimension; featureIndex++) {
                double scale = standardizeFeatures ? scales[featureIndex] : 1d;
                if (scale == 0d) {
                    candidateVector[featureIndex] = 0d;
                    currentVector[featureIndex] = 0d;
                } else {
                    candidateVector[featureIndex] = (candidate.features()[featureIndex] - means[featureIndex]) / scale;
                    currentVector[featureIndex] = (currentFeatures[featureIndex] - means[featureIndex]) / scale;
                }
            }
            double distance = DISTANCE.compute(candidateVector, currentVector);
            if (!Double.isFinite(distance)) {
                return false;
            }
            candidate.distance(distance);
        }
        return true;
    }

    private Forecast summarize(int index, List<Candidate> neighbors) {
        double nearestDistance = neighbors.get(0).distance();
        double weightTotal = 0d;
        double[] weights = new double[neighbors.size()];
        for (int i = 0; i < neighbors.size(); i++) {
            weights[i] = Math.exp(-(neighbors.get(i).distance() - nearestDistance));
            weightTotal += weights[i];
        }
        if (!Double.isFinite(weightTotal) || weightTotal <= 0d) {
            return Forecast.unstable(index, horizon);
        }

        NumFactory numFactory = getBarSeries().numFactory();
        List<WeightedReturn> weightedReturns = new ArrayList<>(neighbors.size());
        Num numericWeightTotal = numFactory.zero();
        Num valueScale = numFactory.zero();
        for (int i = 0; i < neighbors.size(); i++) {
            double primitiveWeight = weights[i] / weightTotal;
            Num rawWeight = numFactory.numOf(weights[i]);
            if (primitiveWeight <= 0d || !Num.isFinite(rawWeight) || rawWeight.isZero()) {
                return Forecast.unstable(index, horizon);
            }
            Num value = normalize(neighbors.get(i).realizedReturn(), numFactory);
            if (value == null) {
                return Forecast.unstable(index, horizon);
            }
            weightedReturns.add(new WeightedReturn(value, primitiveWeight, rawWeight));
            numericWeightTotal = numericWeightTotal.plus(rawWeight);
            Num magnitude = value.abs();
            if (magnitude.isGreaterThan(valueScale)) {
                valueScale = magnitude;
            }
        }
        if (!Num.isFinite(numericWeightTotal) || !numericWeightTotal.isPositive()) {
            return Forecast.unstable(index, horizon);
        }

        Num mean = numFactory.zero();
        Num standardDeviation = numFactory.zero();
        if (!valueScale.isZero()) {
            Num scaledValueTotal = numFactory.zero();
            for (WeightedReturn weightedReturn : weightedReturns) {
                scaledValueTotal = scaledValueTotal
                        .plus(weightedReturn.value().dividedBy(valueScale).multipliedBy(weightedReturn.rawWeight()));
            }
            Num scaledMean = scaledValueTotal.dividedBy(numericWeightTotal);
            mean = scaledMean.multipliedBy(valueScale);
            if (!Num.isFinite(mean) || mean.isZero() && !scaledMean.isZero()) {
                return Forecast.unstable(index, horizon);
            }

            Num scaledSquaredDeviationTotal = numFactory.zero();
            for (WeightedReturn weightedReturn : weightedReturns) {
                Num scaledDeviation = weightedReturn.value().dividedBy(valueScale).minus(scaledMean);
                scaledSquaredDeviationTotal = scaledSquaredDeviationTotal
                        .plus(scaledDeviation.multipliedBy(scaledDeviation).multipliedBy(weightedReturn.rawWeight()));
            }
            Num scaledVariance = scaledSquaredDeviationTotal.dividedBy(numericWeightTotal);
            if (!Num.isFinite(scaledVariance) || scaledVariance.isNegative()) {
                return Forecast.unstable(index, horizon);
            }
            Num scaledStandardDeviation = scaledVariance.isZero() ? numFactory.zero() : scaledVariance.sqrt();
            standardDeviation = valueScale.multipliedBy(scaledStandardDeviation);
            if (!Num.isFinite(standardDeviation) || standardDeviation.isZero() && !scaledStandardDeviation.isZero()) {
                return Forecast.unstable(index, horizon);
            }
        }

        weightedReturns.sort(Comparator.comparing(WeightedReturn::value));
        Num median = weightedQuantile(weightedReturns, 0.5d);
        Map<Double, Num> quantiles = new LinkedHashMap<>();
        for (double probability : quantileProbabilities) {
            quantiles.put(probability,
                    Double.compare(probability, 0.5d) == 0 ? median : weightedQuantile(weightedReturns, probability));
        }
        try {
            return Forecast.builder(index, horizon, numFactory, ForecastSupport.empirical(neighbors.size()))
                    .mean(mean)
                    .median(median)
                    .standardDeviation(standardDeviation)
                    .quantiles(quantiles)
                    .build();
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return Forecast.unstable(index, horizon);
        }
    }

    private static Num weightedQuantile(List<WeightedReturn> sortedValues, double probability) {
        double cumulativeWeight = 0d;
        for (WeightedReturn value : sortedValues) {
            cumulativeWeight += value.primitiveWeight();
            if (cumulativeWeight >= probability) {
                return value.value();
            }
        }
        return sortedValues.get(sortedValues.size() - 1).value();
    }

    private static Num normalize(Num value, NumFactory numFactory) {
        if (!Num.isFinite(value)) {
            return null;
        }
        Num normalized = numFactory.numOf(value.bigDecimalValue());
        return Num.isFinite(normalized) && (!normalized.isZero() || value.isZero()) ? normalized : null;
    }

    private static <S extends ReturnMomentState> org.ta4j.core.BarSeries validatedSeries(Builder<S> builder) {
        builder.validate();
        return IndicatorUtils.requireSameSeries(builder.stateIndicator, builder.returnIndicator);
    }

    private static final class Candidate {

        private final int index;
        private final double[] features;
        private final Num realizedReturn;
        private double distance;

        private Candidate(int index, double[] features, Num realizedReturn) {
            this.index = index;
            this.features = features;
            this.realizedReturn = realizedReturn;
        }

        private int index() {
            return index;
        }

        private double[] features() {
            return features;
        }

        private Num realizedReturn() {
            return realizedReturn;
        }

        private double distance() {
            return distance;
        }

        private void distance(double value) {
            distance = value;
        }
    }

    private record WeightedReturn(Num value, double primitiveWeight, Num rawWeight) {
    }

    /**
     * Builder for advanced analog settings.
     *
     * @param <S> state type
     * @since 0.23.1
     */
    public static final class Builder<S extends ReturnMomentState> {

        private final ForecastStateIndicator<S> stateIndicator;
        private final ReturnIndicator returnIndicator;
        private int horizon = 1;
        private int lookbackBarCount = 252;
        private int neighborCount = 30;
        private int minimumNeighborCount = 5;
        private boolean standardizeFeatures = true;
        private ForecastFeatureExtractor<? super S> featureExtractor = ForecastFeatureExtractors
                .meanVolatility(ReturnRepresentation.LOG);
        private List<Double> quantileProbabilities = Forecast.DEFAULT_QUANTILE_PROBABILITIES;

        private Builder(ForecastStateIndicator<S> stateIndicator, ReturnIndicator returnIndicator) {
            this.stateIndicator = Objects.requireNonNull(stateIndicator, "stateIndicator must not be null");
            this.returnIndicator = Objects.requireNonNull(returnIndicator, "returnIndicator must not be null");
        }

        /**
         * Sets the positive forecast horizon in bars.
         *
         * @param value horizon in bars
         * @return this builder
         * @since 0.23.1
         */
        public Builder<S> horizon(int value) {
            horizon = value;
            return this;
        }

        /**
         * Sets the maximum number of historical candidate decision rows.
         *
         * @param value positive candidate lookback
         * @return this builder
         * @since 0.23.1
         */
        public Builder<S> lookbackBarCount(int value) {
            lookbackBarCount = value;
            return this;
        }

        /**
         * Sets the maximum number of nearest neighbors selected.
         *
         * @param value positive neighbor count
         * @return this builder
         * @since 0.23.1
         */
        public Builder<S> neighborCount(int value) {
            neighborCount = value;
            return this;
        }

        /**
         * Sets the minimum usable neighbors required for a forecast.
         *
         * @param value positive minimum not greater than {@link #neighborCount(int)}
         * @return this builder
         * @since 0.23.1
         */
        public Builder<S> minimumNeighborCount(int value) {
            minimumNeighborCount = value;
            return this;
        }

        /**
         * Enables or disables candidate-only feature standardization.
         *
         * @param value {@code true} to standardize each feature
         * @return this builder
         * @since 0.23.1
         */
        public Builder<S> standardizeFeatures(boolean value) {
            standardizeFeatures = value;
            return this;
        }

        /**
         * Sets the fixed-schema feature extractor used for distance calculations.
         *
         * @param value representation-aware extractor
         * @return this builder
         * @since 0.23.1
         */
        public Builder<S> featureExtractor(ForecastFeatureExtractor<? super S> value) {
            featureExtractor = value;
            return this;
        }

        /**
         * Sets quantile probabilities included in each forecast.
         *
         * @param probabilities probabilities in {@code [0, 1]}
         * @return this builder
         * @since 0.23.1
         */
        public Builder<S> quantiles(double... probabilities) {
            Objects.requireNonNull(probabilities, "probabilities must not be null");
            Double[] boxed = new Double[probabilities.length];
            for (int i = 0; i < probabilities.length; i++) {
                boxed[i] = probabilities[i];
            }
            quantileProbabilities = List.of(boxed);
            return this;
        }

        /**
         * Builds the validated analog projection.
         *
         * @return configured projection
         * @since 0.23.1
         */
        public AnalogReturnProjectionIndicator<S> build() {
            validate();
            return new AnalogReturnProjectionIndicator<>(this);
        }

        private void validate() {
            if (horizon < 1 || lookbackBarCount < 1 || neighborCount < 1 || minimumNeighborCount < 1) {
                throw new IllegalArgumentException(
                        "horizon, lookbackBarCount, neighborCount, and minimumNeighborCount must be >= 1");
            }
            if (minimumNeighborCount > neighborCount || neighborCount > lookbackBarCount) {
                throw new IllegalArgumentException(
                        "minimumNeighborCount must be <= neighborCount and neighborCount must be <= lookbackBarCount");
            }
            if (returnIndicator.getReturnRepresentation() != ReturnRepresentation.LOG) {
                throw new IllegalArgumentException("returnIndicator must use ReturnRepresentation.LOG");
            }
            ForecastFeatureExtractor<? super S> extractor = Objects.requireNonNull(featureExtractor,
                    "featureExtractor must not be null");
            ForecastFeatureSchema schema = Objects.requireNonNull(extractor.schema(),
                    "featureExtractor schema must not be null");
            if (schema.representation() != ReturnRepresentation.LOG || schema.dimension() < 1) {
                throw new IllegalArgumentException("featureExtractor schema must contain log-return features");
            }
            List<Double> input = Objects.requireNonNull(quantileProbabilities,
                    "quantileProbabilities must not be null");
            if (input.isEmpty()) {
                throw new IllegalArgumentException("quantileProbabilities must not be empty");
            }
            TreeSet<Double> sorted = new TreeSet<>();
            for (Double probability : input) {
                Double value = Objects.requireNonNull(probability, "quantile probability must not be null");
                if (Double.isNaN(value) || value < 0d || value > 1d) {
                    throw new IllegalArgumentException("quantile probability must be in [0, 1]");
                }
                sorted.add(value);
            }
            quantileProbabilities = List.copyOf(sorted);
            IndicatorUtils.requireSameSeries(stateIndicator, returnIndicator);
        }
    }
}
