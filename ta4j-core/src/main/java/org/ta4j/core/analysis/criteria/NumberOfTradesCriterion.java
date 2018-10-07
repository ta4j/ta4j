package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Number of trades criterion.
 */
public class NumberOfTradesCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return series.numOf(tradingRecord.getTradeCount());
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return series.numOf(1);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }
}
