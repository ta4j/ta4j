/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators;

import org.ta4j.core.Decimal;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;

/**
 * The Stochastic RSI Indicator.
 * 
 * Stoch RSI = (RSI - MinimumRSIn) / (MaximumRSIn - MinimumRSIn)
 */
public class StochasticRSIIndicator extends CachedIndicator<Decimal> {

    private final RSIIndicator rsi;
    private final LowestValueIndicator minRsi;
    private final HighestValueIndicator maxRsi;

    /**
     * Constructor.
     * @param series the series
     * @param timeFrame the time frame
     */
    public StochasticRSIIndicator(TimeSeries series, int timeFrame) {
        this(new ClosePriceIndicator(series), timeFrame);
    }

    /**
     * Constructor.
     * @param indicator the indicator
     * @param timeFrame the time frame
     */
    public StochasticRSIIndicator(Indicator<Decimal> indicator, int timeFrame) {
        this(new RSIIndicator(indicator, timeFrame), timeFrame);
    }

    /**
     * Constructor.
     * @param rsi the rsi indicator
     * @param timeFrame the time frame
     */
    public StochasticRSIIndicator(RSIIndicator rsi, int timeFrame) {
        super(rsi);
        this.rsi = rsi;
        minRsi = new LowestValueIndicator(rsi, timeFrame);
        maxRsi = new HighestValueIndicator(rsi, timeFrame);
    }

    @Override
    protected Decimal calculate(int index) {
        Decimal minRsiValue = minRsi.getValue(index);
        return rsi.getValue(index).minus(minRsiValue)
                .dividedBy(maxRsi.getValue(index).minus(minRsiValue));
    }

}
