package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Average profitable trades criterion.
 * </p>
 * The number of profitable trades.
 */
public class AverageProfitableTradesCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        Num result = calculateResult(series, trade);
        return (result.isGreaterThan(series.numOf(1))) ? series.numOf(1) : series.numOf(0);
    }

    private Num calculateResult(TimeSeries series, Trade trade) {
        int entryIndex = trade.getEntry().getIndex();
        int exitIndex = trade.getExit().getIndex();
        if (trade.getEntry().isBuy()) {
            // buy-then-sell trade
            return series.getBar(exitIndex).getClosePrice().dividedBy(series.getBar(entryIndex).getClosePrice());
        } else {
            // sell-then-buy trade
            return series.getBar(entryIndex).getClosePrice().dividedBy(series.getBar(exitIndex).getClosePrice());
        }
    }

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        int numberOfProfitable = 0;
        for (Trade trade : tradingRecord.getTrades()) {
            Num result = calculateResult(series, trade);
            if (result.isGreaterThan(series.numOf(1))) {
                numberOfProfitable++;
            }
        }
        return series.numOf(numberOfProfitable).dividedBy(series.numOf(tradingRecord.getTradeCount()));
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
