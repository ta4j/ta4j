package org.ta4j.core.indicators.keltner;

import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Keltner Channel (lower line) indicator
 * @see <a href="http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:keltner_channels</a>
 */
public class KeltnerChannelLowerIndicator extends CachedIndicator<Num> {

    private final ATRIndicator averageTrueRangeIndicator;

    private final KeltnerChannelMiddleIndicator keltnerMiddleIndicator;

    private final Num ratio;

    public KeltnerChannelLowerIndicator(KeltnerChannelMiddleIndicator keltnerMiddleIndicator, double ratio, int barCountATR) {
        super(keltnerMiddleIndicator);
        this.ratio = numOf(ratio);
        this.keltnerMiddleIndicator = keltnerMiddleIndicator;
        averageTrueRangeIndicator = new ATRIndicator(keltnerMiddleIndicator.getTimeSeries(), barCountATR);
    }

    @Override
    protected Num calculate(int index) {
        return keltnerMiddleIndicator.getValue(index).minus(ratio.multipliedBy(averageTrueRangeIndicator.getValue(index)));
    }

}
