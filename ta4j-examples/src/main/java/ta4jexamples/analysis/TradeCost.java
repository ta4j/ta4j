/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import java.text.DecimalFormat;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import ta4jexamples.loaders.CsvTradesLoader;

/**
 * This class displays an example of the transaction cost calculation.
 */
public class TradeCost {

    public static void main(String[] args) {

        // Getting the bar series
        BarSeries series = CsvTradesLoader.loadBitstampSeries();
        // Building the short selling trading strategy
        Strategy strategy = buildShortSellingMomentumStrategy(series);

        // Setting the trading cost models
        double feePerTrade = 0.0005;
        double borrowingFee = 0.00001;
        CostModel transactionCostModel = new LinearTransactionCostModel(feePerTrade);
        CostModel borrowingCostModel = new LinearBorrowingCostModel(borrowingFee);

        // Running the strategy
        BarSeriesManager seriesManager = new BarSeriesManager(series, transactionCostModel, borrowingCostModel);
        Trade.TradeType entryTrade = Trade.TradeType.SELL;
        TradingRecord tradingRecord = seriesManager.run(strategy, entryTrade);

        DecimalFormat df = new DecimalFormat("##.##");
        System.out.println("------------ Borrowing Costs ------------");
        tradingRecord.getPositions()
                .forEach(position -> System.out.println("Borrowing cost for "
                        + df.format(position.getExit().getIndex() - position.getEntry().getIndex()) + " periods is: "
                        + df.format(position.getHoldingCost().doubleValue())));
        System.out.println("------------ Transaction Costs ------------");
        tradingRecord.getPositions()
                .forEach(position -> System.out.println("Transaction cost for selling: "
                        + df.format(position.getEntry().getCost().doubleValue()) + " -- Transaction cost for buying: "
                        + df.format(position.getExit().getCost().doubleValue())));
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
