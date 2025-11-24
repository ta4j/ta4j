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
package ta4jexamples.doc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.*;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.*;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.loaders.CsvTradesLoader;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Temporary utility class to generate chart images for README documentation.
 *
 * <p>
 * This class generates charts that match the code examples in the README. Run
 * the main method to generate chart images that can be used in documentation.
 * </p>
 */
public class ReadmeChartGenerator {

    private static final Logger LOG = LogManager.getLogger(ReadmeChartGenerator.class);

    /**
     * Generates the EMA Crossover chart matching the README quick start example.
     *
     * @param outputDir the directory to save the chart image (e.g., "docs/charts"
     *                  or ".")
     * @return the path to the saved chart image, or empty if saving failed
     */
    public static Optional<Path> generateEmaCrossoverChart(String outputDir) {
        LOG.info("Generating EMA Crossover chart for README...");

        // Load historical price data
        BarSeries fullSeries = CsvTradesLoader.loadBitstampSeries();
        LOG.info("Loaded {} bars from Bitstamp series", fullSeries.getBarCount());

        // Use a smaller subset for a cleaner chart (last ~150 bars)
        int totalBars = fullSeries.getBarCount();
        int startIndex = Math.max(0, totalBars - 150);
        BarSeries series = fullSeries.getSubSeries(startIndex, totalBars);
        LOG.info("Using subset: {} bars (indices {} to {})", series.getBarCount(), startIndex, totalBars - 1);

        // Create indicators: calculate moving averages from close prices
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator fastEma = new EMAIndicator(close, 12); // 12-period EMA
        EMAIndicator slowEma = new EMAIndicator(close, 26); // 26-period EMA

        // Define entry rule: buy when fast EMA crosses above slow EMA (golden cross)
        Rule entry = new CrossedUpIndicatorRule(fastEma, slowEma);

        // Define exit rule: sell when price gains 3% OR loses 1.5%
        Rule exit = new StopGainRule(close, 3.0) // take profit at +3%
                .or(new StopLossRule(close, 1.5)); // or cut losses at -1.5%

        // Combine rules into a strategy
        Strategy strategy = new BaseStrategy("EMA Crossover", entry, exit);

        // Run the strategy on historical data
        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record = manager.run(strategy);

        LOG.info("Strategy executed: {} positions", record.getPositionCount());

        // Generate simplified chart - just price, indicators, and signals (no subchart)
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withTitle("EMA Crossover Strategy")
                .withSeries(series) // Price bars (candlesticks)
                .withIndicatorOverlay(fastEma) // Overlay indicators on price chart
                .withIndicatorOverlay(slowEma)
                .withTradingRecordOverlay(record) // Mark entry/exit points with arrows
                .toChart();

        // Save chart image
        Optional<Path> savedPath = chartWorkflow.saveChartImage(chart, series, "ema-crossover-readme", outputDir);

        if (savedPath.isPresent()) {
            LOG.info("Chart saved to: {}", savedPath.get().toAbsolutePath());
        } else {
            LOG.warn("Failed to save ema-crossover-readme.jpg chart image");
        }

        return savedPath;
    }

