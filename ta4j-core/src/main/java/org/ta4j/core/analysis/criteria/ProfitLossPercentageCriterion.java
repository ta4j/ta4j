package org.ta4j.core.analysis.criteria;

import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Profit and loss in percentage criterion.
 * </p>
 * The profit or loss in percentage over the provided {@link Trade trade(s)}.
 * https://www.investopedia.com/ask/answers/how-do-you-calculate-percentage-gain-or-loss-investment/
 */
public class ProfitLossPercentageCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return tradingRecord.getTrades().stream()
                .filter(Trade::isClosed)
                .map(trade -> calculate(series, trade))
                .reduce(series.numOf(0), Num::plus);
    }

    /**
     * Calculates the profit or loss on a trade in percentage.
     *
     * @param series a time series
     * @param trade  a trade
     * @return the profit or loss on a trade
     */
    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        if (trade.isClosed()) {
            Num entryPrice = series.getBar(trade.getEntry().getIndex()).getClosePrice();
            Num exitPrice = series.getBar(trade.getExit().getIndex()).getClosePrice();

            return exitPrice.minus(entryPrice).dividedBy(entryPrice).multipliedBy(series.numOf(100));
        }
        return series.numOf(0);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

}