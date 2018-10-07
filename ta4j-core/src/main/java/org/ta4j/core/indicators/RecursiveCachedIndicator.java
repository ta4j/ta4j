package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;

/**
 * Recursive cached {@link Indicator indicator}.
 * </p>
 * Recursive indicators should extend this class.<br>
 * This class is only here to avoid (OK, to postpone) the StackOverflowError that
 * may be thrown on the first getValue(int) call of a recursive indicator.
 * Concretely when an index value is asked, if the last cached value is too old/far,
 * the computation of all the values between the last cached and the asked one
 * is executed iteratively.
 */
public abstract class RecursiveCachedIndicator<T> extends CachedIndicator<T> {

    /**
     * The recursion threshold for which an iterative calculation is executed.
     * TODO Should be variable (depending on the sub-indicators used in this indicator)
     */
    private static final int RECURSION_THRESHOLD = 100;

    /**
     * Constructor.
     * @param series the related time series
     */
    public RecursiveCachedIndicator(TimeSeries series) {
        super(series);
    }

    /**
     * Constructor.
     * @param indicator a related indicator (with a time series)
     */
    public RecursiveCachedIndicator(Indicator indicator) {
        this(indicator.getTimeSeries());
    }

    @Override
    public T getValue(int index) {
        TimeSeries series = getTimeSeries();
        if (series != null) {
            final int seriesEndIndex = series.getEndIndex();
            if (index <= seriesEndIndex) {
                // We are not after the end of the series
                final int removedBarsCount = series.getRemovedBarsCount();
                int startIndex = Math.max(removedBarsCount, highestResultIndex);
                if (index - startIndex > RECURSION_THRESHOLD) {
                    // Too many uncalculated values; the risk for a StackOverflowError becomes high.
                    // Calculating the previous values iteratively
                    for (int prevIdx = startIndex; prevIdx < index; prevIdx++) {
                        super.getValue(prevIdx);
                    }
                }
            }
        }

        return super.getValue(index);
    }
}
