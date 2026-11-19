/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * WMA indicator.
 *
 * <p>
 * Sequential requests reuse weighted sums when retained history and numeric
 * precision permit; otherwise the window is recalculated.
 * </p>
 */
public class WMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final Indicator<Num> indicator;
    private final transient RollingWeighted rolling = new RollingWeighted();

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public WMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        if (index == 0) {
            return indicator.getValue(0);
        }

        final var numFactory = getBarSeries().numFactory();
        int start = index - barCount + 1;
        var series = getBarSeries();
        if (barCount > 1 && barCount <= 46340 && start >= Math.max(0, series.getBeginIndex())
                && RollingWeighted.supports(numFactory.zero())) {
            var snapshot = synchronizeCacheWithSeries(series);
            boolean terminal = index == snapshot.endIndex();
            if (terminal && index > start) {
                getValue(index - 1);
            }
            int checkpoint = rolling.index();
            long revision = rolling.revision();
            if (checkpoint == index - 1 && index - barCount >= series.getBeginIndex() && isCached(checkpoint)) {
                var changes = series.getBarSeriesChangeSnapshot(revision);
                Num result = rolling.advance(checkpoint, revision, changes, true, index, barCount,
                        indicator.getValue(index), indicator.getValue(index - barCount), numFactory.numOf(barCount),
                        terminal);
                if (result != null) {
                    return result;
                }
            }
            return rolling.rebuild(indicator, start, index, barCount, false, snapshot.revision(), terminal);
        }
        Num value = numFactory.zero();
        int loopLength = (index - barCount < 0) ? index + 1 : barCount;
        int actualIndex = index;
        for (int i = loopLength; i > 0; i--) {
            value = value.plus(numFactory.numOf(i).multipliedBy(indicator.getValue(actualIndex)));
            actualIndex--;
        }

        return value.dividedBy(numFactory.numOf((loopLength * (loopLength + 1)) / 2));
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + barCount - 1;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
