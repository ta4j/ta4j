/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;
import ta4jexamples.strategies.MovingMomentumStrategy;

/**
 * This class builds a graphical chart showing the cash flow of a strategy.
 *
 * <p>
 * This example demonstrates the use of the dual-axis chart functionality in
 * {@link ChartWorkflow} to display both the close price and cash flow of a
 * trading strategy on the same chart with separate Y-axes.
 * </p>
 */
public class CashFlowToChart {

    public static void main(String[] args) {
        // Getting the bar series
        BarSeries series = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();

        // Building the trading strategy
        Strategy strategy = MovingMomentumStrategy.buildStrategy(series);

        // Running the strategy
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);

        // Getting the cash flow of the resulting positions
        CashFlow cashFlow = new CashFlow(series, tradingRecord);

        // Creating indicators for the dual-axis chart
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // Building and displaying the dual-axis chart using ChartWorkflow
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        chartWorkflow.displayDualAxisChart(series, closePrice, "Price (USD)", cashFlow, "Cash Flow Ratio",
                "Bitstamp BTC", "Ta4j example - Cash flow to chart");
    }
}
