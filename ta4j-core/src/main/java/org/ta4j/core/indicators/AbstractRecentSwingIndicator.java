/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Base class for swing-point indicators that exposes both swing values and
 * confirmed swing indexes.
 * <p>
 * Subclasses implement the swing-detection logic via
 * {@link #detectLatestSwingIndex(int)}. This base class handles caching of
 * swing indexes, purges indexes that fall out of the series window, and
 * provides access to swing-point values through the {@link Indicator} API.
 *
 * @since 0.20
 */
public abstract class AbstractRecentSwingIndicator extends CachedIndicator<Num> implements RecentSwingIndicator {

    private final Indicator<Num> priceIndicator;
    private final SwingPointTracker swingPoints;
    private final int unstableBars;

    /**
     * Constructor.
     *
     * @param priceIndicator the price indicator used to fetch swing values
     * @param unstableBars   number of unstable bars
     */
    protected AbstractRecentSwingIndicator(Indicator<Num> priceIndicator, int unstableBars) {
        super(priceIndicator);
        this.priceIndicator = Objects.requireNonNull(priceIndicator, "priceIndicator cannot be null");
        this.unstableBars = Math.max(0, unstableBars);
        final BarSeries series = Objects.requireNonNull(priceIndicator.getBarSeries(),
                "priceIndicator.getBarSeries() cannot be null");
        this.swingPoints = new SwingPointTracker(this::detectLatestSwingIndex, series);
    }

    @Override
    public final int getLatestSwingIndex(int index) {
        return swingPoints.getLatestSwingIndex(index);
    }

    @Override
    public final List<Integer> getSwingPointIndexesUpTo(int index) {
        return swingPoints.getSwingPointIndexes(index);
    }

    @Override
    public final List<Integer> getSwingPointIndexes() {
        final BarSeries series = getBarSeries();
        return series == null ? List.of() : swingPoints.getSwingPointIndexes(series.getEndIndex());
    }

    @Override
    public Indicator<Num> getPriceIndicator() {
        return priceIndicator;
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }

    @Override
    protected Num calculate(int index) {
        final BarSeries series = getBarSeries();
        if (series == null) {
            return NaN;
        }
        final int beginIndex = series.getBeginIndex();
        final int endIndex = series.getEndIndex();
        if (index < beginIndex || index > endIndex) {
            return NaN;
        }
        final int swingIndex = getLatestSwingIndex(index);
        if (swingIndex < beginIndex) {
            return NaN;
        }
        final Num swingValue = priceIndicator.getValue(swingIndex);
        return Num.isNaNOrNull(swingValue) ? NaN : swingValue;
    }

    /**
     * Returns the most recent confirmed swing point index that can be evaluated
     * using data up to the given index.
     *
     * @param index the current evaluation index
     * @return the latest confirmed swing index (monotonic, never exceeding the
     *         current {@code index}) or {@code -1} if no swing can be confirmed
     *         yet. Implementations should not move backwards once a swing is
     *         confirmed for a given window; use {@link #purgeOnNegativeDetection()}
     *         when a subclass needs to invalidate stale swings.
     */
    protected abstract int detectLatestSwingIndex(int index);

    /**
     * Whether a negative swing detection ({@code -1}) should clear previously
     * confirmed swings. Subclasses that invalidate stale swings (for example, when
     * a plateau grows beyond an equality allowance) can override to return
     * {@code true}. Default is {@code false}, so negative detections simply skip
     * adding a swing.
     *
     * @return {@code true} if negative detections should purge recorded swings
     */
    protected boolean purgeOnNegativeDetection() {
        return false;
    }

    private final class SwingPointTracker {
        private final IntFunction<Integer> swingIndexDetector;
        private final BarSeries series;
        private final List<Integer> swingPointIndexes = new ArrayList<>();
        private int lastScannedIndex = Integer.MIN_VALUE;

        private SwingPointTracker(IntFunction<Integer> swingIndexDetector, BarSeries series) {
            this.swingIndexDetector = Objects.requireNonNull(swingIndexDetector, "swingIndexDetector cannot be null");
            this.series = Objects.requireNonNull(series, "series cannot be null");
        }

        private int getLatestSwingIndex(int index) {
            ensureScanned(index);
            if (swingPointIndexes.isEmpty()) {
                return -1;
            }
            for (int i = swingPointIndexes.size() - 1; i >= 0; i--) {
                final int candidate = swingPointIndexes.get(i);
                if (candidate <= index) {
                    return candidate;
                }
            }
            return -1;
        }

        private List<Integer> getSwingPointIndexes(int index) {
            ensureScanned(index);
            final List<Integer> filtered = new ArrayList<>();
            for (int swingIndex : swingPointIndexes) {
                if (swingIndex <= index) {
                    filtered.add(swingIndex);
                }
            }
            return Collections.unmodifiableList(filtered);
        }

        private void ensureScanned(int index) {
            final int beginIndex = series.getBeginIndex();
            final int endIndex = series.getEndIndex();
            purgeOutOfRange(beginIndex);
            if (index < beginIndex || beginIndex > endIndex) {
                return;
            }
            final int targetIndex = Math.min(index, endIndex);
            if (lastScannedIndex < beginIndex - 1) {
                lastScannedIndex = beginIndex - 1;
            }
            if (targetIndex <= lastScannedIndex) {
                return;
            }
            for (int currentIndex = Math.max(beginIndex,
                    lastScannedIndex + 1); currentIndex <= targetIndex; currentIndex++) {
                final int swingIndex = swingIndexDetector.apply(currentIndex);
                if (swingIndex < 0) {
                    if (purgeOnNegativeDetection()) {
                        swingPointIndexes.clear();
                    }
                    continue;
                }
                final boolean validSwing = swingIndex >= beginIndex && swingIndex <= currentIndex;
                if (!validSwing) {
                    continue;
                }
                while (!swingPointIndexes.isEmpty()
                        && swingPointIndexes.get(swingPointIndexes.size() - 1) > swingIndex) {
                    swingPointIndexes.remove(swingPointIndexes.size() - 1);
                }
                if (swingPointIndexes.isEmpty() || swingIndex > swingPointIndexes.get(swingPointIndexes.size() - 1)) {
                    swingPointIndexes.add(swingIndex);
                }
            }
            lastScannedIndex = targetIndex;
        }

        private void purgeOutOfRange(int beginIndex) {
            if (swingPointIndexes.isEmpty()) {
                return;
            }
            int firstRetained = 0;
            while (firstRetained < swingPointIndexes.size() && swingPointIndexes.get(firstRetained) < beginIndex) {
                firstRetained++;
            }
            if (firstRetained > 0) {
                swingPointIndexes.subList(0, firstRetained).clear();
            }
        }
    }
}