    /**
     * Generates an RSI Strategy chart with RSI indicator in a subchart.
     * Demonstrates using subcharts for indicators with different scales.
     *
     * @param outputDir the directory to save the chart image
     * @return the path to the saved chart image, or empty if saving failed
     */
    public static Optional<Path> generateRsiStrategyChart(String outputDir) {
        LOG.info("Generating RSI Strategy chart with subchart for README...");

        // Load historical price data
        BarSeries fullSeries = CsvTradesLoader.loadBitstampSeries();
        LOG.info("Loaded {} bars from Bitstamp series", fullSeries.getBarCount());

        // Use a smaller subset for a cleaner chart (last ~150 bars)
        int totalBars = fullSeries.getBarCount();
        int startIndex = Math.max(0, totalBars - 150);
        BarSeries series = fullSeries.getSubSeries(startIndex, totalBars);
        LOG.info("Using subset: {} bars (indices {} to {})", series.getBarCount(), startIndex, totalBars - 1);

        // Create indicators
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(close, 14);

        // Simple RSI strategy: buy when RSI crosses below 30 (oversold), sell when RSI crosses above 70 (overbought)
        Rule entry = new CrossedDownIndicatorRule(rsi, 30);
        Rule exit = new CrossedUpIndicatorRule(rsi, 70);

        // Combine rules into a strategy
        Strategy strategy = new BaseStrategy("RSI Strategy", entry, exit);

        // Run the strategy on historical data
        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record = manager.run(strategy);

        LOG.info("Strategy executed: {} positions", record.getPositionCount());

        // Generate chart with RSI in subchart
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withTitle("RSI Strategy with Subchart")
                .withSeries(series) // Price bars (candlesticks)
                .withTradingRecordOverlay(record) // Mark entry/exit points with arrows
                .withSubChart(rsi) // RSI indicator in separate subchart panel
                .toChart();

        // Save chart image
        Optional<Path> savedPath = chartWorkflow.saveChartImage(chart, series, "rsi-strategy-readme", outputDir);

        if (savedPath.isPresent()) {
            LOG.info("Chart saved to: {}", savedPath.get().toAbsolutePath());
        } else {
            LOG.warn("Failed to save rsi-strategy-readme.jpg chart image");
        }

        return savedPath;
    }

    /**
     * Generates a strategy chart with performance metrics subchart.
     * Demonstrates visualizing performance analysis criteria over time.
     *
     * @param outputDir the directory to save the chart image
     * @return the path to the saved chart image, or empty if saving failed
     */
    public static Optional<Path> generateStrategyPerformanceChart(String outputDir) {
        LOG.info("Generating Strategy Performance chart with metrics subchart for README...");

        // Load historical price data
        BarSeries fullSeries = CsvTradesLoader.loadBitstampSeries();
        LOG.info("Loaded {} bars from Bitstamp series", fullSeries.getBarCount());

        // Use a smaller subset for a cleaner chart (last ~150 bars)
        int totalBars = fullSeries.getBarCount();
        int startIndex = Math.max(0, totalBars - 150);
        BarSeries series = fullSeries.getSubSeries(startIndex, totalBars);
        LOG.info("Using subset: {} bars (indices {} to {})", series.getBarCount(), startIndex, totalBars - 1);

        // Create indicators: multiple moving averages
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator sma20 = new SMAIndicator(close, 20);
        EMAIndicator ema12 = new EMAIndicator(close, 12);

        // Strategy: buy when EMA crosses above SMA, sell when EMA crosses below SMA
        Rule entry = new CrossedUpIndicatorRule(ema12, sma20);
        Rule exit = new CrossedDownIndicatorRule(ema12, sma20);

        // Combine rules into a strategy
        Strategy strategy = new BaseStrategy("EMA/SMA Crossover", entry, exit);

        // Run the strategy on historical data
        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record = manager.run(strategy);

        LOG.info("Strategy executed: {} positions", record.getPositionCount());

        // Generate chart with performance metrics subchart
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withTitle("Strategy Performance Analysis")
                .withSeries(series) // Price bars (candlesticks)
                .withIndicatorOverlay(sma20) // Overlay SMA on price chart
                .withIndicatorOverlay(ema12) // Overlay EMA on price chart
                .withTradingRecordOverlay(record) // Mark entry/exit points with arrows
                .withSubChart(new MaximumDrawdownCriterion(), record) // Performance metric in subchart
                .toChart();

        // Save chart image
        Optional<Path> savedPath = chartWorkflow.saveChartImage(chart, series, "strategy-performance-readme", outputDir);

        if (savedPath.isPresent()) {
            LOG.info("Chart saved to: {}", savedPath.get().toAbsolutePath());
        } else {
            LOG.warn("Failed to save strategy-performance-readme.jpg chart image");
        }

        return savedPath;
    }

