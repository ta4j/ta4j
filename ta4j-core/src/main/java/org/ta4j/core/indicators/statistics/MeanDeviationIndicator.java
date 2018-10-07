package org.ta4j.core.indicators.statistics;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Mean deviation indicator.
 * <p/>
 * @see <a href="http://en.wikipedia.org/wiki/Mean_absolute_deviation#Average_absolute_deviation">
 *     http://en.wikipedia.org/wiki/Mean_absolute_deviation#Average_absolute_deviation</a>
 */
public class MeanDeviationIndicator extends CachedIndicator<Num> {

    private Indicator<Num> indicator;

    private int barCount;

    private SMAIndicator sma;
    
    /**
     * Constructor.
     * @param indicator the indicator
     * @param barCount the time frame
     */
    public MeanDeviationIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        sma = new SMAIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        Num absoluteDeviations = numOf(0);

        final Num average = sma.getValue(index);
        final int startIndex = Math.max(0, index - barCount + 1);
        final int nbValues = index - startIndex + 1;

        for (int i = startIndex; i <= index; i++) {
            // For each period...
            absoluteDeviations = absoluteDeviations.plus(indicator.getValue(i).minus(average).abs());
        }
        return absoluteDeviations.dividedBy(numOf(nbValues));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
