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

import java.awt.Color;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecentFractalSwingHighIndicator;
import org.ta4j.core.indicators.RecentFractalSwingLowIndicator;
import org.ta4j.core.indicators.SwingPointMarkerIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingHighIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingLowIndicator;
import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.loaders.CsvBarsLoader;

/**
 * This class demonstrates swing point identification and visualization using
 * ChartWorkflow.
 * <p>
 * The example loads OHLC data from a CSV or JSON file, creates support and
 * resistance trendline indicators to identify swing points, and displays them
 * as markers on a candlestick chart. Swing highs are shown in red, and swing
 * lows are shown in green.
 */
public class SwingPointAnalysis {

    private static final Logger LOG = LogManager.getLogger(SwingPointAnalysis.class);

    /**
     * Main method to run the swing point analysis example.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        // Load bar series from CSV file
        BarSeries series = CsvBarsLoader.loadSeriesFromFile();

        Objects.requireNonNull(series, "Bar series was null");
        if (series.isEmpty()) {
            LOG.error("Bar series is empty. Cannot identify swing points.");
            return;
        }

        // Create marker indicators for swing points using the official indicator
        // Use fractal-based swing indicators with 5-bar symmetric window
        LowPriceIndicator lowPrice = new LowPriceIndicator(series);
        HighPriceIndicator highPrice = new HighPriceIndicator(series);
        RecentFractalSwingLowIndicator swingLowIndicator = new RecentFractalSwingLowIndicator(lowPrice, 5, 5, 0);
        RecentFractalSwingHighIndicator swingHighIndicator = new RecentFractalSwingHighIndicator(highPrice, 5, 5, 0);

        SwingPointMarkerIndicator swingLowMarkers = new SwingPointMarkerIndicator(series, swingLowIndicator);
        SwingPointMarkerIndicator swingHighMarkers = new SwingPointMarkerIndicator(series, swingHighIndicator);

        // Build and display chart using ChartWorkflow
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        ChartPlan plan = chartWorkflow.builder()
                .withTitle("Fractal Swing Point Analysis")
                .withSeries(series)
                .withIndicatorOverlay(swingLowMarkers)
                .withLineColor(Color.GREEN)
                .withLineWidth(3.0f)
                .withConnectAcrossNaN(true)
                .withIndicatorOverlay(swingHighMarkers)
                .withLineColor(Color.RED)
                .withLineWidth(3.0f)
                .withConnectAcrossNaN(true)
                .toPlan();

        chartWorkflow.display(plan);
        chartWorkflow.save(plan, "temp/charts", "fractal-swing-point-analysis");

        LOG.info("Identified {} fractal swing lows and {} fractal swing highs",
                swingLowMarkers.getSwingPointIndexes().size(), swingHighMarkers.getSwingPointIndexes().size());
        swingLowMarkers.getSwingPointIndexes()
                .stream()
                .forEach(index -> LOG.debug("Fractal swing low {} at index {}", lowPrice.getValue(index), index));
        swingHighMarkers.getSwingPointIndexes()
                .stream()
                .forEach(index -> LOG.debug("Fractal swing high {} at index {}", highPrice.getValue(index), index));

        RecentZigZagSwingLowIndicator zigzagLowIndicator = new RecentZigZagSwingLowIndicator(series);
        RecentZigZagSwingHighIndicator zigzagHighIndicator = new RecentZigZagSwingHighIndicator(series);

        SwingPointMarkerIndicator zigzagLowMarkers = new SwingPointMarkerIndicator(series, zigzagLowIndicator);
        SwingPointMarkerIndicator zigzagHighMarkers = new SwingPointMarkerIndicator(series, zigzagHighIndicator);

        // Build and display chart using ChartWorkflow
        ChartWorkflow zigzagChartWorkflow = new ChartWorkflow();
        ChartPlan zigzagPlan = zigzagChartWorkflow.builder()
                .withTitle("ZigZag Swing Point Analysis")
                .withSeries(series)
                .withIndicatorOverlay(zigzagLowMarkers)
                .withLineColor(Color.GREEN)
                .withLineWidth(3.0f)
                .withOpacity(0.25f)
                .withConnectAcrossNaN(true)
                .withIndicatorOverlay(zigzagHighMarkers)
                .withLineColor(Color.RED)
                .withLineWidth(3.0f)
                .withConnectAcrossNaN(false)
                .toPlan();

        zigzagChartWorkflow.display(zigzagPlan);
        zigzagChartWorkflow.save(zigzagPlan, "temp/charts", "zigzag-swing-point-analysis");

        LOG.info("Identified {} zigzag swing lows and {} zigzag swing highs",
                zigzagLowMarkers.getSwingPointIndexes().size(), zigzagHighMarkers.getSwingPointIndexes().size());
        zigzagLowMarkers.getSwingPointIndexes()
                .stream()
                .forEach(index -> LOG.debug("ZigZag swing low {} at index {}", lowPrice.getValue(index), index));
        zigzagHighMarkers.getSwingPointIndexes()
                .stream()
                .forEach(index -> LOG.debug("ZigZag swing high {} at index {}", highPrice.getValue(index), index));

    }
}
