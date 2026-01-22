/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.TimeSeriesCollection;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.SwingPointMarkerIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.TrendLineSegment;
import org.ta4j.core.num.Num;

import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.CsvFileBarSeriesDataSource;

/**
 * Analysis coverage for trendline + swing point indicators on realistic data.
 */
class TrendLineAndSwingPointAnalysisTest {

    @Test
    void analysisHarnessVerifiesHeadroomAndRendersOverlays() {
        TrendLineAndSwingPointAnalysis analysis = new TrendLineAndSwingPointAnalysis();
        analysis.verifyDefaultCapsHeadroomForBundledDatasets();

        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();
        assertNotNull(series, "Example bar series should not be null");
        assertFalse(series.isEmpty(), "Example bar series should not be empty");

        int lookback = Math.min(series.getBarCount(), TrendLineAndSwingPointAnalysis.DEFAULT_TRENDLINE_LOOKBACK);
        TrendLineAndSwingPointAnalysis.AnalysisChartArtifacts artifacts = analysis.buildAnalysisChartArtifacts(series,
                lookback, TrendLineAndSwingPointAnalysis.DEFAULT_SURROUNDING_BARS);

        int endIndex = series.getEndIndex();
        TrendLineAndSwingPointAnalysis.TrendLineVariants trendLines = artifacts.trendLines();
        TrendLineAndSwingPointAnalysis.SwingMarkerVariants swings = artifacts.swings();

        assertAll(() -> assertTrendLinePopulates(trendLines.support(), endIndex),
                () -> assertTrendLinePopulates(trendLines.supportTouchBiased(), endIndex),
                () -> assertTrendLinePopulates(trendLines.supportExtremeBiased(), endIndex),
                () -> assertTrendLinePopulates(trendLines.resistance(), endIndex),
                () -> assertTrendLinePopulates(trendLines.resistanceTouchBiased(), endIndex),
                () -> assertTrendLinePopulates(trendLines.resistanceExtremeBiased(), endIndex));

        assertAll(() -> assertSwingMarkersPopulated(swings.fractalLows(), series),
                () -> assertSwingMarkersPopulated(swings.fractalHighs(), series),
                () -> assertSwingMarkersPopulated(swings.zigzagLows(), series),
                () -> assertSwingMarkersPopulated(swings.zigzagHighs(), series));

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        ChartPlan plan = artifacts.plan();
        JFreeChart chart = chartWorkflow.render(plan);
        assertNotNull(chart, "Rendered chart should not be null");

        byte[] png = chartWorkflow.getChartAsByteArray(chart);
        assertTrue(png.length > 0, "Rendered chart should produce non-empty PNG bytes");

        assertRenderedOverlaysHaveData(chart, plan);
    }

    private void assertTrendLinePopulates(AbstractTrendLineIndicator trendLine, int endIndex) {
        trendLine.getValue(endIndex);
        TrendLineSegment segment = trendLine.getCurrentSegment();
        assertNotNull(segment, "Trendline should have an active segment at the end of the series");

        assertTrue(segment.firstIndex < segment.secondIndex, "Trendline anchors should be ordered in time");
        assertTrue(segment.windowStart <= segment.firstIndex && segment.firstIndex <= segment.windowEnd,
                "First anchor should be inside the evaluation window");
        assertTrue(segment.secondIndex <= segment.windowEnd,
                "Second anchor should be before or at the end of the evaluation window");
        assertEquals(endIndex, segment.windowEnd, "Trendline window should be anchored at endIndex");

        List<Integer> swingIndexes = trendLine.getSwingPointIndexes();
        assertTrue(swingIndexes.contains(segment.firstIndex), "First anchor should be a confirmed swing point");
        assertTrue(swingIndexes.contains(segment.secondIndex), "Second anchor should be a confirmed swing point");

        Num valueAtEnd = trendLine.getValue(endIndex);
        assertNotNull(valueAtEnd, "Trendline value should not be null");
        assertFalse(valueAtEnd.isNaN(), "Trendline value should be defined at endIndex");

        Num valueAtStart = trendLine.getValue(segment.windowStart);
        assertNotNull(valueAtStart, "Trendline value should not be null within the window");
        assertFalse(valueAtStart.isNaN(), "Trendline should backfill values within the window");

        if (segment.windowStart > trendLine.getBarSeries().getBeginIndex()) {
            Num beforeWindow = trendLine.getValue(segment.windowStart - 1);
            assertTrue(beforeWindow.isNaN(), "Trendline should return NaN before the evaluation window");
        }
    }

