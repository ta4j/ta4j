/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.ReturnIndicator;
import org.ta4j.core.indicators.forecast.state.ReturnForecastState;
import org.ta4j.core.indicators.forecast.state.ReturnForecastStateIndicator;
import org.ta4j.core.indicators.statistics.EwmaVarianceIndicator;
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
 * still computed from the discarded prefix. Reads bracket the removal count and
 * the bar-history revision across the cached read and repeat until both are
 * stable, so a concurrently pruning or mutating series can never publish a
 * state computed against the discarded prefix or a state mixing moments from a
 * bar that was replaced mid-read. Series without bar-history revisions instead
 * validate retained bar values before and after each read, rebuilding the
 * owner-local recursive estimators and cached return dependencies when those
 * values change. Revision-aware reads retain constant-time validation.
 *
 * @since 0.22.9
 */
public final class EwmaReturnForecastStateIndicator extends CachedIndicator<ReturnForecastState>
        implements ReturnForecastStateIndicator<ReturnForecastState> {
    private final ReturnIndicator returnIndicator;
    private volatile transient EwmaVarianceIndicator varianceIndicator;
    private transient ValidObservationCountIndicator observationCountIndicator;
    private final int initializationBarCount;
    private final double decayFactor;
    private transient RetainedValues observedRetainedValues;
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
        this.initializationBarCount = initializationBarCount;
        this.decayFactor = decayFactor;
        this.varianceIndicator = variance;
        this.observationCountIndicator = new ValidObservationCountIndicator(returnIndicator);
        this.driftMode = Objects.requireNonNull(driftMode, "driftMode must not be null");
    }

    @Override
    public synchronized ReturnForecastState getValue(int index) {
        BarSeries series = getBarSeries();
        while (true) {
            int removedBarsCount = series.getRemovedBarsCount();
            long barHistoryRevision = series.getBarHistoryRevision();
            if (removedBarsCount != observedRemovedBarsCount) {
                resetForRetainedHead(removedBarsCount);
            }
            RetainedValues retainedValues = barHistoryRevision < 0L
                    ? series.withReadLock(() -> RetainedValues.capture(series))
                    : null;
            if (retainedValues != null
                    && (observedRetainedValues == null || !observedRetainedValues.matchesPrefixOf(retainedValues))) {
                resetForChangedValues();
            }
            ReturnForecastState value = super.getValue(index);
            if (series.getRemovedBarsCount() == removedBarsCount
                    && series.getBarHistoryRevision() == barHistoryRevision) {
                if (retainedValues == null) {
                    observedRetainedValues = null;
                    return value;
                }
                RetainedValues afterRead = series.withReadLock(() -> RetainedValues.capture(series));
                if (retainedValues.matchesPrefixOf(afterRead)) {
                    observedRetainedValues = afterRead;
                    return value;
                }
            }
            if (retainedValues != null) {
                // The source graph may have cached one side of a raced mutation.
                // Clear it outside the series read scope before rebuilding the
                // owner-local mean, variance, count, and enclosing state.
                resetForChangedValues();
                observedRetainedValues = null;
            }
            // A prune or a bar mutation raced the cached read. A prune can
            // leave the state computed against the discarded prefix; a
            // mutation of the published end bar can leave a state whose
            // mean, variance, and observation count were each read from a
            // different bar revision. Reset and read again until a full read
            // completes against a stable removal count and revision. The
            // cached read is cheap once re-anchored, so this settles as soon
            // as the series stops changing concurrently.
        }
    }

    private void resetForChangedValues() {
        invalidateCacheIncludingDependencies();
        varianceIndicator = new EwmaVarianceIndicator(returnIndicator, initializationBarCount, decayFactor);
        observationCountIndicator = new ValidObservationCountIndicator(returnIndicator);
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

    // A ReturnIndicator may read any bar field. Capture bar data only while in
    // the source read scope; evaluating its graph there would invert cache/series
    // lock order. Appends preserve the existing prefix and need no reset.
    private record RetainedValues(int beginIndex, int endIndex, BarValues[] bars) {

        private static RetainedValues capture(BarSeries series) {
            int beginIndex = series.getBeginIndex();
            int endIndex = series.getEndIndex();
            BarValues[] bars = new BarValues[series.isEmpty() ? 0 : endIndex - beginIndex + 1];
            for (int offset = 0; offset < bars.length; offset++) {
                Bar bar = series.getBar(beginIndex + offset);
                bars[offset] = new BarValues(bar.getOpenPrice(), bar.getHighPrice(), bar.getLowPrice(),
                        bar.getClosePrice(), bar.getVolume(), bar.getAmount(), bar.getTrades(), bar.getTimePeriod(),
                        bar.getBeginTime(), bar.getEndTime());
            }
            return new RetainedValues(beginIndex, endIndex, bars);
        }

        private boolean matchesPrefixOf(RetainedValues current) {
            if (beginIndex != current.beginIndex || endIndex > current.endIndex) {
                return false;
            }
            for (int offset = 0; offset < bars.length; offset++) {
                if (!bars[offset].equals(current.bars[offset])) {
                    return false;
                }
            }
            return true;
        }
    }

    private record BarValues(Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume, Num amount,
            long trades, Duration timePeriod, Instant beginTime, Instant endTime) {
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
