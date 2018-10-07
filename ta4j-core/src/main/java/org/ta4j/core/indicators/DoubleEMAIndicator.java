package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Double exponential moving average indicator.
 * </p/>
 * see https://en.wikipedia.org/wiki/Double_exponential_moving_average
 */
public class DoubleEMAIndicator extends CachedIndicator<Num> {

    private static final long serialVersionUID = 502597792760330884L;

    private final int barCount;
    private final EMAIndicator ema;
    private final EMAIndicator emaEma;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public DoubleEMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.barCount = barCount;
        this.ema = new EMAIndicator(indicator, barCount);
        this.emaEma = new EMAIndicator(ema, barCount);
    }

    @Override
    protected Num calculate(int index) {
        return ema.getValue(index).multipliedBy(numOf(2)).minus(emaEma.getValue(index));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
