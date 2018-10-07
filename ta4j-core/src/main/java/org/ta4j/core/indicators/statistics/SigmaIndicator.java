package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Sigma-Indicator (also called, "z-score" or "standard score").
 * <p/>
 * see http://www.statisticshowto.com/probability-and-statistics/z-score/
 */
public class SigmaIndicator extends CachedIndicator<Num> {

    private static final long serialVersionUID = 6283425887025798038L;
    
    private Indicator<Num> ref;
    private int barCount;

    private SMAIndicator mean;
    private StandardDeviationIndicator sd;
    
    /**
     * Constructor.
     * @param ref the indicator
     * @param barCount the time frame
     */
    public SigmaIndicator(Indicator<Num> ref, int barCount) {
        super(ref);
        this.ref = ref;
        this.barCount = barCount;
        mean = new SMAIndicator(ref, barCount);
        sd = new StandardDeviationIndicator(ref, barCount);
    }

    @Override
    protected Num calculate(int index) {
        // z-score = (ref - mean) / sd
        return (ref.getValue(index).minus(mean.getValue(index))).dividedBy(sd.getValue(index));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
