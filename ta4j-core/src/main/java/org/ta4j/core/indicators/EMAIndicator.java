package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Exponential moving average indicator.
 * <p/>
 */
public class EMAIndicator extends AbstractEMAIndicator {

    private static final long serialVersionUID = -3739171856534680816L;

    /**
     * Constructor.
     *
     * @param indicator an indicator
     * @param barCount  the EMA time frame
     */
    public EMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator, barCount, (2.0 / (barCount + 1)));
    }
}
