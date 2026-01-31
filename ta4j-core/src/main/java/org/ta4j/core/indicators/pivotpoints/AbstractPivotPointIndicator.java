/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.pivotpoints;

import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Abstract base class for pivot point indicators that calculate pivot points
 * based on time periods.
 * <p>
 * This class provides common functionality for determining which bars belong to
 * the previous period (day, week, month, year) based on the configured
 * {@link TimeLevel}.
 *
 * @since 0.20
 */
public abstract class AbstractPivotPointIndicator extends RecursiveCachedIndicator<Num> {

    /** The time level for pivot calculation. */
    protected final TimeLevel timeLevel;

    /**
     * Constructor.
     *
     * @param series    the bar series with adequate endTime of each bar for the
     *                  given time level
     * @param timeLevel the corresponding {@link TimeLevel} for pivot calculation
     * @since 0.20
     */
    protected AbstractPivotPointIndicator(BarSeries series, TimeLevel timeLevel) {
        super(series);
        this.timeLevel = timeLevel;
    }

    @Override
    protected Num calculate(int index) {
        return calcPivotPoint(getBarsOfPreviousPeriod(index));
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

    /**
     * Calculates the pivot point value from the bars of the previous period.
     *
     * @param barsOfPreviousPeriod list of bar indices from the previous period
     * @return the calculated pivot point value, or NaN if the list is empty
     * @since 0.20
     */
    protected abstract Num calcPivotPoint(List<Integer> barsOfPreviousPeriod);

    /**
     * Calculates the indices of the bars of the previous period.
     *
     * @param index index of the current bar
     * @return list of indices of the bars of the previous period
     * @since 0.20
     */
    public List<Integer> getBarsOfPreviousPeriod(int index) {
        List<Integer> previousBars = new ArrayList<>();

        if (timeLevel == TimeLevel.BARBASED) {
            previousBars.add(Math.max(0, index - 1));
            return previousBars;
        }
        if (index <= getBarSeries().getBeginIndex()) {
            return previousBars;
        }

        final Bar currentBar = getBarSeries().getBar(index);

        // step back while bar-1 in same period (day, week, etc.), including beginIndex
        // so the first bar of the series can anchor the current period boundary
        while (index - 1 >= getBarSeries().getBeginIndex()
                && getPeriod(getBarSeries().getBar(index - 1)) == getPeriod(currentBar)) {
            index--;
        }

        // index = last bar in same period, index-1 = first bar in previous period
        long previousPeriod = getPreviousPeriod(currentBar, index - 1);
        // Include all bars from the previous period, including the bar at beginIndex if
        // it belongs to that period
        while (index - 1 >= getBarSeries().getBeginIndex()
                && getPeriod(getBarSeries().getBar(index - 1)) == previousPeriod) { // while bar-n in previous period
            index--;
            previousBars.add(index);
        }
        return previousBars;
    }

    /**
     * Gets the period identifier for the given bar based on the configured time
     * level.
     *
     * @param bar the bar to get the period for
     * @return the period identifier (day of year, week number, month, or year)
     * @since 0.20
     */
    protected long getPeriod(Bar bar) {
        var zonedEndTime = bar.getZonedEndTime();
        switch (timeLevel) {
        case DAY:
            return zonedEndTime.getDayOfYear();
        case WEEK:
            return zonedEndTime.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        case MONTH:
            return zonedEndTime.getMonthValue();
        default: // YEAR
            return zonedEndTime.getYear();
        }
    }

    /**
     * Gets the previous period identifier for the given bar based on the configured
     * time level.
     *
     * @param bar                the current bar
     * @param indexOfPreviousBar the index of the previous bar to check
     * @return the previous period identifier
     * @since 0.20
     */
    protected long getPreviousPeriod(Bar bar, int indexOfPreviousBar) {
        var zonedEndTime = bar.getZonedEndTime();
        switch (timeLevel) {
        case DAY: // return previous day
            int prevCalendarDay = zonedEndTime.minusDays(1).getDayOfYear();
            // skip weekend and holidays:
            while (indexOfPreviousBar >= getBarSeries().getBeginIndex() && prevCalendarDay > 0) {
                var previousZonedEndTime = getBarSeries().getBar(indexOfPreviousBar).getZonedEndTime();
                if (previousZonedEndTime.getDayOfYear() == prevCalendarDay) {
                    break;
                }
                prevCalendarDay--;
            }
            return prevCalendarDay;
        case WEEK: // return previous week
            return zonedEndTime.minusWeeks(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        case MONTH: // return previous month
            return zonedEndTime.minusMonths(1).getMonthValue();
        default: // return previous year
            return zonedEndTime.minusYears(1).getYear();
        }
    }
}
