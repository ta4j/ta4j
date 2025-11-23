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
import org.ta4j.core.indicators.averages.EMAIndicator;
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
 * This class generates charts that match the code examples in the README.
 * Run the main method to generate chart images that can be used in documentation.
 * </p>
 */
public class ReadmeChartGenerator {

    private static final Logger LOG = LogManager.getLogger(ReadmeChartGenerator.class);
    
    /**
     * Generates the EMA Crossover chart matching the README quick start example.
     * 
     * @param outputDir the directory to save the chart image (e.g., "docs/charts" or ".")
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
        LOG.info("Using subset: {} bars (indices {} to {})", 
                series.getBarCount(), startIndex, totalBars - 1);

        // Create indicators: calculate moving averages from close prices
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        EMAIndicator fastEma = new EMAIndicator(close, 12);  // 12-period EMA
        EMAIndicator slowEma = new EMAIndicator(close, 26);  // 26-period EMA

        // Define entry rule: buy when fast EMA crosses above slow EMA (golden cross)
        Rule entry = new CrossedUpIndicatorRule(fastEma, slowEma);

        // Define exit rule: sell when price gains 3% OR loses 1.5%
        Rule exit = new StopGainRule(close, 3.0)      // take profit at +3%
                .or(new StopLossRule(close, 1.5));    // or cut losses at -1.5%

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
                .withSeries(series)                    // Price bars (candlesticks)
                .withIndicatorOverlay(fastEma)         // Overlay indicators on price chart
                .withIndicatorOverlay(slowEma)
                .withTradingRecordOverlay(record)      // Mark entry/exit points with arrows
                .toChart();

        // Save chart image
        Optional<Path> savedPath = chartWorkflow.saveChartImage(
                chart, series, "ema-crossover-readme", outputDir);
        
        if (savedPath.isPresent()) {
            LOG.info("Chart saved to: {}", savedPath.get().toAbsolutePath());
        } else {
            LOG.warn("Failed to save chart image");
        }
        
        return savedPath;
    }

    /**
     * Main method to generate all README charts.
     * 
     * @param args optional output directory (defaults to "ta4j-examples/docs/img")
     */
    public static void main(String[] args) {
        String outputDir = args.length > 0 ? args[0] : "ta4j/ta4j-examples/docs/img";
        
        LOG.info("=== README Chart Generator ===");
        LOG.info("Output directory: {}", outputDir);
        
        // Generate EMA Crossover chart
        generateEmaCrossoverChart(outputDir);
        
        LOG.info("=== Chart generation complete ===");
    }
}

