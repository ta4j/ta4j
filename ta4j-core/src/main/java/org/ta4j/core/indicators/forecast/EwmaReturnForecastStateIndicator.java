/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.averages.EWMAIndicator;
import org.ta4j.core.indicators.statistics.VarianceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Builds reusable log-return forecast state from EWMA mean and variance
 * indicators.
 *
 * @since 0.22.9
 */
public final class EwmaReturnForecastStateIndicator extends CachedIndicator<ReturnForecastState>
        implements ReturnForecastStateIndicator {

    private final ReturnIndicator returnIndicator;
    private final Indicator<Num> meanIndicator;
    private final Indicator<Num> varianceIndicator;
    private final int initialObservationCount;
    private final DriftMode driftMode;

    /**
     * Constructor using default EWMA settings and zero drift.
     *
     * @param returnIndicator log-return source
     * @since 0.22.9
     */
    public EwmaReturnForecastStateIndicator(ReturnIndicator returnIndicator) {
        this(returnIndicator, 30, 0.94d);
    }

    /**
     * Constructor using EWMA mean and variance with zero drift.
     *
     * @param returnIndicator        log-return source
     * @param initializationBarCount observations required before the state is
     *                               stable
     * @param decayFactor            EWMA decay factor in {@code (0, 1)}
     * @since 0.22.9
     */
    public EwmaReturnForecastStateIndicator(ReturnIndicator returnIndicator, int initializationBarCount,
            double decayFactor) {
        this(returnIndicator, initializationBarCount, decayFactor, DriftMode.ZERO);
    }

    /**
     * Constructor using EWMA mean and variance.
     *
     * @param returnIndicator        log-return source
     * @param initializationBarCount observations required before the state is
     *                               stable
     * @param decayFactor            EWMA decay factor in {@code (0, 1)}
     * @param driftMode              drift assumption
     * @since 0.22.9
     */
    public EwmaReturnForecastStateIndicator(ReturnIndicator returnIndicator, int initializationBarCount,
            double decayFactor, DriftMode driftMode) {
        super(validateLogReturnIndicator(returnIndicator));
        if (initializationBarCount < 1) {
            throw new IllegalArgumentException("initializationBarCount must be >= 1");
        }
        Indicator<Num> mean = new EWMAIndicator(returnIndicator, initializationBarCount, decayFactor);
        this.returnIndicator = returnIndicator;
        this.meanIndicator = mean;
        this.varianceIndicator = new EwmaVarianceIndicator(returnIndicator, mean, initializationBarCount, decayFactor);
        this.initialObservationCount = initializationBarCount;
        this.driftMode = Objects.requireNonNull(driftMode, "driftMode must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public ReturnIndicator getReturnIndicator() {
        return returnIndicator;
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public ReturnRepresentation getReturnRepresentation() {
        return ReturnRepresentation.LOG;
    }

    private static ReturnIndicator validateLogReturnIndicator(ReturnIndicator returnIndicator) {
        ReturnIndicator validated = Objects.requireNonNull(returnIndicator, "returnIndicator must not be null");
        if (validated.getReturnRepresentation() != ReturnRepresentation.LOG) {
            throw new IllegalArgumentException("returnIndicator must use ReturnRepresentation.LOG");
        }
        return validated;
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
        private final Indicator<Num> initialVarianceIndicator;
        private final int barCount;
        private final double decayFactor;

        private EwmaVarianceIndicator(Indicator<Num> indicator, Indicator<Num> meanIndicator, int barCount,
                double decayFactor) {
            super(IndicatorUtils.requireSameSeries(indicator, meanIndicator));
            this.indicator = indicator;
            this.meanIndicator = meanIndicator;
            this.initialVarianceIndicator = VarianceIndicator.ofPopulation(indicator, barCount);
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
            return initialVarianceIndicator.getValue(index);
        }
    }
}
