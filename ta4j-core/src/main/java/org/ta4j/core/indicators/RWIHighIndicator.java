package org.ta4j.core.indicators;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * The RandomWalkIndexHighIndicator.
 *
 * @see <a href="http://https://rtmath.net/helpFinAnalysis/html/934563a8-9171-42d2-8444-486691234b1d.html">Source of formular</a>
 */
public class RWIHighIndicator extends CachedIndicator<Num> {

    private final int barCount;

    /**
     * Constructor.
     *
     * @param series   the series
     * @param barCount the time frame
     */
    public RWIHighIndicator(TimeSeries series, int barCount) {
        super(series);
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        if (index - barCount + 1 < getTimeSeries().getBeginIndex()) {
            return NaN.NaN;
        }

        Num maxRWIH = numOf(0);
        for (int n = 2; n <= barCount; n++) {
            maxRWIH = maxRWIH.max(calcRWIHFor(index, n));
        }

        return maxRWIH;
    }

    private Num calcRWIHFor(final int index, final int n) {
        TimeSeries series = getTimeSeries();
        Num high = series.getBar(index).getHighPrice();
        Num low_N = series.getBar(index + 1 - n).getLowPrice();
        Num atr_N = new ATRIndicator(series, n).getValue(index);
        Num sqrt_N = numOf(n).sqrt();

        return high.minus(low_N).dividedBy(atr_N.multipliedBy(sqrt_N));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }

}
