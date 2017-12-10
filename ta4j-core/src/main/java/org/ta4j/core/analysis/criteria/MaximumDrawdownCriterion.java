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
package org.ta4j.core.analysis.criteria;

import org.ta4j.core.Decimal;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;

/**
 * Maximum drawdown criterion.
 * <p></p>
 * @see <a href="http://en.wikipedia.org/wiki/Drawdown_%28economics%29">http://en.wikipedia.org/wiki/Drawdown_%28economics%29</a>
 */
public class MaximumDrawdownCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, TradingRecord tradingRecord, int beginIndex, int endIndex) {
        CashFlow cashFlow = new CashFlow(series, tradingRecord, beginIndex, endIndex);
        Decimal maximumDrawdown = calculateMaximumDrawdown(series, cashFlow, beginIndex, endIndex);
        return maximumDrawdown.doubleValue();
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        if (trade != null && trade.getEntry() != null && trade.getExit() != null) {
            CashFlow cashFlow = new CashFlow(series, trade);
            Decimal maximumDrawdown = calculateMaximumDrawdown(series, cashFlow);
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
    private Decimal calculateMaximumDrawdown(TimeSeries series, CashFlow cashFlow) {
        return calculateMaximumDrawdown(series, cashFlow, 0, cashFlow.getSize()-1);
    }
    /**
     * Calculates the maximum drawdown from a cash flow over a series.
     * @param series the time series
     * @param cashFlow the cash flow
     * @return the maximum drawdown from a cash flow over a series
     */
    private Decimal calculateMaximumDrawdown(TimeSeries series, CashFlow cashFlow, int beginIndex, int endIndex) {
        Decimal maximumDrawdown = Decimal.ZERO;
        Decimal maxPeak = Decimal.ZERO;
        if (!series.isEmpty()) {
        	// The series is not empty
	        for (int i = beginIndex; i <= endIndex; i++) {
	            Decimal value = cashFlow.getValue(i);
	            if (value.isGreaterThan(maxPeak)) {
	                maxPeak = value;
	            }
	
	            Decimal drawdown = maxPeak.minus(value).dividedBy(maxPeak);
	            if (drawdown.isGreaterThan(maximumDrawdown)) {
	                maximumDrawdown = drawdown;
	            }
	        }
        }
        return maximumDrawdown;
    }
}
