package org.ta4j.core.indicators.numeric.facades;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;

/**
 * A facade to create the 3 Keltner Channel indicators.
 * An exponential moving average of close price is used as the middle channel.
 *
 * <p>
 * This class creates lightweight "fluent" numeric indicators. These objects are
 * not cached, although they may be wrapped around cached objects. Overall there
 * is less caching and probably better performance.
 */
public class KeltnerChannels {

    private final NumericIndicator middle;
    private final NumericIndicator upper;
    private final NumericIndicator lower;

    public KeltnerChannels(BarSeries bs, int emaCount, int atrCount, Number k) {
        NumericIndicator price = NumericIndicator.of(new ClosePriceIndicator(bs));
        NumericIndicator atr = NumericIndicator.of(new ATRIndicator(bs, atrCount));
        this.middle = price.ema(emaCount);
        this.upper = middle.plus(atr.multipliedBy(k));
        this.lower = middle.minus(atr.multipliedBy(k));
    }

    public NumericIndicator middle() {
        return middle;
    }

    public NumericIndicator upper() {
        return upper;
    }

    public NumericIndicator lower() {
        return lower;
    }

}
