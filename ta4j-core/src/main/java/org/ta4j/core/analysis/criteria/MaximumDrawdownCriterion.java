package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.num.Num;
/**
 * Maximum drawdown criterion.
 * </p>
 * @see <a href="http://en.wikipedia.org/wiki/Drawdown_%28economics%29">http://en.wikipedia.org/wiki/Drawdown_%28economics%29</a>
 */
public class MaximumDrawdownCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        CashFlow cashFlow = new CashFlow(series, tradingRecord);
        return calculateMaximumDrawdown(series, cashFlow);
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        if (trade != null && trade.getEntry() != null && trade.getExit() != null) {
            CashFlow cashFlow = new CashFlow(series, trade);
            return calculateMaximumDrawdown(series, cashFlow);
        }
        return series.numOf(0);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
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
