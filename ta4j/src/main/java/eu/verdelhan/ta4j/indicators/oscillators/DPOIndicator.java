/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package eu.verdelhan.ta4j.indicators.oscillators;

import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

/**
 * The Detrended Price Oscillator (DPO) indicator.
 * <p>
 * The Detrended Price Oscillator (DPO) is an indicator designed to remove trend
 * from price and make it easier to identify cycles. DPO does not extend to the
 * last date because it is based on a displaced moving average. However,
 * alignment with the most recent is not an issue because DPO is not a momentum
 * oscillator. Instead, DPO is used to identify cycles highs/lows and estimate
 * cycle length.
 * </p>
 * @see http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:detrended_price_osci
 */
public class DPOIndicator extends CachedIndicator<Decimal> {

    private final int timeFrame;
    
    private final int timeShift;
    
    private final Indicator<Decimal> price;
    
    private final SMAIndicator sma;
    
    /**
     * Constructor.
     * @param series the series
     * @param timeFrame the time frame
     */
    public DPOIndicator(TimeSeries series, int timeFrame) {
        this(new ClosePriceIndicator(series), timeFrame);
    }
    
    /**
     * Constructor.
     * @param price the price
     * @param timeFrame the time frame
     */
    public DPOIndicator(Indicator<Decimal> price, int timeFrame) {
        super(price);
        this.timeFrame = timeFrame;
        timeShift = timeFrame / 2 + 1;
        this.price = price;
        sma = new SMAIndicator(price, this.timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        return price.getValue(index).minus(sma.getValue(index + timeShift));
    }
}
