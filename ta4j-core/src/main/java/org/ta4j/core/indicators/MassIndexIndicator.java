package org.ta4j.core.indicators;

import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Mass index indicator.
 * </p>
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:mass_index">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:mass_index</a>
 */
public class MassIndexIndicator extends CachedIndicator<Num> {

    private final EMAIndicator singleEma;
    private final EMAIndicator doubleEma;
    private final int barCount;

    /**
     * Constructor.
     * @param series the time series
     * @param emaBarCount the time frame for EMAs (usually 9)
     * @param barCount the time frame
     */
    public MassIndexIndicator(TimeSeries series, int emaBarCount, int barCount) {
        super(series);
        Indicator<Num> highLowDifferential = new DifferenceIndicator(
                new HighPriceIndicator(series),
                new LowPriceIndicator(series)
        );
        singleEma = new EMAIndicator(highLowDifferential, emaBarCount);
        doubleEma = new EMAIndicator(singleEma, emaBarCount); // Not the same formula as DoubleEMAIndicator
        this.barCount = barCount;
    }

    @Override
    protected Num calculate(int index) {
        final int startIndex = Math.max(0, index - barCount + 1);
        Num massIndex = numOf(0);
        for (int i = startIndex; i <= index; i++) {
            Num emaRatio = singleEma.getValue(i).dividedBy(doubleEma.getValue(i));
            massIndex = massIndex.plus(emaRatio);
        }
        return massIndex;
    }
}
