package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * REX indicator with SMA
 *
 * @see <a href=
 *      "https://www.tradingview.com/script/m08Wp5xr-REX-by-vitelot/">https://www.tradingview.com/script/m08Wp5xr-REX-by-vitelot/</a>
 */
public class REXIndicator extends CachedIndicator<Num> {

    private final SMAIndicator rexIndicator;
    private final SMAIndicator signalIndicator;

    public REXIndicator(BarSeries series, int rexPeriod, int signalPeriod) {
        super(series);
        this.rexIndicator = new SMAIndicator(new TVBIndicator(series), rexPeriod);
        this.signalIndicator = new SMAIndicator(rexIndicator, signalPeriod);
    }

    public SMAIndicator getRexIndicator() {
        return rexIndicator;
    }

    public SMAIndicator getSignalIndicator() {
        return signalIndicator;
    }

    @Override
    protected Num calculate(int index) {
        return rexIndicator.calculate(index);
    }
}
