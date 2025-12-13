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
import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

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
        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();

        Objects.requireNonNull(series, "Bar series was null");
        if (series.isEmpty()) {
            LOG.error("Bar series is empty. Cannot create trendlines.");
            return;
        }

        int trendLineLookback = Math.min(series.getBarCount(), 200);
        int surroundingBars = 5;

        // Create support trendline indicator
        TrendLineSupportIndicator supportLine = new TrendLineSupportIndicator(series, surroundingBars,
                trendLineLookback);
        TrendLineSupportIndicator supportLineWithTouchCountBias = new TrendLineSupportIndicator(series, surroundingBars,
                trendLineLookback, TrendLineSupportIndicator.ScoringWeights.touchCountBiasPreset());
        TrendLineSupportIndicator supportLineWithExtremeSwingBias = new TrendLineSupportIndicator(series,
                surroundingBars, trendLineLookback, TrendLineSupportIndicator.ScoringWeights.extremeSwingBiasPreset());

        // Create resistance trendline indicator
        TrendLineResistanceIndicator resistanceLine = new TrendLineResistanceIndicator(series, surroundingBars,
                trendLineLookback);
        TrendLineResistanceIndicator resistanceLineWithTouchCountBias = new TrendLineResistanceIndicator(series,
                surroundingBars, trendLineLookback, TrendLineResistanceIndicator.ScoringWeights.touchCountBiasPreset());
        TrendLineResistanceIndicator resistanceLineWithExtremeSwingBias = new TrendLineResistanceIndicator(series,
                surroundingBars, trendLineLookback,
                TrendLineResistanceIndicator.ScoringWeights.extremeSwingBiasPreset());

        // Build and display chart using ChartWorkflow
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        ChartPlan chartPlan = chartWorkflow.builder()
                .withTitle("Fractal Support and Resistance Trendlines")
                .withSeries(series)
                .withIndicatorOverlay(supportLine)
                .withLineColor(Color.GREEN)
                .withLineWidth(2.0f)
                .withOpacity(0.5f)
                .withLabel("Support (default)")
                .withIndicatorOverlay(supportLineWithTouchCountBias)
                .withLineColor(Color.BLUE)
                .withLineWidth(2.0f)
                .withOpacity(0.5f)
                .withLabel("Support (touchCountBiasPreset)")
                .withIndicatorOverlay(supportLineWithExtremeSwingBias)
                .withLineColor(Color.MAGENTA)
                .withLineWidth(2.0f)
                .withOpacity(0.5f)
                .withLabel("Support (extremeSwingBiasPreset)")
                .withIndicatorOverlay(resistanceLine)
                .withLineColor(Color.RED)
                .withLineWidth(2.0f)
                .withOpacity(0.5f)
                .withLabel("Resistance (default)")
                .withIndicatorOverlay(resistanceLineWithTouchCountBias)
                .withLineColor(Color.CYAN)
                .withLineWidth(2.0f)
                .withOpacity(0.5f)
                .withLabel("Resistance (touchCountBiasPreset)")
                .withIndicatorOverlay(resistanceLineWithExtremeSwingBias)
                .withLineColor(Color.ORANGE)
                .withLineWidth(2.0f)
                .withOpacity(0.5f)
                .withLabel("Resistance (extremeSwingBiasPreset)")
                .toPlan();

        chartWorkflow.display(chartPlan);
        chartWorkflow.save(chartPlan, "temp/charts", "support-resistance-trendlines");

        logSegment("Support (default)", supportLine.getCurrentSegment());
        logSegment("Support (preferTouchPoint)", supportLineWithTouchCountBias.getCurrentSegment());
        logSegment("Support (preferExtremePoint)", supportLineWithExtremeSwingBias.getCurrentSegment());

        logSegment("Resistance (default)", resistanceLine.getCurrentSegment());
        logSegment("Resistance (preferTouchPoint)", resistanceLineWithTouchCountBias.getCurrentSegment());
        logSegment("Resistance (preferExtremePoint)", resistanceLineWithExtremeSwingBias.getCurrentSegment());

    }

    private static void logSegment(String label, TrendLineSegment segment) {
        if (segment == null) {
            LOG.info("{} trendline: no active segment", label);
            return;
        }
        LOG.info(
                "{} trendline anchors=({}, {}), slope={}, intercept={}, swingTouches={}, swingsOutside={}, anchoredAtExtreme={}, score={}",
                label, segment.firstIndex, segment.secondIndex, segment.slope, segment.intercept, segment.touchCount,
                segment.outsideCount, segment.touchesExtreme, segment.score);
    }
}
