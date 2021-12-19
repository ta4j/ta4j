package org.ta4j.core.indicators.numeric.facades;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * A facade to create the MACD indicator, signal and histogram.
 *
 * <p>
 * This class creates lightweight "fluent" numeric indicators. These objects are
 * not cached, although they are wrapped around cached EMA objects. 
 */
public class MACD {

    private final NumericIndicator line;

    public MACD(BarSeries bs, int n1, int n2) {
        Indicator<Num> price = new ClosePriceIndicator(bs);
        NumericIndicator ema1 = NumericIndicator.of(new EMAIndicator(price, n1));
        NumericIndicator ema2 = NumericIndicator.of(new EMAIndicator(price, n2));
        this.line = ema1.minus(ema2);
    }

    public NumericIndicator line() {
        return line;
    }

    /**
     * Creates an exponential moving average to act as a signal line for this MACD.
     * 
     * @param n the number of periods used to average MACD
     * @return a NumericIndicator wrapped around cached EMA indicator
     */
    public NumericIndicator signal(int n) {
        return NumericIndicator.of(new EMAIndicator(line, n));
    }

    /**
     * Creates an object to calculate the MACD histogram.
     * 
     * @param n the number of periods used for the signal line
     * 
     * @return an object to calculate the difference between this MACD and its signal
     */
    public NumericIndicator histogram(int n) {
        return line.minus(signal(n));
    }
}
