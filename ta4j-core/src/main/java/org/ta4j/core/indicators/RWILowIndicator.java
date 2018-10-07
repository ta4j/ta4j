package org.ta4j.core.indicators;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * The Class RandomWalkIndexLowIndicator.
 *
 * @see <a href="http://https://rtmath.net/helpFinAnalysis/html/934563a8-9171-42d2-8444-486691234b1d.html">Source of formular</a>
 */
public class RWILowIndicator extends CachedIndicator<Num> {

    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the series
     * @param barCount the time frame
     */
    public RWILowIndicator(TimeSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (index - barCount + 1 < getTimeSeries().getBeginIndex()) {
            return NaN.NaN;
        }

        Num minRWIL = numOf(0);
        for (int n = 2; n <= barCount; n++) {
            minRWIL = minRWIL.max(calcRWIHFor(index, n));
        }

        return minRWIL;
    }

    private Num calcRWIHFor(final int index, final int n) {
        TimeSeries series = getTimeSeries();
        Num low = series.getBar(index).getLowPrice();
        Num high_N = series.getBar(index + 1 - n).getHighPrice();
        Num atr_N = new ATRIndicator(series, n).getValue(index);
        Num sqrt_N = numOf(n).sqrt();

        return high_N.minus(low).dividedBy(atr_N.multipliedBy(sqrt_N));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
