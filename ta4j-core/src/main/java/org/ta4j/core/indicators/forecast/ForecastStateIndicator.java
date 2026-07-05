/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.frequency.SampleSummary;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.averages.EWMAIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Builds reusable forecast state from mean and variance indicators.
 *
 * @since 0.22.9
 */
public final class ForecastStateIndicator extends CachedIndicator<ReturnForecastState> {

    private final Indicator<Num> meanIndicator;
    private final Indicator<Num> varianceIndicator;
    private final int initialObservationCount;
    private final DriftMode driftMode;

    /**
     * Constructor.
     *
     * @param meanIndicator           mean source
     * @param varianceIndicator       variance source
     * @param initialObservationCount observations represented by the first stable
     *                                state
     * @param driftMode               drift assumption
     * @since 0.22.9
     */
    public ForecastStateIndicator(Indicator<Num> meanIndicator, Indicator<Num> varianceIndicator,
            int initialObservationCount, DriftMode driftMode) {
        super(IndicatorUtils.requireSameSeries(Objects.requireNonNull(meanIndicator, "meanIndicator must not be null"),
                Objects.requireNonNull(varianceIndicator, "varianceIndicator must not be null")));
        if (initialObservationCount < 1) {
            throw new IllegalArgumentException("initialObservationCount must be >= 1");
        }
        this.meanIndicator = meanIndicator;
        this.varianceIndicator = varianceIndicator;
        this.initialObservationCount = initialObservationCount;
        this.driftMode = Objects.requireNonNull(driftMode, "driftMode must not be null");
    }

    /**
     * Creates an EWMA-backed forecast state indicator.
     *
     * @param indicator              return source
     * @param initializationBarCount observations required before the state is
     *                               stable
     * @param decayFactor            EWMA decay factor in {@code (0, 1)}
     * @param driftMode              drift assumption
     * @return forecast state indicator
     * @since 0.22.9
     */
    public static ForecastStateIndicator ofEwma(Indicator<Num> indicator, int initializationBarCount,
            double decayFactor, DriftMode driftMode) {
        Indicator<Num> validatedIndicator = Objects.requireNonNull(indicator, "indicator must not be null");
        EWMAIndicator mean = new EWMAIndicator(validatedIndicator, initializationBarCount, decayFactor);
        Indicator<Num> variance = new EwmaVarianceIndicator(validatedIndicator, mean, initializationBarCount,
                decayFactor);
        return new ForecastStateIndicator(mean, variance, initializationBarCount, driftMode);
    }

    @Override
    protected ReturnForecastState calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return ReturnForecastState.unstable(index);
        }
        Num mean = meanIndicator.getValue(index);
        Num variance = varianceIndicator.getValue(index);
        if (IndicatorUtils.isInvalid(mean) || IndicatorUtils.isInvalid(variance)) {
            return ReturnForecastState.unstable(index);
        }
        Num volatility = variance.sqrt();
        if (IndicatorUtils.isInvalid(volatility)) {
            return ReturnForecastState.unstable(index);
        }
        Num drift = driftMode == DriftMode.ZERO ? getBarSeries().numFactory().zero() : mean;
        int observationCount = initialObservationCount + index - getCountOfUnstableBars();
        return new ReturnForecastState(index, observationCount, true, mean, drift, variance, volatility);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(meanIndicator.getCountOfUnstableBars(), varianceIndicator.getCountOfUnstableBars());
    }

    /**
     * Drift assumption used when converting return state to forecast paths.
     *
     * @since 0.22.9
     */
    public enum DriftMode {

        /**
         * Use zero drift.
         *
         * @since 0.22.9
         */
        ZERO,

        /**
         * Use the rolling mean as drift.
         *
         * @since 0.22.9
         */
        ROLLING_MEAN
    }

    private static final class EwmaVarianceIndicator extends RecursiveCachedIndicator<Num> {

        private final Indicator<Num> indicator;
        private final Indicator<Num> meanIndicator;
        private final int barCount;
        private final double decayFactor;

        private EwmaVarianceIndicator(Indicator<Num> indicator, Indicator<Num> meanIndicator, int barCount,
                double decayFactor) {
            super(IndicatorUtils.requireSameSeries(indicator, meanIndicator));
            this.indicator = indicator;
            this.meanIndicator = meanIndicator;
            this.barCount = barCount;
            this.decayFactor = decayFactor;
        }

        @Override
        protected Num calculate(int index) {
            if (index < getCountOfUnstableBars()) {
                return NaN.NaN;
            }
            Num current = indicator.getValue(index);
            if (IndicatorUtils.isInvalid(current)) {
                return NaN.NaN;
            }
            Num previousVariance = getValue(index - 1);
            Num previousMean = meanIndicator.getValue(index - 1);
            if (IndicatorUtils.isInvalid(previousVariance) || IndicatorUtils.isInvalid(previousMean)) {
                return initialVariance(index);
            }
            Num decay = getBarSeries().numFactory().numOf(decayFactor);
            Num oneMinusDecay = getBarSeries().numFactory().one().minus(decay);
            Num deviation = current.minus(previousMean);
            return previousVariance.multipliedBy(decay)
                    .plus(deviation.multipliedBy(deviation).multipliedBy(oneMinusDecay));
        }

        @Override
        public int getCountOfUnstableBars() {
            return Math.max(meanIndicator.getCountOfUnstableBars(), indicator.getCountOfUnstableBars() + barCount - 1);
        }

        private Num initialVariance(int index) {
            List<Num> values = new ArrayList<>(barCount);
            int startIndex = index - barCount + 1;
            for (int i = startIndex; i <= index; i++) {
                Num value = indicator.getValue(i);
                if (IndicatorUtils.isInvalid(value)) {
                    return NaN.NaN;
                }
                values.add(value);
            }
            SampleSummary summary = SampleSummary.fromValues(values.stream(), getBarSeries().numFactory());
            return summary.m2().dividedBy(getBarSeries().numFactory().numOf(values.size()));
        }
    }
}
