/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2019 Ta4j Organization & respective
 * authors (see AUTHORS)
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
package ta4jexamples.analysis;

import org.ta4j.core.*;
import org.ta4j.core.cost.CostModel;
import org.ta4j.core.cost.LinearBorrowingCostModel;
import org.ta4j.core.cost.LinearTransactionCostModel;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;
import ta4jexamples.loaders.CsvTradesLoader;

import java.text.DecimalFormat;

/**
 * This class displays an example of the transaction cost calculation.
 */
public class TradeCost {

    public static void main(String[] args) {

        // Getting the time series
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();
        // Building the short selling trading strategy
        Strategy strategy = buildShortSellingMomentumStrategy(series);

        // Setting the trading cost models
        double feePerTrade = 0.0005;
        double borrowingFee = 0.00001;
        CostModel transactionCostModel = new LinearTransactionCostModel(feePerTrade);
        CostModel borrowingCostModel = new LinearBorrowingCostModel(borrowingFee);

        // Running the strategy
        TimeSeriesManager seriesManager = new TimeSeriesManager(series, transactionCostModel, borrowingCostModel);
        Order.OrderType entryOrder = Order.OrderType.SELL;
        TradingRecord tradingRecord = seriesManager.run(strategy, entryOrder);

        DecimalFormat df = new DecimalFormat("##.##");
        System.out.println("------------ Borrowing Costs ------------");
        tradingRecord.getTrades().forEach(trade -> System.out.println("Borrowing cost for " +
                df.format(trade.getExit().getIndex()-trade.getEntry().getIndex()) + " periods is: " +
                df.format(trade.getHoldingCost().doubleValue())));
        System.out.println("------------ Transaction Costs ------------");
        tradingRecord.getTrades().forEach(trade -> System.out.println("Transaction cost for selling: " +
                df.format(trade.getEntry().getCost().doubleValue()) + " -- Transaction cost for buying: " +
                df.format(trade.getExit().getCost().doubleValue())));
    }

    private static Strategy buildShortSellingMomentumStrategy(TimeSeries series) {
        Indicator<Num> closingPrices = new ClosePriceIndicator(series);
        SMAIndicator shortEma = new SMAIndicator(closingPrices, 10);
        SMAIndicator longEma = new SMAIndicator(closingPrices, 50);
        Rule shortOverLongRule = new OverIndicatorRule(shortEma, longEma);
        Rule shortUnderLongRule = new UnderIndicatorRule(shortEma, longEma);

        String strategyName = "Momentum short-selling strategy";
        return new BaseStrategy(strategyName, shortOverLongRule, shortUnderLongRule);
    }
}
