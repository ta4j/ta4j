/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.pivotpoints;

import static org.ta4j.core.num.NaN.NaN;

import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * DeMark Pivot Point indicator.
 *
 * <p>
 * The {@link java.time.Instant UTC} represents a point in time on the
 * time-line, typically measured in milliseconds. It is independent of time
 * zones, days of the week, or months. However, this rule converts a UTC to a
 * ZonedDateTime in UTC to get the day, week and month in that time zone.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-overlays/pivot-points">
 *      https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-overlays/pivot-points</a>
 */
public class DeMarkPivotPointIndicator extends AbstractPivotPointIndicator {

    private final Num two;
    private final Num four;

    /**
     * Constructor.
     *
     * Calculates the deMark pivot point based on the time level parameter.
     *
     * @param series      the bar series with adequate endTime of each bar for the
     *                    given time level.
     * @param timeLevelId the corresponding time level for pivot calculation:
     *                    <ul>
     *                    <li>1-, 5-, 10- and 15-minute charts use the prior days
     *                    high, low and close: <b>timeLevelId</b> =
     *                    PIVOT_TIME_LEVEL_ID_DAY (= 1)</li>
     *                    <li>30- 60- and 120-minute charts use the prior week's
     *                    high, low, and close: <b>timeLevelId</b> =
     *                    PIVOT_TIME_LEVEL_ID_WEEK (= 2)</li>
     *                    <li>Pivot Points for daily charts use the prior month's
     *                    high, low and close: <b>timeLevelId</b> =
     *                    PIVOT_TIME_LEVEL_ID_MONTH (= 3)</li>
     *                    <li>Pivot Points for weekly and monthly charts use the
     *                    prior year's high, low and close: <b>timeLevelId</b> =
     *                    PIVOT_TIME_LEVEL_ID_YEAR (= 4)</li>
     *                    <li>If you want to use just the last bar data:
     *                    <b>timeLevelId</b> = PIVOT_TIME_LEVEL_ID_BARBASED (=
     *                    0)</li>
     *                    </ul>
     *                    The user has to make sure that there are enough previous
     *                    bars to calculate correct pivots at the first bar that
     *                    matters. For example for PIVOT_TIME_LEVEL_ID_MONTH there
     *                    will be only correct pivot point values (and reversals)
     *                    after the first complete month
     */
    public DeMarkPivotPointIndicator(BarSeries series, TimeLevel timeLevelId) {
        super(series, timeLevelId);
        this.two = getBarSeries().numFactory().two();
        this.four = getBarSeries().numFactory().numOf(4);
    }

    @Override
    protected Num calcPivotPoint(List<Integer> barsOfPreviousPeriod) {
        if (barsOfPreviousPeriod.isEmpty())
            return NaN;
        Bar bar = getBarSeries().getBar(barsOfPreviousPeriod.get(0));
        Num open = getBarSeries().getBar(barsOfPreviousPeriod.get(barsOfPreviousPeriod.size() - 1)).getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        for (int i : barsOfPreviousPeriod) {
            Bar iBar = getBarSeries().getBar(i);
            high = iBar.getHighPrice().max(high);
            low = iBar.getLowPrice().min(low);
        }

        Num x;
        if (close.isLessThan(open)) {
            x = high.plus(two.multipliedBy(low)).plus(close);
        } else if (close.isGreaterThan(open)) {
            x = two.multipliedBy(high).plus(low).plus(close);
        } else {
            x = high.plus(low).plus(two.multipliedBy(close));
        }

        return x.dividedBy(four);
    }

}
