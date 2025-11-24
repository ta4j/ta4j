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
import org.ta4j.core.indicators.supportresistance.TrendLineResistanceIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineSupportIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.TrendLineSegment;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingHighIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingLowIndicator;
import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.loaders.CsvBarsLoader;

/**
 * This class demonstrates the use of support and resistance trendline
 * indicators with visualization using ChartWorkflow.
 * <p>
 * The example loads OHLC data from a CSV or JSON file, creates both support and
 * resistance trendline indicators, and displays them as overlays on a
 * candlestick chart. Support trendlines are shown in green, and resistance
 * trendlines are shown in red.
 */
public class TrendLineAnalysis {

    private static final Logger LOG = LogManager.getLogger(TrendLineAnalysis.class);

    /**
     * Main method to run the trendline analysis example.
     *
     * @param args command line arguments (not used)
     */
    public static void main(String[] args) {
        // Load bar series from CSV file
        BarSeries series = CsvBarsLoader.loadSeriesFromFile();

        Objects.requireNonNull(series, "Bar series was null");
        if (series.isEmpty()) {
            LOG.error("Bar series is empty. Cannot create trendlines.");
            return;
        }

        int trendLineLookback = Math.min(series.getBarCount(), 200);

        // Create support trendline indicator
        TrendLineSupportIndicator fractalSupportTrendLine = new TrendLineSupportIndicator(series, 5, trendLineLookback);

        // Create resistance trendline indicator
        TrendLineResistanceIndicator fractalResistanceTrendLine = new TrendLineResistanceIndicator(series, 5,
                trendLineLookback);

        // Build and display chart using ChartWorkflow
        ChartWorkflow fractalChartWorkflow = new ChartWorkflow();
        ChartPlan fractalPlan = fractalChartWorkflow.builder()
                .withTitle("Fractal Support and Resistance Trendlines")
                .withSeries(series)
                .withIndicatorOverlay(fractalSupportTrendLine)
                .withLineColor(Color.GREEN)
                .withLineWidth(2.0f)
                .withIndicatorOverlay(fractalResistanceTrendLine)
                .withLineColor(Color.RED)
                .withLineWidth(2.0f)
                .toPlan();

        fractalChartWorkflow.display(fractalPlan);
        fractalChartWorkflow.save(fractalPlan, "log/charts", "fractal-support-resistance-trendlines");

        TrendLineResistanceIndicator zigzagResistanceTrendLine = new TrendLineResistanceIndicator(
                new RecentZigZagSwingHighIndicator(series), 5, 5, trendLineLookback);
        TrendLineSupportIndicator zigzagSupportTrendLine = new TrendLineSupportIndicator(
                new RecentZigZagSwingLowIndicator(series), 5, 5, trendLineLookback);

        ChartWorkflow zigzagChartWorkflow = new ChartWorkflow();
        ChartPlan zigzagPlan = zigzagChartWorkflow.builder()
                .withTitle("ZigZag Support and Resistance Trendlines")
                .withSeries(series)
                .withIndicatorOverlay(zigzagSupportTrendLine)
                .withLineColor(Color.BLUE)
                .withLineWidth(2.0f)
                .withIndicatorOverlay(zigzagResistanceTrendLine)
                .withLineColor(Color.YELLOW)
                .withLineWidth(2.0f)
                .toPlan();

        zigzagChartWorkflow.display(zigzagPlan);
        zigzagChartWorkflow.save(zigzagPlan, "log/charts", "zigzag-support-resistance-trendlines");

        logSegment("Fractal support", fractalSupportTrendLine.getCurrentSegment());
        logSegment("Fractal resistance", fractalResistanceTrendLine.getCurrentSegment());
        logSegment("ZigZag support", zigzagSupportTrendLine.getCurrentSegment());
        logSegment("ZigZag resistance", zigzagResistanceTrendLine.getCurrentSegment());
    }

    private static void logSegment(String label, TrendLineSegment segment) {
        if (segment == null) {
            LOG.info("{} trendline: no active segment", label);
            return;
        }
        LOG.info(
                "{} trendline anchors=({}, {}), slope={}, intercept={}, swingTouches={}, swingsOutside={}, anchoredAtExtreme={}, score={}",
                label, segment.firstIndex, segment.secondIndex, segment.slope, segment.intercept,
                segment.countOfSwingPointsAnchoringTrendline, segment.countOfSwingPointsOutsideTrendline,
                segment.isTrendLineAnchoredByExtremeSwingPoint, segment.score);
    }
}