    private void assertSwingMarkersPopulated(SwingPointMarkerIndicator marker, BarSeries series) {
        int endIndex = series.getEndIndex();
        marker.getValue(endIndex);

        List<Integer> swingIndexes = marker.getSwingIndicator().getSwingPointIndexesUpTo(endIndex);
        assertFalse(swingIndexes.isEmpty(), "Swing indicator should produce at least one swing point");
        assertStrictlyIncreasingWithinBounds(swingIndexes, series.getBeginIndex(), endIndex);

        int firstSwing = swingIndexes.get(0);
        Num markerPrice = marker.getPriceIndicator().getValue(firstSwing);
        if (markerPrice == null || markerPrice.isNaN()) {
            markerPrice = series.getBar(firstSwing).getClosePrice();
        }
        assertNotNull(markerPrice, "Swing marker should have a price value available at a swing point index");
        assertFalse(markerPrice.isNaN(), "Swing marker should have a defined price value at a swing point index");

        Integer nonSwingIndex = findNonSwingIndex(series.getBeginIndex(), endIndex, swingIndexes);
        assertNotNull(nonSwingIndex, "Series should contain at least one non-swing index");
        assertFalse(marker.getSwingPointIndexes().contains(nonSwingIndex),
                "Non-swing index should not be a swing point");
    }

    private Integer findNonSwingIndex(int beginIndex, int endIndex, List<Integer> swingIndexes) {
        for (int i = beginIndex; i <= endIndex; i++) {
            if (!swingIndexes.contains(i)) {
                return i;
            }
        }
        return null;
    }

    private void assertStrictlyIncreasingWithinBounds(List<Integer> indexes, int beginIndex, int endIndex) {
        Integer previous = null;
        for (Integer index : indexes) {
            assertNotNull(index, "Swing index must not be null");
            assertTrue(index >= beginIndex && index <= endIndex,
                    "Swing index " + index + " must be within [" + beginIndex + ", " + endIndex + "]");
            if (previous != null) {
                assertTrue(index > previous, "Swing indexes must be strictly increasing");
            }
            previous = index;
        }
    }

    private void assertRenderedOverlaysHaveData(JFreeChart chart, ChartPlan plan) {
        CombinedDomainXYPlot combinedPlot = assertInstanceOf(CombinedDomainXYPlot.class, chart.getPlot(),
                "Regression chart should use a combined domain plot");
        assertNotNull(combinedPlot.getSubplots(), "Combined plot should expose its subplots");
        assertFalse(combinedPlot.getSubplots().isEmpty(), "Combined plot should contain at least one subplot");

        XYPlot basePlot = assertInstanceOf(XYPlot.class, combinedPlot.getSubplots().get(0),
                "First subplot should be an XYPlot");

        int expectedOverlays = plan.definition().basePlot().overlays().size();
        assertEquals(1 + expectedOverlays, basePlot.getDatasetCount(), "Each overlay should map to a dataset");

        for (int datasetIndex = 1; datasetIndex < basePlot.getDatasetCount(); datasetIndex++) {
            TimeSeriesCollection dataset = assertInstanceOf(TimeSeriesCollection.class,
                    basePlot.getDataset(datasetIndex), "Overlay datasets should be time series collections");
            assertTrue(totalItemCount(dataset) > 0, "Overlay dataset " + datasetIndex + " should contain data points");
        }
    }

    private int totalItemCount(TimeSeriesCollection dataset) {
        int total = 0;
        for (int seriesIndex = 0; seriesIndex < dataset.getSeriesCount(); seriesIndex++) {
            total += dataset.getSeries(seriesIndex).getItemCount();
        }
        return total;
    }
}