    /**
     * Generates an advanced multi-indicator strategy chart with multiple subcharts.
     * Demonstrates full charting capabilities with multiple analysis layers.
     *
     * @param outputDir the directory to save the chart image
     * @return the path to the saved chart image, or empty if saving failed
     */
    public static Optional<Path> generateAdvancedStrategyChart(String outputDir) {
        LOG.info("Generating Advanced Multi-Indicator Strategy chart for README...");

        // Load historical price data
        BarSeries fullSeries = CsvTradesLoader.loadBitstampSeries();
        LOG.info("Loaded {} bars from Bitstamp series", fullSeries.getBarCount());

        // Use a smaller subset for a cleaner chart (last ~150 bars)
        int totalBars = fullSeries.getBarCount();
        int startIndex = Math.max(0, totalBars - 150);
        BarSeries series = fullSeries.getSubSeries(startIndex, totalBars);
        LOG.info("Using subset: {} bars (indices {} to {})", series.getBarCount(), startIndex, totalBars - 1);

        // Create indicators
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator sma50 = new SMAIndicator(close, 50);
        EMAIndicator ema12 = new EMAIndicator(close, 12);
        MACDIndicator macd = new MACDIndicator(close, 12, 26);
        RSIIndicator rsi = new RSIIndicator(close, 14);

        // Strategy: buy when EMA crosses above SMA and RSI > 50, sell when EMA crosses below SMA
        Rule entry = new CrossedUpIndicatorRule(ema12, sma50)
                .and(new OverIndicatorRule(rsi, 50));
        Rule exit = new CrossedDownIndicatorRule(ema12, sma50);

        // Combine rules into a strategy
        Strategy strategy = new BaseStrategy("Advanced Multi-Indicator Strategy", entry, exit);

        // Run the strategy on historical data
        BarSeriesManager manager = new BarSeriesManager(series);
        TradingRecord record = manager.run(strategy);

        LOG.info("Strategy executed: {} positions", record.getPositionCount());

        NetProfitLossCriterion netProfitLossCriterion = new NetProfitLossCriterion();
        netProfitLossCriterion.calculate(series, record);
        LOG.info("Net profit/loss: {}", netProfitLossCriterion.calculate(series, record));

        // Generate advanced chart with multiple subcharts
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        JFreeChart chart = chartWorkflow.builder()
                .withTitle("Advanced Multi-Indicator Strategy")
                .withSeries(series) // Price bars (candlesticks)
                .withIndicatorOverlay(sma50) // Overlay SMA on price chart
                .withIndicatorOverlay(ema12) // Overlay EMA on price chart
                .withTradingRecordOverlay(record) // Mark entry/exit points with arrows
                .withSubChart(macd) // MACD indicator in subchart
                .withSubChart(rsi) // RSI indicator in subchart
                .withSubChart(new NetProfitLossCriterion(), record) // Net profit/loss performance metric
                .toChart();

        // Save chart image
        Optional<Path> savedPath = chartWorkflow.saveChartImage(chart, series, "advanced-strategy-readme", outputDir);

        if (savedPath.isPresent()) {
            LOG.info("Chart saved to: {}", savedPath.get().toAbsolutePath());
        } else {
            LOG.warn("Failed to save advanced-strategy-readme.jpg chart image");
        }

        return savedPath;
    }

    /**
     * Main method to generate all README charts.
     *
     * @param args optional output directory (defaults to "ta4j-examples/docs/img")
     */
    public static void main(String[] args) {
        String outputDir = args.length > 0 ? args[0] : "ta4j-examples/docs/img";

        LOG.info("=== README Chart Generator ===");
        LOG.info("Output directory: {}", outputDir);

        // Generate all charts
        generateEmaCrossoverChart(outputDir);
        generateRsiStrategyChart(outputDir);
        generateStrategyPerformanceChart(outputDir);
        generateAdvancedStrategyChart(outputDir);

        LOG.info("=== Chart generation complete ===");
    }
}
