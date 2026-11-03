/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import java.text.DecimalFormat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;

/**
 * This class displays transaction and borrowing costs. The legacy borrowing
 * approximation uses entry value times index distance times the fee per index
 * period. It does not infer elapsed calendar time or revalue the borrowed
 * asset. Calendar/mark-based borrowing requires a separate cost model with
 * explicitly qualified historical inputs in the same manager holding-cost slot.
 * Do not interpret this example's coefficient as an annual or calendar rate.
 * Fair-value marks exclude hypothetical liquidation fees; actual transaction
 * fees and accrued borrowing remain separate costs.
 */
public class TradeCost {

    private static final Logger LOG = LogManager.getLogger(TradeCost.class);

    public static void main(String[] args) {

        // Getting the bar series
        BarSeries series = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();
        // Building the short selling trading strategy
        Strategy strategy = buildShortSellingMomentumStrategy(series);

        // Setting the trading cost models
        double feePerTrade = 0.0005;
        double borrowingFeePerIndexPeriod = 0.00001;
        CostModel transactionCostModel = new LinearTransactionCostModel(feePerTrade);
        CostModel borrowingCostModel = new LinearBorrowingCostModel(borrowingFeePerIndexPeriod);

        // Running the strategy
        BarSeriesManager seriesManager = new BarSeriesManager(series, transactionCostModel, borrowingCostModel);
        Trade.TradeType entryTrade = Trade.TradeType.SELL;
        TradingRecord tradingRecord = seriesManager.run(strategy, entryTrade);

        DecimalFormat df = new DecimalFormat("##.##");
        LOG.debug("------------ Borrowing Costs ------------");
        tradingRecord.getPositions()
                .forEach(position -> LOG.debug("Borrowing cost for {} index periods is: {}",
                        df.format(position.getExit().getIndex() - position.getEntry().getIndex()),
                        df.format(position.getHoldingCost().doubleValue())));
        LOG.debug("------------ Transaction Costs ------------");
        tradingRecord.getPositions()
                .forEach(position -> LOG.debug("Transaction cost for selling: {} -- Transaction cost for buying: {}",
                        df.format(position.getEntry().getCost().doubleValue()),
                        df.format(position.getExit().getCost().doubleValue())));
    }

    private static Strategy buildShortSellingMomentumStrategy(BarSeries series) {
        Indicator<Num> closingPrices = new ClosePriceIndicator(series);
        SMAIndicator shortEma = new SMAIndicator(closingPrices, 10);
        SMAIndicator longEma = new SMAIndicator(closingPrices, 50);
        Rule shortOverLongRule = new OverIndicatorRule(shortEma, longEma);
        Rule shortUnderLongRule = new UnderIndicatorRule(shortEma, longEma);

        String strategyName = "Momentum short-selling strategy";
        return new BaseStrategy(strategyName, shortOverLongRule, shortUnderLongRule);
    }
}
