/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.statistics.EwmaVarianceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Builds reusable log-return forecast state from EWMA mean and variance
 * indicators.
 *
 * <p>
 * The published mean and the variance are computed around one shared EWMA mean
 * estimator owned by the {@link EwmaVarianceIndicator}: when the backing series
 * prunes its retained head the estimator is re-anchored together with the
 * variance, the enclosing state cache and the observation-count recursion are
 * invalidated, and the count restarts at the first source value computed
 * entirely within the retained window, so retained-index reads recompute from
 * the re-anchored estimators and never return moments or observation counts
 * still computed from the discarded prefix. Reads bracket the removal count
 * across the cached read and repeat until it is stable, so a concurrently
 * pruning series can never publish a state computed against the discarded
 * prefix.
 *
 * @since 0.22.9
 */
public final class EwmaReturnForecastStateIndicator extends CachedIndicator<ReturnForecastState>
        implements ReturnForecastStateIndicator<ReturnForecastState> {
    private final ReturnIndicator returnIndicator;
    private final EwmaVarianceIndicator varianceIndicator;
    private final ValidObservationCountIndicator observationCountIndicator;
    private final DriftMode driftMode;
    private volatile transient int observedRemovedBarsCount = getBarSeries().getRemovedBarsCount();

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
        if (Double.isNaN(decayFactor) || decayFactor <= 0d || decayFactor >= 1d) {
            throw new IllegalArgumentException("decayFactor must be in (0, 1)");
        }
        EwmaVarianceIndicator variance = new EwmaVarianceIndicator(returnIndicator, initializationBarCount,
                decayFactor);
        this.returnIndicator = returnIndicator;
        this.varianceIndicator = variance;
        this.observationCountIndicator = new ValidObservationCountIndicator(returnIndicator);
        this.driftMode = Objects.requireNonNull(driftMode, "driftMode must not be null");
    }

    @Override
    public ReturnForecastState getValue(int index) {
        BarSeries series = getBarSeries();
        while (true) {
            int removedBarsCount = series.getRemovedBarsCount();
            if (removedBarsCount != observedRemovedBarsCount) {
                resetForRetainedHead(removedBarsCount);
            }
            ReturnForecastState value = super.getValue(index);
            if (series.getRemovedBarsCount() == removedBarsCount) {
                return value;
            }
            // A prune raced the cached read, so the state may still be
            // computed against the discarded prefix: reset and read again
            // until a full read completes against a stable removal count. The
            // cached read is cheap once re-anchored, so this settles as soon
            // as the series stops pruning concurrently.
        }
    }

    private synchronized void resetForRetainedHead(int removedBarsCount) {
        if (removedBarsCount != observedRemovedBarsCount) {
            // Invalidate first, publish last: a concurrent reader that
            // observes the new count must never see a state or an
            // observation count still computed from the discarded prefix.
            invalidateCache();
            observationCountIndicator.invalidateForRetainedHead();
            observedRemovedBarsCount = removedBarsCount;
        }
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
        int observationCount = observationCountIndicator.getValue(index);
        if (index < getCountOfUnstableBars()) {
            return ReturnForecastState.unstable(index, observationCount, ReturnRepresentation.LOG);
        }
        Num mean = varianceIndicator.getMeanIndicator().getValue(index);
        Num variance = varianceIndicator.getValue(index);
        if (!Num.isFinite(mean) || !Num.isFinite(variance)) {
            return ReturnForecastState.unstable(index, observationCount, ReturnRepresentation.LOG);
        }
        Num drift = driftMode == DriftMode.ZERO ? getBarSeries().numFactory().zero() : mean;
        return ReturnForecastState.stable(index, observationCount, ReturnRepresentation.LOG, mean, drift, variance);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.9
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(varianceIndicator.getMeanIndicator().getCountOfUnstableBars(),
                varianceIndicator.getCountOfUnstableBars());
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

    private static final class ValidObservationCountIndicator extends RecursiveCachedIndicator<Integer> {

        private final Indicator<Num> indicator;

        private ValidObservationCountIndicator(Indicator<Num> indicator) {
            super(indicator);
            this.indicator = indicator;
        }

        private void invalidateForRetainedHead() {
            invalidateCache();
        }

        @Override
        protected Integer calculate(int index) {
            int beginIndex = getBarSeries().getBeginIndex();
            // The count restarts past the retained head at the first index
            // where the source is computed entirely within the retained
            // window: the moments seed their windows at beginIndex plus the
            // source's unstable bars, so the count anchors there too. For a
            // lookback source such as LogReturnIndicator, the pruned head
            // publishes an artificial zero against the removed predecessor
            // and must not be counted.
            long firstValidIndex = (long) beginIndex + indicator.getCountOfUnstableBars();
            if (index < beginIndex || index < firstValidIndex || !Num.isFinite(indicator.getValue(index))) {
                return 0;
            }
            return index == firstValidIndex ? 1 : getValue(index - 1) + 1;
        }

        @Override
        public int getCountOfUnstableBars() {
            return indicator.getCountOfUnstableBars();
        }
    }

}
