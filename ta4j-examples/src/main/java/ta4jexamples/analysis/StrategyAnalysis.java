/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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

import org.ta4j.core.AnalysisCriterion.PositionFilter;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.AverageReturnPerBarCriterion;
import org.ta4j.core.criteria.EnterAndHoldCriterion;
import org.ta4j.core.criteria.LinearTransactionCostCriterion;
import org.ta4j.core.criteria.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.NumberOfBarsCriterion;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.PositionsRatioCriterion;
import org.ta4j.core.criteria.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.VersusEnterAndHoldCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;

import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.num.Num;
import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.MovingMomentumStrategy;

/**
 * This class displays analysis criterion values after running a trading
 * strategy over a bar series.
 */
public class StrategyAnalysis {

    public static void main(String[] args) {

        // Getting the bar series
        var series = CsvTradesLoader.loadBitstampSeries();
        // Building the trading strategy
        var strategy = MovingMomentumStrategy.buildStrategy(series);
        // Running the strategy
        var seriesManager = new BarSeriesManager(series);
        var tradingRecord = seriesManager.run(strategy);

        /*
         * Analysis criteria
         */

        var grossReturn = new GrossReturnCriterion().calculate(series, tradingRecord);
        System.out.println("Gross return: " + grossReturn);

        var netReturnCriterion = new NetReturnCriterion();
        var netReturn = netReturnCriterion.calculate(series, tradingRecord);
        System.out.println("Gross return: " + netReturn);

        var numberOfBars = new NumberOfBarsCriterion().calculate(series, tradingRecord);
        System.out.println("Number of bars: " + numberOfBars);

        var AverageReturnPerBar = new AverageReturnPerBarCriterion().calculate(series, tradingRecord);
        System.out.println("Average return per bar: " + AverageReturnPerBar);

        var numberOfPositions = new NumberOfPositionsCriterion().calculate(series, tradingRecord);
        System.out.println("Number of positions: " + numberOfPositions);

        var positionsRatio = new PositionsRatioCriterion(PositionFilter.PROFIT).calculate(series, tradingRecord);
        System.out.println("Winning positions ratio: " + positionsRatio);

        var maximumDrawdown = new MaximumDrawdownCriterion().calculate(series, tradingRecord);
        System.out.println("Maximum drawdown: " + maximumDrawdown);

        var returnOverMaxDrawdown = new ReturnOverMaxDrawdownCriterion().calculate(series, tradingRecord);
        System.out.println("Return over maximum drawdown: " + returnOverMaxDrawdown);

        var linearTransactionCost = new LinearTransactionCostCriterion(1000, 0.005).calculate(series, tradingRecord);
        System.out.println("Total transaction cost (from $1000): " + linearTransactionCost);

        var enterAndHold = EnterAndHoldCriterion.EnterAndHoldReturnCriterion().calculate(series, tradingRecord);
        System.out.println("Buy-and-hold return: " + enterAndHold);

        var versusEnterAndHold = new VersusEnterAndHoldCriterion(netReturnCriterion).calculate(series, tradingRecord);
        System.out.println("Custom strategy return vs buy-and-hold strategy return: " + versusEnterAndHold);
    }

}
