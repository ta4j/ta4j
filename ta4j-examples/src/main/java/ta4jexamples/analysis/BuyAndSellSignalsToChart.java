/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.Strategy;
import org.ta4j.core.BarSeries;

import ta4jexamples.strategies.MovingMomentumStrategy;
import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;
import ta4jexamples.charting.workflow.ChartWorkflow;

/**
 * This class builds a graphical chart showing the buy/sell signals of a
 * strategy.
 */
public class BuyAndSellSignalsToChart {

    public static void main(String[] args) {

        // Getting the bar series
        BarSeries series = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();
        // Building the trading strategy
        Strategy strategy = MovingMomentumStrategy.buildStrategy(series);

        // Running the strategy
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);

        // Displaying the chart using the shared ChartWorkflow utility
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        String strategyName = strategy.getName() != null ? strategy.getName() : "Moving Momentum Strategy";
        chartWorkflow.displayTradingRecordChart(series, strategyName, tradingRecord);
    }
}
