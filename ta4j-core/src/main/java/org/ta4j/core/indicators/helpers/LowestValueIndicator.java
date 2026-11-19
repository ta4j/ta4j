/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Lowest value indicator.
 *
 * <p>
 * Returns the lowest indicator value from the bar series within the bar count.
 * Sequential requests reuse an indexed monotonic window; changes or access
 * patterns that invalidate its state are handled by rebuilding the window.
 */
public class LowestValueIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final transient RollingExtrema rolling = new RollingExtrema(false);

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public LowestValueIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    public Num calculate(int index) {
        BarSeries series = indicator.getBarSeries();
        var snapshot = synchronizeCacheWithSeries(series);
        int beginIndex = Math.max(0, series.getBeginIndex());
        Num current = indicator.getValue(index);
        if (barCount <= 1) {
            return current;
        }
        int start = Math.max(beginIndex, index - barCount + 1);
        boolean terminal = index == snapshot.endIndex();
        if (terminal && index > beginIndex) {
            getValue(index - 1);
        }
        int checkpoint = rolling.index();
        long revision = rolling.revision();
        var changes = series.getBarSeriesChangeSnapshot(revision);
        Num previous = terminal && checkpoint == index - 2 ? indicator.getValue(index - 1) : null;
        Num result = rolling.advance(checkpoint, revision, changes, checkpoint >= 0 && isCached(checkpoint), index,
                start, current, previous, terminal);
        return result != null ? result
                : rolling.rebuild(indicator, start, index, current, snapshot.revision(), terminal);
    }

    /** @return {@link #barCount} */
    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
