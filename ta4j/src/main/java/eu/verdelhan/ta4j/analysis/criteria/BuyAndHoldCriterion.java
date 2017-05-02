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
package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.TradingRecord;

/**
 * Buy and hold criterion.
 * <p>
 * @see <a href="http://en.wikipedia.org/wiki/Buy_and_hold">http://en.wikipedia.org/wiki/Buy_and_hold</a>
 */
public class BuyAndHoldCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, TradingRecord tradingRecord) {
        return series.getTick(series.getEnd()).getClosePrice().dividedBy(series.getTick(series.getBegin()).getClosePrice()).toDouble();
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        int entryIndex = trade.getEntry().getIndex();
        int exitIndex = trade.getExit().getIndex();

        if (trade.getEntry().isBuy()) {
            return series.getTick(exitIndex).getClosePrice().dividedBy(series.getTick(entryIndex).getClosePrice()).toDouble();
        } else {
            return series.getTick(entryIndex).getClosePrice().dividedBy(series.getTick(exitIndex).getClosePrice()).toDouble();
        }
    }

    @Override
    public boolean betterThan(double criterionValue1, double criterionValue2) {
        return criterionValue1 > criterionValue2;
    }
}
