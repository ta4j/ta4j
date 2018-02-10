/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan, Ta4j Organization & respective authors (see AUTHORS)

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
package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.num.Num;
/**
 * Maximum drawdown criterion.
 * <p></p>
 * @see <a href="http://en.wikipedia.org/wiki/Drawdown_%28economics%29">http://en.wikipedia.org/wiki/Drawdown_%28economics%29</a>
 */
public class MaximumDrawdownCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, TradingRecord tradingRecord) {
        CashFlow cashFlow = new CashFlow(series, tradingRecord);
        Num maximumDrawdown = calculateMaximumDrawdown(series, cashFlow);
        return maximumDrawdown.doubleValue();
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        if (trade != null && trade.getEntry() != null && trade.getExit() != null) {
            CashFlow cashFlow = new CashFlow(series, trade);
            Num maximumDrawdown = calculateMaximumDrawdown(series, cashFlow);
            return maximumDrawdown.doubleValue();
        }
        return 0;
    }

    @Override
    public boolean betterThan(double criterionValue1, double criterionValue2) {
        return criterionValue1 < criterionValue2;
    }

    /**
     * Calculates the maximum drawdown from a cash flow over a series.
     * @param series the time series
     * @param cashFlow the cash flow
     * @return the maximum drawdown from a cash flow over a series
     */
    private Num calculateMaximumDrawdown(TimeSeries series, CashFlow cashFlow) {
        Num maximumDrawdown = series.numOf(0);
        Num maxPeak = series.numOf(0);
        if (!series.isEmpty()) {
        	// The series is not empty
	        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
	            Num value = cashFlow.getValue(i);
	            if (value.isGreaterThan(maxPeak)) {
	                maxPeak = value;
	            }
	
	            Num drawdown = maxPeak.minus(value).dividedBy(maxPeak);
	            if (drawdown.isGreaterThan(maximumDrawdown)) {
	                maximumDrawdown = drawdown;
	            }
	        }
        }
        return maximumDrawdown;
    }
}
