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

import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Strategy;
import org.ta4j.core.BarSeries;

import ta4jexamples.strategies.MovingMomentumStrategy;
import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.charting.ChartMaker;

/**
 * This class builds a graphical chart showing the buy/sell signals of a
 * strategy.
 */
public class BuyAndSellSignalsToChart {

    public static void main(String[] args) {

        // Getting the bar series
        BarSeries series = CsvTradesLoader.loadBitstampSeries();
        // Building the trading strategy
        Strategy strategy = MovingMomentumStrategy.buildStrategy(series);

        // Running the strategy
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);

        // Displaying the chart using the shared ChartMaker utility
        ChartMaker chartMaker = new ChartMaker();
        String strategyName = strategy.getName() != null ? strategy.getName() : "Moving Momentum Strategy";
        chartMaker.generateAndDisplayTradingRecordChart(series, strategyName, tradingRecord);
    }
}
