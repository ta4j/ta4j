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
 * prunes its retained head, a live instance continues its moments and
 * observation count from their cached predecessors. A fresh or invalidated
 * chain seeds only from retained history and excludes a synthetic head return
 * from its count. A return stream whose lookback no longer fits the retained
 * window remains unstable with no synthetic head observations. Reads bracket
 * the removal count and bar-history revision and repeat until both are stable,
 * so a state never mixes moments from different bar revisions. Series without
 * bar-history revisions validate all retained bar values before and after each
 * read, rebuilding the owner-local recursive estimators and cached return
 * dependencies when overlapping values change. Before accepting an appended
 * range, cached return dependencies are cleared while preserving moments and
 * counts from the unchanged prefix. This fallback costs O(retained bars) per
 * read; revision-aware normal continuation is independent of retention
 * capacity.
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
            RetainedValues retainedValues = barHistoryRevision < 0L
                    ? series.withReadLock(() -> RetainedValues.capture(series))
                    : null;
            if (retainedValues != null
                    && (observedRetainedValues == null || !observedRetainedValues.matchesPrefixOf(retainedValues))) {
                resetForChangedValues();
            } else if (retainedValues != null && retainedValues.endIndex() > observedRetainedValues.endIndex()) {
                // Shared return readers may have cached appended bars before this
                // owner observed them. Clear those sources before accepting the
                // extension, preserving moments and counts from the unchanged prefix.
                invalidateCacheIncludingDependencies();
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
                    // Appends raced during this read have not passed the source
                    // reset boundary yet; keep them outside the accepted baseline.
                    observedRetainedValues = retainedValues;
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
            // Retry until the tuple belongs to one stable head and revision.
            // Revision journals invalidate changed suffixes; pure eviction
            // preserves the already consumed moments and observation count.
        }
    }

    private void resetForChangedValues() {
        invalidateCacheIncludingDependencies();
        varianceIndicator = new EwmaVarianceIndicator(returnIndicator, initializationBarCount, decayFactor);
        observationCountIndicator = new ValidObservationCountIndicator(returnIndicator);
    }

    @Override
    protected boolean hasRecursiveDependencies() {
        return true;
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
    protected synchronized ReturnForecastState calculate(int index) {
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
    // lock order. Appends preserve owner-local moments and counts, but source
    // caches must be cleared before accepting a previously unobserved extension.
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
            if (endIndex > current.endIndex || current.beginIndex < beginIndex) {
                return false;
            }
            int overlapBegin = Math.max(beginIndex, current.beginIndex);
            for (int index = overlapBegin; index <= endIndex; index++) {
                if (!bars[index - beginIndex].equals(current.bars[index - current.beginIndex])) {
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
        private transient int chainStartIndex;

        private ValidObservationCountIndicator(Indicator<Num> indicator) {
            super(indicator);
            this.indicator = indicator;
        }

        @Override
        protected Integer calculate(int index) {
            int beginIndex = getBarSeries().getBeginIndex();
            if (index < beginIndex) {
                return getValue(beginIndex);
            }
            Integer previousCount = index == beginIndex ? getCachedValueOrNull(index - 1) : getValue(index - 1);
            if (previousCount == null) {
                chainStartIndex = index;
                previousCount = 0;
            }
            long firstValidIndex = (long) chainStartIndex + indicator.getCountOfUnstableBars();
            if (index < firstValidIndex || !Num.isFinite(indicator.getValue(index))) {
                return 0;
            }
            return previousCount + 1;
        }

        @Override
        public int getCountOfUnstableBars() {
            return indicator.getCountOfUnstableBars();
        }

    }

}
