/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Linearly Weighted Moving Average (LWMA) indicator.
 *
 * <p>
 * Sequential requests reuse weighted sums when retained history and numeric
 * precision permit; otherwise the window is recalculated.
 * </p>
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/l/linearlyweightedmovingaverage.asp">
 *      https://www.investopedia.com/terms/l/linearlyweightedmovingaverage.asp</a>
 */
public class LWMAIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final transient RollingWeighted rolling = new RollingWeighted();

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator}
     * @param barCount  the time frame
     */
    public LWMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        final var numFactory = getBarSeries().numFactory();
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        int start = (index - barCount) + 1;
        var series = getBarSeries();
        if (barCount > 1 && start >= Math.max(0, series.getBeginIndex())
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
            return rolling.rebuild(indicator, start, index, barCount, true, snapshot.revision(), terminal);
        }
        Num sum = numFactory.zero();
        Num denominator = numFactory.zero();
        int count = 0;

        int startIndex = (index - barCount) + 1;
        for (int i = startIndex; i <= index; i++) {
            count++;
            denominator = denominator.plus(numFactory.numOf(count));
            sum = sum.plus(indicator.getValue(i).multipliedBy(numFactory.numOf(count)));
        }
        return sum.dividedBy(denominator);
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
