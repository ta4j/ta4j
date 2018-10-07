package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * Ulcer index indicator.
 * <p/>
 *
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ulcer_index">
 * http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ulcer_index</a>
 * @see <a href="https://en.wikipedia.org/wiki/Ulcer_index">https://en.wikipedia.org/wiki/Ulcer_index</a>
 */
public class UlcerIndexIndicator extends CachedIndicator<Num> {

    private Indicator<Num> indicator;

    private HighestValueIndicator highestValueInd;

    private int barCount;

    /**
     * Constructor.
     *
     * @param indicator the indicator
     * @param barCount  the time frame
     */
    public UlcerIndexIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        highestValueInd = new HighestValueIndicator(indicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        final int numberOfObservations = index - startIndex + 1;
        Num squaredAverage = numOf(0);
        for (int i = startIndex; i <= index; i++) {
            Num currentValue = indicator.getValue(i);
            Num highestValue = highestValueInd.getValue(i);
            Num percentageDrawdown = currentValue.minus(highestValue).dividedBy(highestValue).multipliedBy(numOf(100));
            squaredAverage = squaredAverage.plus(percentageDrawdown.pow(2));
        }
        squaredAverage = squaredAverage.dividedBy(numOf(numberOfObservations));
        return squaredAverage.sqrt();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
