package org.ta4j.core.analysis.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Gross loss criterion in percentage.
 *
 * The gross profit of the provided {@link Trade trade(s)} over the provided
 * {@link BarSeries series}.
 */
public class GrossLossPercentageCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream().filter(Trade::isClosed).map(trade -> calculate(series, trade))
                .reduce(series.numOf(0), Num::plus);
    }

    /**
     * Calculates the gross loss percentage of given trade
     *
     * @param series a bar series
     * @param trade  a trade
     * @return the loss of the profit
     */
    @Override
    public Num calculate(BarSeries series, Trade trade) {
        if (trade.isClosed()) {
            Num entryPrice = trade.getEntry().getPricePerAsset();
            Num loss = trade.getGrossProfit().dividedBy(entryPrice).multipliedBy(series.numOf(100));
            return loss.isNegative() ? loss : series.numOf(0);
        }
        return series.numOf(0);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}