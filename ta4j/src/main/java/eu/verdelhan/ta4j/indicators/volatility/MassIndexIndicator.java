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
package eu.verdelhan.ta4j.indicators.volatility;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;
import eu.verdelhan.ta4j.indicators.simple.DifferenceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MaxPriceIndicator;
import eu.verdelhan.ta4j.indicators.simple.MinPriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.EMAIndicator;

/**
 * Mass index indicator.
 * <p>
 * @see http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:mass_index
 */
public class MassIndexIndicator extends CachedIndicator<Decimal> {

    private EMAIndicator singleEma;
    
    private EMAIndicator doubleEma;
    
    private int timeFrame;

    /**
     * Constructor.
     * @param series the time series
     * @param emaTimeFrame the time frame for EMAs (usually 9)
     * @param timeFrame the time frame
     */
    public MassIndexIndicator(TimeSeries series, int emaTimeFrame, int timeFrame) {
        super(series);
        Indicator<Decimal> highLowDifferential = new DifferenceIndicator(
                new MaxPriceIndicator(series),
                new MinPriceIndicator(series)
        );
        singleEma = new EMAIndicator(highLowDifferential, emaTimeFrame);
        doubleEma = new EMAIndicator(singleEma, emaTimeFrame); // Not the same formula as DoubleEMAIndicator
        this.timeFrame = timeFrame;
    }

    @Override
    protected Decimal calculate(int index) {
        final int startIndex = Math.max(0, index - timeFrame + 1);
        Decimal massIndex = Decimal.ZERO;
        for (int i = startIndex; i <= index; i++) {
            Decimal emaRatio = singleEma.getValue(i).dividedBy(doubleEma.getValue(i));
            massIndex = massIndex.plus(emaRatio);
        }
        return massIndex;
    }
}
