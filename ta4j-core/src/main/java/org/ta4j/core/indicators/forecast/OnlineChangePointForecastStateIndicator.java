/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.commons.math3.special.Gamma;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.state.OnlineChangePointForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.indicators.forecast.state.RunLengthPosterior;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Estimates log-return regime moments and run-length uncertainty with Bayesian
 * online change-point detection.
 *
 * <p>
 * The shortest operator path uses an expected regime length of 100
 * observations, retains run lengths through 252, reports the top five
 * hypotheses, and becomes stable after 20 consecutive valid observations:
 *
 * <pre>{@code
 * OnlineChangePointForecastStateIndicator states = new OnlineChangePointForecastStateIndicator(logReturns);
 * }</pre>
 *
 * <p>
 * Advanced construction keeps assumptions explicit:
 *
 * <pre>{@code
 * OnlineChangePointForecastStateIndicator states = OnlineChangePointForecastStateIndicator.builder(logReturns)
 *         .expectedRunLength(150)
 *         .maximumRunLength(500)
 *         .topRunLengthCount(8)
 *         .minimumObservationCount(30)
 *         .recentChangeWindow(10)
 *         .build();
 * }</pre>
 *
 * <p>
 * The filter uses a constant hazard, a Normal-Inverse-Gamma conjugate model,
 * Student-t predictive densities, and log-space normalization. In the
 * untruncated constant-hazard filter, the posterior probability of run length
 * zero equals that hazard. Hard tail truncation and renormalization can
 * increase it slightly, but it still is not a responsive change signal.
 * Therefore, {@link OnlineChangePointForecastState#recentChangeProbability()}
 * reports posterior mass over run lengths zero through the configured recent
 * window. A non-finite input resets the filter, and the full valid-run warm-up
 * is required again.
 *
 * @since 0.23.1
 */
public final class OnlineChangePointForecastStateIndicator extends AbstractIndicator<OnlineChangePointForecastState>
        implements ReturnForecastStateIndicator<OnlineChangePointForecastState> {

    private final ReturnIndicator returnIndicator;
    private final double expectedRunLength;
    private final int maximumRunLength;
    private final int topRunLengthCount;
    private final int minimumObservationCount;
    private final int recentChangeWindow;
    private final double priorMean;
    private final double priorMeanPrecision;
    private final double priorShape;
    private final double priorScale;
    private transient volatile PosteriorFrameIndicator posteriorIndicator;
    private transient volatile int posteriorHistoryStart;

    /**
     * Creates online change-point state with operator defaults.
     *
     * @param returnIndicator log-return source
     * @since 0.23.1
     */
    public OnlineChangePointForecastStateIndicator(ReturnIndicator returnIndicator) {
        this(builder(returnIndicator));
    }

    OnlineChangePointForecastStateIndicator(ReturnIndicator returnIndicator, double expectedRunLength,
            int maximumRunLength, int topRunLengthCount, int minimumObservationCount, int recentChangeWindow,
            double priorMean, double priorMeanPrecision, double priorShape, double priorScale) {
        this(builder(returnIndicator).expectedRunLength(expectedRunLength)
                .maximumRunLength(maximumRunLength)
                .topRunLengthCount(topRunLengthCount)
                .minimumObservationCount(minimumObservationCount)
                .recentChangeWindow(recentChangeWindow)
                .priorMean(priorMean)
                .priorMeanPrecision(priorMeanPrecision)
                .priorShape(priorShape)
                .priorScale(priorScale));
    }

    private OnlineChangePointForecastStateIndicator(Builder builder) {
        super(validatedReturnIndicator(builder).getBarSeries());
        this.returnIndicator = builder.returnIndicator;
        this.expectedRunLength = builder.expectedRunLength;
        this.maximumRunLength = builder.maximumRunLength;
        this.topRunLengthCount = builder.topRunLengthCount;
        this.minimumObservationCount = builder.minimumObservationCount;
        this.recentChangeWindow = builder.recentChangeWindow;
        this.priorMean = builder.priorMean;
        this.priorMeanPrecision = builder.priorMeanPrecision;
        this.priorShape = builder.priorShape;
        this.priorScale = builder.priorScale;
        this.posteriorHistoryStart = getBarSeries().getRemovedBarsCount();
        this.posteriorIndicator = newPosteriorIndicator(posteriorHistoryStart);
    }

    /**
     * Starts an advanced online change-point builder.
     *
     * @param returnIndicator log-return source
     * @return builder with operator defaults
     * @since 0.23.1
     */
    public static Builder builder(ReturnIndicator returnIndicator) {
        return new Builder(returnIndicator);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public ReturnIndicator getReturnIndicator() {
        return returnIndicator;
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

    /**
     * Returns state at the requested source index.
     *
     * @param index source index
     * @return stable state when the valid-run warm-up and numeric checks pass;
     *         otherwise unavailable state
     * @since 0.23.1
     */
    @Override
    public OnlineChangePointForecastState getValue(int index) {
        int historyStart = getBarSeries().getRemovedBarsCount();
        if (index < historyStart) {
            return OnlineChangePointForecastState.unstable(index, 0);
        }
        PosteriorFrameIndicator model = posteriorModel(historyStart);
        PosteriorFrame frame = model.getValue(index);
        if (getBarSeries().getRemovedBarsCount() != historyStart) {
            historyStart = getBarSeries().getRemovedBarsCount();
            if (index < historyStart) {
                return OnlineChangePointForecastState.unstable(index, 0);
            }
            model = posteriorModel(historyStart);
            frame = model.getValue(index);
        }
        return toState(index, frame);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.23.1
     */
    @Override
    public int getCountOfUnstableBars() {
        return returnIndicator.getCountOfUnstableBars() + minimumObservationCount - 1;
    }

    private OnlineChangePointForecastState toState(int index, PosteriorFrame frame) {
        if (!frame.isAvailable() || frame.observationCount < minimumObservationCount) {
            return OnlineChangePointForecastState.unstable(index, frame.observationCount);
        }
        try {
            NumFactory numFactory = getBarSeries().numFactory();
            List<Integer> orderedRunLengths = new ArrayList<>(frame.size());
            for (int runLength = 0; runLength < frame.size(); runLength++) {
                orderedRunLengths.add(runLength);
            }
            orderedRunLengths.sort((left, right) -> {
                int probabilityOrder = Double.compare(frame.logProbabilities[right], frame.logProbabilities[left]);
                return probabilityOrder != 0 ? probabilityOrder : Integer.compare(left, right);
            });
            int publishedCount = Math.min(topRunLengthCount, orderedRunLengths.size());
            List<RunLengthPosterior> candidates = new ArrayList<>(publishedCount);
            for (int position = 0; position < publishedCount; position++) {
                int runLength = orderedRunLengths.get(position);
                double probability = Math.exp(frame.logProbabilities[runLength]);
                if (probability == 0d && Double.isFinite(frame.logProbabilities[runLength])) {
                    probability = Double.MIN_VALUE;
                }
                double variance = frame.scales[runLength] / (frame.shapes[runLength] - 1d);
                RunLengthPosterior posterior = new RunLengthPosterior(runLength,
                        normalizedNum(probability, numFactory, "posterior probability"),
                        normalizedNum(frame.means[runLength], numFactory, "posterior mean"),
                        normalizedNum(variance, numFactory, "posterior variance"));
                candidates.add(posterior);
            }
            candidates.sort(OnlineChangePointForecastStateIndicator::comparePosteriors);
            List<RunLengthPosterior> topRunLengths = List.copyOf(candidates);
            RunLengthPosterior mostLikely = topRunLengths.get(0);

            double recentProbability = 0d;
            int lastRecentRunLength = Math.min(recentChangeWindow, frame.size() - 1);
            for (int runLength = 0; runLength <= lastRecentRunLength; runLength++) {
                recentProbability += Math.exp(frame.logProbabilities[runLength]);
            }
            recentProbability = Math.max(0d, Math.min(1d, recentProbability));
            Num recentChangeProbability = normalizedNum(recentProbability, numFactory, "recent change probability");
            ReturnMoments moments = ReturnMoments.stable(index, frame.observationCount, ReturnRepresentation.LOG,
                    mostLikely.mean(), mostLikely.mean(), mostLikely.variance());
            return OnlineChangePointForecastState.stable(moments, recentChangeProbability, mostLikely.runLength(),
                    topRunLengths);
        } catch (IllegalArgumentException | ArithmeticException exception) {
            return OnlineChangePointForecastState.unstable(index, frame.observationCount);
        }
    }

    private PosteriorFrameIndicator posteriorModel(int historyStart) {
        PosteriorFrameIndicator current = posteriorIndicator;
        if (current != null && posteriorHistoryStart == historyStart) {
            return current;
        }
        synchronized (this) {
            if (posteriorIndicator == null || posteriorHistoryStart != historyStart) {
                PosteriorFrameIndicator replacement = newPosteriorIndicator(historyStart);
                posteriorIndicator = replacement;
                posteriorHistoryStart = historyStart;
            }
            return posteriorIndicator;
        }
    }

    private PosteriorFrameIndicator newPosteriorIndicator(int historyStart) {
        return new PosteriorFrameIndicator(returnIndicator, historyStart, expectedRunLength, maximumRunLength,
                priorMean, priorMeanPrecision, priorShape, priorScale);
    }

    private static int comparePosteriors(RunLengthPosterior left, RunLengthPosterior right) {
        if (left.probability().isGreaterThan(right.probability())) {
            return -1;
        }
        if (left.probability().isLessThan(right.probability())) {
            return 1;
        }
        return Integer.compare(left.runLength(), right.runLength());
    }

    private static Num normalizedNum(double value, NumFactory numFactory, String fieldName) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        Num normalized = numFactory.numOf(value);
        if (!Num.isFinite(normalized) || (normalized.isZero() && value != 0d)) {
            throw new IllegalArgumentException(fieldName + " cannot be represented by the series factory");
        }
        return normalized;
    }

    private static ReturnIndicator validatedReturnIndicator(Builder builder) {
        builder.validate();
        return builder.returnIndicator;
    }

    /**
     * Builder for advanced online change-point assumptions.
     *
     * @since 0.23.1
     */
    public static final class Builder {

        private final ReturnIndicator returnIndicator;
        private double expectedRunLength = 100d;
        private int maximumRunLength = 252;
        private int topRunLengthCount = 5;
        private int minimumObservationCount = 20;
        private int recentChangeWindow = 5;
        private double priorMean;
        private double priorMeanPrecision = 1e-4d;
        private double priorShape = 2d;
        private double priorScale = 1e-4d;

        private Builder(ReturnIndicator returnIndicator) {
            this.returnIndicator = Objects.requireNonNull(returnIndicator, "returnIndicator must not be null");
        }

        /**
         * Sets the constant-hazard expected regime length.
         *
         * @param value finite expected observations greater than one
         * @return this builder
         * @since 0.23.1
         */
        public Builder expectedRunLength(double value) {
            expectedRunLength = value;
            return this;
        }

        /**
         * Sets the largest retained run length.
         *
         * @param value positive retained run length below {@link Integer#MAX_VALUE}
         * @return this builder
         * @since 0.23.1
         */
        public Builder maximumRunLength(int value) {
            maximumRunLength = value;
            return this;
        }

        /**
         * Sets the number of posterior summaries exposed in stable state.
         *
         * @param value positive count no larger than the complete posterior shape
         * @return this builder
         * @since 0.23.1
         */
        public Builder topRunLengthCount(int value) {
            topRunLengthCount = value;
            return this;
        }

        /**
         * Sets the consecutive valid observations required for stable state.
         *
         * @param value positive warm-up count
         * @return this builder
         * @since 0.23.1
         */
        public Builder minimumObservationCount(int value) {
            minimumObservationCount = value;
            return this;
        }

        /**
         * Sets the inclusive run-length boundary used by recent change probability.
         *
         * @param value positive boundary no larger than the maximum run length
         * @return this builder
         * @since 0.23.1
         */
        public Builder recentChangeWindow(int value) {
            recentChangeWindow = value;
            return this;
        }

        /**
         * Sets the Normal-Inverse-Gamma prior mean.
         *
         * @param value finite prior mean
         * @return this builder
         * @since 0.23.1
         */
        public Builder priorMean(double value) {
            priorMean = value;
            return this;
        }

        /**
         * Sets the positive prior precision of the mean.
         *
         * @param value finite positive precision
         * @return this builder
         * @since 0.23.1
         */
        public Builder priorMeanPrecision(double value) {
            priorMeanPrecision = value;
            return this;
        }

        /**
         * Sets the Inverse-Gamma shape.
         *
         * @param value finite shape greater than one
         * @return this builder
         * @since 0.23.1
         */
        public Builder priorShape(double value) {
            priorShape = value;
            return this;
        }

        /**
         * Sets the positive Inverse-Gamma scale.
         *
         * @param value finite positive scale
         * @return this builder
         * @since 0.23.1
         */
        public Builder priorScale(double value) {
            priorScale = value;
            return this;
        }

        /**
         * Builds the configured state estimator.
         *
         * @return online change-point indicator
         * @since 0.23.1
         */
        public OnlineChangePointForecastStateIndicator build() {
            return new OnlineChangePointForecastStateIndicator(this);
        }

        private void validate() {
            if (returnIndicator.getReturnRepresentation() != ReturnRepresentation.LOG) {
                throw new IllegalArgumentException("returnIndicator must use ReturnRepresentation.LOG");
            }
            if (!Double.isFinite(expectedRunLength) || expectedRunLength <= 1d) {
                throw new IllegalArgumentException("expectedRunLength must be finite and > 1");
            }
            if (maximumRunLength < 1 || maximumRunLength == Integer.MAX_VALUE) {
                throw new IllegalArgumentException("maximumRunLength must be in [1, Integer.MAX_VALUE - 1]");
            }
            if (topRunLengthCount < 1 || topRunLengthCount > maximumRunLength + 1) {
                throw new IllegalArgumentException("topRunLengthCount must be in [1, maximumRunLength + 1]");
            }
            if (minimumObservationCount < 1) {
                throw new IllegalArgumentException("minimumObservationCount must be >= 1");
            }
            if (recentChangeWindow < 1 || recentChangeWindow > maximumRunLength) {
                throw new IllegalArgumentException("recentChangeWindow must be in [1, maximumRunLength]");
            }
            if (!Double.isFinite(priorMean)) {
                throw new IllegalArgumentException("priorMean must be finite");
            }
            if (!Double.isFinite(priorMeanPrecision) || priorMeanPrecision <= 0d) {
                throw new IllegalArgumentException("priorMeanPrecision must be finite and > 0");
            }
            if (!Double.isFinite(priorShape) || priorShape <= 1d) {
                throw new IllegalArgumentException("priorShape must be finite and > 1");
            }
            if (!Double.isFinite(priorScale) || priorScale <= 0d) {
                throw new IllegalArgumentException("priorScale must be finite and > 0");
            }
        }
    }

    private static final class PosteriorFrameIndicator extends RecursiveCachedIndicator<PosteriorFrame> {

        private final ReturnIndicator source;
        private final int historyStart;
        private final int maximumRunLength;
        private final double logHazard;
        private final double logSurvival;
        private final double priorMean;
        private final double priorMeanPrecision;
        private final double priorShape;
        private final double priorScale;

        private PosteriorFrameIndicator(ReturnIndicator source, int historyStart, double expectedRunLength,
                int maximumRunLength, double priorMean, double priorMeanPrecision, double priorShape,
                double priorScale) {
            super(source);
            this.source = source;
            this.historyStart = historyStart;
            this.maximumRunLength = maximumRunLength;
            double hazard = 1d / expectedRunLength;
            this.logHazard = Math.log(hazard);
            this.logSurvival = Math.log1p(-hazard);
            this.priorMean = priorMean;
            this.priorMeanPrecision = priorMeanPrecision;
            this.priorShape = priorShape;
            this.priorScale = priorScale;
        }

        @Override
        protected PosteriorFrame calculate(int index) {
            Num value = source.getValue(index);
            double observation = finitePrimitive(value);
            int sourceUnstableBars = source.getCountOfUnstableBars();
            if (index < historyStart || index - historyStart < sourceUnstableBars || !Double.isFinite(observation)) {
                return PosteriorFrame.unavailable();
            }
            PosteriorFrame previous = index == historyStart
                    ? PosteriorFrame.initial(priorMean, priorMeanPrecision, priorShape, priorScale)
                    : getValue(index - 1);
            if (!previous.isAvailable()) {
                previous = PosteriorFrame.initial(priorMean, priorMeanPrecision, priorShape, priorScale);
            }
            return advance(previous, observation);
        }

        @Override
        public int getCountOfUnstableBars() {
            return source.getCountOfUnstableBars();
        }

        private PosteriorFrame advance(PosteriorFrame previous, double observation) {
            int resultSize = previous.size() > maximumRunLength ? previous.size() : previous.size() + 1;
            double[] logWeights = new double[resultSize];
            double[] means = new double[resultSize];
            double[] precisions = new double[resultSize];
            double[] shapes = new double[resultSize];
            double[] scales = new double[resultSize];
            Arrays.fill(logWeights, Double.NEGATIVE_INFINITY);
            updateComponent(observation, priorMean, priorMeanPrecision, priorShape, priorScale, 0, means, precisions,
                    shapes, scales);

            for (int runLength = 0; runLength < previous.size(); runLength++) {
                double logPredictive = studentTLogDensity(observation, previous.means[runLength],
                        previous.precisions[runLength], previous.shapes[runLength], previous.scales[runLength]);
                if (!Double.isFinite(logPredictive)) {
                    continue;
                }
                double previousWeight = previous.logProbabilities[runLength] + logPredictive;
                logWeights[0] = logAdd(logWeights[0], previousWeight + logHazard);
                int grownRunLength = runLength + 1;
                if (grownRunLength < resultSize) {
                    logWeights[grownRunLength] = previousWeight + logSurvival;
                    updateComponent(observation, previous.means[runLength], previous.precisions[runLength],
                            previous.shapes[runLength], previous.scales[runLength], grownRunLength, means, precisions,
                            shapes, scales);
                }
            }

            double logEvidence = logSum(logWeights);
            if (!Double.isFinite(logEvidence)) {
                return PosteriorFrame.unavailable();
            }
            for (int runLength = 0; runLength < resultSize; runLength++) {
                logWeights[runLength] -= logEvidence;
                if (Double.isNaN(logWeights[runLength]) || !validComponent(means[runLength], precisions[runLength],
                        shapes[runLength], scales[runLength])) {
                    return PosteriorFrame.unavailable();
                }
            }
            return new PosteriorFrame(previous.observationCount + 1, logWeights, means, precisions, shapes, scales);
        }

        private static void updateComponent(double observation, double mean, double precision, double shape,
                double scale, int target, double[] means, double[] precisions, double[] shapes, double[] scales) {
            double updatedPrecision = precision + 1d;
            double difference = observation - mean;
            means[target] = (precision * mean + observation) / updatedPrecision;
            precisions[target] = updatedPrecision;
            shapes[target] = shape + 0.5d;
            scales[target] = scale + precision * difference * difference / (2d * updatedPrecision);
        }

        private static boolean validComponent(double mean, double precision, double shape, double scale) {
            return Double.isFinite(mean) && Double.isFinite(precision) && precision > 0d && Double.isFinite(shape)
                    && shape > 1d && Double.isFinite(scale) && scale > 0d;
        }

        private static double studentTLogDensity(double observation, double mean, double precision, double shape,
                double scale) {
            double degreesOfFreedom = 2d * shape;
            double predictiveScaleSquared = scale * (precision + 1d) / (shape * precision);
            if (!Double.isFinite(degreesOfFreedom) || degreesOfFreedom <= 0d || !Double.isFinite(predictiveScaleSquared)
                    || predictiveScaleSquared <= 0d) {
                return Double.NaN;
            }
            double difference = observation - mean;
            double logRatio;
            if (difference == 0d) {
                logRatio = Double.NEGATIVE_INFINITY;
            } else {
                logRatio = 2d * Math.log(Math.abs(difference)) - Math.log(degreesOfFreedom)
                        - Math.log(predictiveScaleSquared);
            }
            double logKernel = logOnePlusExp(logRatio);
            return Gamma.logGamma((degreesOfFreedom + 1d) * 0.5d) - Gamma.logGamma(degreesOfFreedom * 0.5d)
                    - 0.5d * (Math.log(degreesOfFreedom * Math.PI) + Math.log(predictiveScaleSquared))
                    - 0.5d * (degreesOfFreedom + 1d) * logKernel;
        }

        private static double logOnePlusExp(double value) {
            if (value == Double.NEGATIVE_INFINITY) {
                return 0d;
            }
            return value > 0d ? value + Math.log1p(Math.exp(-value)) : Math.log1p(Math.exp(value));
        }

        private static double logAdd(double left, double right) {
            if (left == Double.NEGATIVE_INFINITY) {
                return right;
            }
            if (right == Double.NEGATIVE_INFINITY) {
                return left;
            }
            double maximum = Math.max(left, right);
            return maximum + Math.log(Math.exp(left - maximum) + Math.exp(right - maximum));
        }

        private static double logSum(double[] values) {
            double maximum = Double.NEGATIVE_INFINITY;
            for (double value : values) {
                maximum = Math.max(maximum, value);
            }
            if (!Double.isFinite(maximum)) {
                return Double.NaN;
            }
            double total = 0d;
            for (double value : values) {
                total += Math.exp(value - maximum);
            }
            return maximum + Math.log(total);
        }

        private static double finitePrimitive(Num value) {
            if (!Num.isFinite(value)) {
                return Double.NaN;
            }
            double primitive = value.doubleValue();
            return Double.isFinite(primitive) && (primitive != 0d || value.isZero()) ? primitive : Double.NaN;
        }
    }

    private static final class PosteriorFrame {

        private final int observationCount;
        private final double[] logProbabilities;
        private final double[] means;
        private final double[] precisions;
        private final double[] shapes;
        private final double[] scales;

        private PosteriorFrame(int observationCount, double[] logProbabilities, double[] means, double[] precisions,
                double[] shapes, double[] scales) {
            this.observationCount = observationCount;
            this.logProbabilities = logProbabilities;
            this.means = means;
            this.precisions = precisions;
            this.shapes = shapes;
            this.scales = scales;
        }

        private static PosteriorFrame initial(double priorMean, double priorMeanPrecision, double priorShape,
                double priorScale) {
            return new PosteriorFrame(0, new double[] { 0d }, new double[] { priorMean },
                    new double[] { priorMeanPrecision }, new double[] { priorShape }, new double[] { priorScale });
        }

        private static PosteriorFrame unavailable() {
            return new PosteriorFrame(0, new double[0], new double[0], new double[0], new double[0], new double[0]);
        }

        private int size() {
            return logProbabilities.length;
        }

        private boolean isAvailable() {
            return size() > 0;
        }
    }
}
