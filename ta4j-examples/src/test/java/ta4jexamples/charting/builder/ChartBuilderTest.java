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
package ta4jexamples.charting.builder;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYDifferenceRenderer;
import org.jfree.chart.ui.Layer;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYDataset;

import java.util.Collection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.PriceChannel;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

import java.awt.BasicStroke;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

import ta4jexamples.charting.ChannelBoundaryIndicator;
import ta4jexamples.charting.ChartingTestFixtures;
import ta4jexamples.charting.workflow.ChartWorkflow;

class ChartBuilderTest {

    private ChartWorkflow chartWorkflow;
    private BarSeries series;
    private TradingRecord tradingRecord;

    @BeforeEach
    void setUp() {
        chartWorkflow = new ChartWorkflow();
        series = ChartingTestFixtures.standardDailySeries();
        tradingRecord = ChartingTestFixtures.completedTradeRecord(series);
    }

    @Test
    void buildsCandlestickChartWithIndicatorOverlay() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withLineColor(Color.CYAN)
                .toChart();

        assertNotNull(chart);
        assertInstanceOf(CombinedDomainXYPlot.class, chart.getPlot());
        CombinedDomainXYPlot combined = (CombinedDomainXYPlot) chart.getPlot();
        assertEquals(1, combined.getSubplots().size());
        XYPlot basePlot = combined.getSubplots().get(0);
        // Candle dataset + overlay dataset
        assertEquals(2, basePlot.getDatasetCount());
    }

    @Test
    void barIndexTimeAxisModeBuildsNumberAxis() {
        BarSeries gapSeries = ChartingTestFixtures.dailySeriesWithWeekendGap("Gap Series");

        JFreeChart chart = chartWorkflow.builder()
                .withTimeAxisMode(TimeAxisMode.BAR_INDEX)
                .withSeries(gapSeries)
                .toChart();

        CombinedDomainXYPlot combined = (CombinedDomainXYPlot) chart.getPlot();
        assertInstanceOf(NumberAxis.class, combined.getDomainAxis());

        XYPlot basePlot = combined.getSubplots().get(0);
        OHLCDataset dataset = (OHLCDataset) basePlot.getDataset(0);
        assertEquals(gapSeries.getBeginIndex(), dataset.getXValue(0, 0), 0.0);
        assertEquals(gapSeries.getBeginIndex() + 1, dataset.getXValue(0, 1), 0.0);
    }

    @Test
    void withChannelOverlayAddsUpperMedianLowerOverlays() {
        ConstantIndicator upper = constantIndicator(110, "upper");
        ConstantIndicator median = constantIndicator(100, "median");
        ConstantIndicator lower = constantIndicator(90, "lower");

        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withChannelOverlay(upper, median, lower)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        assertEquals(5, basePlot.getDatasetCount(), "Channel overlays should add fill plus three datasets");
    }

    @Test
    void withChannelOverlayAddsUpperAndLowerOverlays() {
        ConstantIndicator upper = constantIndicator(110, "upper");
        ConstantIndicator lower = constantIndicator(90, "lower");

        JFreeChart chart = chartWorkflow.builder().withSeries(series).withChannelOverlay(upper, lower).toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        assertEquals(4, basePlot.getDatasetCount(), "Channel overlays should add fill plus two datasets");
    }

    @Test
    void withChannelOverlayWrapsPriceChannelIndicator() {
        ConstantChannelIndicator channelIndicator = new ConstantChannelIndicator(series, 110, 90, 100, "Test Channel");

        JFreeChart chart = chartWorkflow.builder().withSeries(series).withChannelOverlay(channelIndicator).toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        assertEquals(5, basePlot.getDatasetCount(),
                "Channel overlays should add fill plus upper, median, and lower datasets when wrapping a channel indicator");
    }

    @Test
    void channelOverlayMedianDefaultsToDashedLowerOpacity() {
        ConstantIndicator upper = constantIndicator(110, "upper");
        ConstantIndicator median = constantIndicator(100, "median");
        ConstantIndicator lower = constantIndicator(90, "lower");

        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withChannelOverlay(upper, median, lower)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        int medianDatasetIndex = -1;
        for (int i = 0; i < basePlot.getDatasetCount(); i++) {
            if (basePlot.getDataset(i) instanceof TimeSeriesCollection collection && collection.getSeriesCount() > 0
                    && "median".equals(collection.getSeriesKey(0))) {
                medianDatasetIndex = i;
                break;
            }
        }

        assertTrue(medianDatasetIndex >= 0, "Median dataset should be present");
        StandardXYItemRenderer renderer = (StandardXYItemRenderer) basePlot.getRenderer(medianDatasetIndex);
        BasicStroke stroke = (BasicStroke) renderer.getSeriesStroke(0);
        assertNotNull(stroke.getDashArray(), "Median stroke should be dashed by default");
        assertTrue(stroke.getLineWidth() < 1.2f, "Median stroke should be thinner than default channel lines");
    }

    @Test
    void channelOverlayStylingUpdatesLinesAndFill() {
        ConstantIndicator upper = constantIndicator(110, "upper");
        ConstantIndicator lower = constantIndicator(90, "lower");
        Color lineColor = new Color(0x607D8B);
        Color fillColor = new Color(0x90A4AE);
        float lineOpacity = 0.6f;
        float fillOpacity = 0.2f;
        float lineWidth = 2.2f;

        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withChannelOverlay(upper, lower)
                .withLineColor(lineColor)
                .withOpacity(lineOpacity)
                .withLineWidth(lineWidth)
                .withFillColor(fillColor)
                .withFillOpacity(fillOpacity)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        XYDifferenceRenderer fillRenderer = null;
        int standardRendererCount = 0;
        for (int i = 0; i < basePlot.getRendererCount(); i++) {
            if (basePlot.getRenderer(i) instanceof XYDifferenceRenderer differenceRenderer) {
                fillRenderer = differenceRenderer;
            } else if (basePlot.getRenderer(i) instanceof StandardXYItemRenderer standardRenderer) {
                Color paint = (Color) standardRenderer.getSeriesPaint(0);
                assertEquals(lineColor.getRed(), paint.getRed(), "Channel line red should match");
                assertEquals(lineColor.getGreen(), paint.getGreen(), "Channel line green should match");
                assertEquals(lineColor.getBlue(), paint.getBlue(), "Channel line blue should match");
                assertEquals(Math.round(lineOpacity * 255), paint.getAlpha(), "Channel line opacity should match");
                BasicStroke stroke = (BasicStroke) standardRenderer.getSeriesStroke(0);
                assertEquals(lineWidth, stroke.getLineWidth(), "Channel line width should match");
                standardRendererCount++;
            }
        }

        assertNotNull(fillRenderer, "Channel fill renderer should be present");
        Color fillPaint = (Color) fillRenderer.getPositivePaint();
        assertEquals(fillColor.getRed(), fillPaint.getRed(), "Channel fill red should match");
        assertEquals(fillColor.getGreen(), fillPaint.getGreen(), "Channel fill green should match");
        assertEquals(fillColor.getBlue(), fillPaint.getBlue(), "Channel fill blue should match");
        assertEquals(Math.round(fillOpacity * 255), fillPaint.getAlpha(), "Channel fill opacity should match");
        assertEquals(2, standardRendererCount, "Channel should render two line overlays");
    }

    @Test
    void channelOverlaysRequireUpperAndLowerBoundaries() {
        ConstantChannelIndicator channelIndicator = new ConstantChannelIndicator(series, 110, 90, 100,
                "Partial Channel");
        ChannelBoundaryIndicator upperBoundary = new ChannelBoundaryIndicator(channelIndicator,
                PriceChannel.Boundary.UPPER);

        ChartBuilder.ChartStage stage = chartWorkflow.builder().withSeries(series).withIndicatorOverlay(upperBoundary);

        assertThrows(IllegalStateException.class, stage::toChart,
                "Channel overlays should require both upper and lower boundaries before charting");
    }

    @Test
    void channelOverlaysAllowUpperAndLowerBoundariesTogether() {
        ConstantChannelIndicator channelIndicator = new ConstantChannelIndicator(series, 110, 90, 100, "Full Channel");
        ChannelBoundaryIndicator upperBoundary = new ChannelBoundaryIndicator(channelIndicator,
                PriceChannel.Boundary.UPPER);
        ChannelBoundaryIndicator lowerBoundary = new ChannelBoundaryIndicator(channelIndicator,
                PriceChannel.Boundary.LOWER);

        ChartBuilder.ChartStage stage = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(upperBoundary)
                .withIndicatorOverlay(lowerBoundary);

        assertDoesNotThrow(stage::toChart, "Upper and lower boundaries should satisfy channel overlay validation");
    }

    @Test
    void supportsIndicatorBaseChartsWithSubplot() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        JFreeChart chart = chartWorkflow.builder().withIndicator(closePrice).withSubChart(rsiIndicator).toChart();

        CombinedDomainXYPlot combined = (CombinedDomainXYPlot) chart.getPlot();
        assertEquals(2, combined.getSubplots().size(), "Indicator chart should contain base plot plus sub-plot");
    }

    @Test
    void tradingRecordOverlayAddsMarkers() {
        JFreeChart chart = chartWorkflow.builder().withSeries(series).withTradingRecordOverlay(tradingRecord).toChart();

        CombinedDomainXYPlot combined = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot basePlot = combined.getSubplots().get(0);
        assertTrue(basePlot.getRendererCount() >= 2,
                "Trading record overlay should install an additional renderer for markers");
    }

    @Test
    void overlayStylingAppliesCustomColorAndStroke() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withLineColor(Color.ORANGE)
                .withLineWidth(3f)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        StandardXYItemRenderer renderer = (StandardXYItemRenderer) basePlot.getRenderer(1);
        assertEquals(Color.ORANGE, renderer.getSeriesPaint(0));
        BasicStroke stroke = (BasicStroke) renderer.getSeriesStroke(0);
        assertEquals(3f, stroke.getLineWidth());
    }

    @Test
    void withSubChartInvalidatesPreviousStage() {
        ChartBuilder.ChartStage baseStage = chartWorkflow.builder().withSeries(series);
        ChartBuilder.ChartStage subStage = baseStage.withSubChart(new ClosePriceIndicator(series));
        assertThrows(IllegalStateException.class, baseStage::toChart,
                "Parent stage should be unusable after withSubChart is invoked");

        assertNotNull(subStage.toChart(), "Sub stage should still be able to build the chart");
    }

    @Test
    void withHorizontalMarkerAddsReferenceLine() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(50.0)
                .toChart();

        CombinedDomainXYPlot combined = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = combined.getSubplots().get(1);
        Collection<?> rangeMarkers = indicatorPlot.getRangeMarkers(Layer.FOREGROUND);
        assertNotNull(rangeMarkers, "Should have range markers");
        assertFalse(rangeMarkers.isEmpty(), "Should have horizontal marker at 50");
    }

    @Test
    void withHorizontalMarkerSupportsStyling() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        Color customColor = Color.RED;
        float customWidth = 2.5f;
        float customOpacity = 0.7f;

        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(50.0)
                .withLineColor(customColor)
                .withLineWidth(customWidth)
                .withOpacity(customOpacity)
                .toChart();

        CombinedDomainXYPlot combined = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = combined.getSubplots().get(1);
        Collection<?> rangeMarkers = indicatorPlot.getRangeMarkers(Layer.FOREGROUND);
        assertFalse(rangeMarkers.isEmpty(), "Should have horizontal marker");
        // Verify marker exists (styling is applied during rendering)
        assertTrue(rangeMarkers.size() >= 1, "Should have at least one marker");
    }

    @Test
    void withMultipleHorizontalMarkers() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(30.0)
                .withHorizontalMarker(50.0)
                .withHorizontalMarker(70.0)
                .toChart();

        CombinedDomainXYPlot combined = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = combined.getSubplots().get(1);
        Collection<?> rangeMarkers = indicatorPlot.getRangeMarkers(Layer.FOREGROUND);
        assertNotNull(rangeMarkers, "Should have range markers");
        assertTrue(rangeMarkers.size() >= 3, "Should have at least 3 horizontal markers");
    }

    @Test
    void withHorizontalMarkerRejectsInvalidLineWidth() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        ChartBuilder.StyledMarkerStage stage = chartWorkflow.builder()
                .withSeries(series)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(50.0);

        assertThrows(IllegalArgumentException.class, () -> stage.withLineWidth(0.0f),
                "Line width of 0 should be rejected");
        assertThrows(IllegalArgumentException.class, () -> stage.withLineWidth(-1.0f),
                "Negative line width should be rejected");
        assertThrows(IllegalArgumentException.class, () -> stage.withLineWidth(0.05f),
                "Line width of 0.05 should be rejected");
    }

    @Test
    void withHorizontalMarkerRejectsInvalidOpacity() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        ChartBuilder.StyledMarkerStage stage = chartWorkflow.builder()
                .withSeries(series)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(50.0);

        assertThrows(IllegalArgumentException.class, () -> stage.withOpacity(-0.1f),
                "Negative opacity should be rejected");
        assertThrows(IllegalArgumentException.class, () -> stage.withOpacity(1.1f),
                "Opacity greater than 1.0 should be rejected");
    }

    @Test
    void withHorizontalMarkerAcceptsValidOpacity() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        ChartBuilder.StyledMarkerStage stage = chartWorkflow.builder()
                .withSeries(series)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(50.0);

        // These should not throw
        assertDoesNotThrow(() -> stage.withOpacity(0.0f), "Opacity of 0.0 should be valid");
        assertDoesNotThrow(() -> stage.withOpacity(0.5f), "Opacity of 0.5 should be valid");
        assertDoesNotThrow(() -> stage.withOpacity(1.0f), "Opacity of 1.0 should be valid");
    }

    @Test
    void withHorizontalMarkerRejectsNullColor() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        ChartBuilder.StyledMarkerStage stage = chartWorkflow.builder()
                .withSeries(series)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(50.0);

        assertThrows(NullPointerException.class, () -> stage.withLineColor(null), "Null color should be rejected");
    }

    @Test
    void withHorizontalMarkerChainingWorks() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(50.0)
                .withLineColor(Color.BLUE)
                .withLineWidth(2.0f)
                .withOpacity(0.8f)
                .withHorizontalMarker(30.0)
                .withLineColor(Color.GREEN)
                .toChart();

        assertNotNull(chart, "Chart should be created with chained marker styling");
        CombinedDomainXYPlot combined = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = combined.getSubplots().get(1);
        Collection<?> rangeMarkers = indicatorPlot.getRangeMarkers(Layer.FOREGROUND);
        assertTrue(rangeMarkers.size() >= 2, "Should have at least 2 markers with chained styling");
    }

    @Test
    void terminalOperationConsumesBuilder() {
        ChartBuilder.ChartStage stage = chartWorkflow.builder()
                .withSeries(series)
                .withTradingRecordOverlay(tradingRecord);
        stage.toChart();
        assertThrows(IllegalStateException.class, stage::toChart,
                "All terminal operations should fail after the builder is consumed");
    }

    @Test
    void exposesChartPlanForManualExecution() {
        ChartPlan plan = chartWorkflow.builder().withSeries(series).toPlan();
        JFreeChart chart = chartWorkflow.render(plan);
        assertNotNull(chart, "Rendered chart should not be null");
    }

    @Test
    void toPlanAlsoConsumesBuilder() {
        ChartBuilder.ChartStage stage = chartWorkflow.builder().withSeries(series);
        stage.toPlan();
        assertThrows(IllegalStateException.class, stage::toChart,
                "Invoking toPlan should consume the builder like other terminal operations");
    }

    @Test
    void analysisCriterionOverlayCreatesSecondaryAxis() {
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withTradingRecordOverlay(tradingRecord)
                .withAnalysisCriterionOverlay(new NetProfitCriterion(), tradingRecord)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        assertNotNull(basePlot.getRangeAxis(1), "Analysis overlays should create a secondary axis");
        assertEquals("NetProfit", basePlot.getRangeAxis(1).getLabel(), "Secondary axis should reflect criterion label");
        assertTrue(plotContainsSeries(basePlot, "NetProfit"),
                "Criterion overlay should contribute a dataset with its label");
    }

    @Test
    void tradingRecordOverlayIgnoredForIndicatorCharts() {
        ConstantIndicator baseIndicator = constantIndicator(1, "base-indicator");
        JFreeChart chart = chartWorkflow.builder()
                .withIndicator(baseIndicator)
                .withTradingRecordOverlay(tradingRecord)
                .toChart();

        XYPlot plot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        assertEquals(1, plot.getDatasetCount(),
                "Indicator charts should ignore trading record overlays due to incompatible axes");
    }

    @Test
    void rejectsOverlaysWhenBothAxesOccupied() {
        ConstantIndicator baseIndicator = constantIndicator(5, "base");
        ConstantIndicator near = constantIndicator(6, "near");
        ConstantIndicator far = constantIndicator(500, "far");
        ConstantIndicator rejected = constantIndicator(-500, "rejected");

        ChartBuilder.ChartStage dualAxisStage = chartWorkflow.builder()
                .withIndicator(baseIndicator)
                .withIndicatorOverlay(near)
                .withIndicatorOverlay(far);
        JFreeChart chartBefore = dualAxisStage.toChart();
        XYPlot plotBefore = ((CombinedDomainXYPlot) chartBefore.getPlot()).getSubplots().get(0);
        int datasetCountBefore = plotBefore.getDatasetCount();
        assertNotNull(plotBefore.getRangeAxis(1), "Secondary axis should be created for the far overlay");

        ChartBuilder.StyledOverlayStage rejectedStage = chartWorkflow.builder()
                .withIndicator(baseIndicator)
                .withIndicatorOverlay(near)
                .withIndicatorOverlay(far)
                .withIndicatorOverlay(rejected);

        assertThrows(IllegalStateException.class, () -> rejectedStage.withLineColor(Color.RED),
                "Styling an invalid overlay should fail fast");

        JFreeChart chart = rejectedStage.toChart();
        XYPlot plot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        assertNotNull(plot.getRangeAxis(1), "Secondary axis should persist after rejecting the overlay");
        assertEquals(datasetCountBefore, plot.getDatasetCount(),
                "Rejected overlay must not mutate the dataset collection");
    }

    @Test
    void overlayLineWidthValidation() {
        ChartBuilder.StyledOverlayStage stage = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(new ClosePriceIndicator(series));

        assertThrows(IllegalArgumentException.class, () -> stage.withLineWidth(0.01f),
                "Line width must be positive and above the minimum threshold");
        assertDoesNotThrow(stage::toChart);
    }

    @Test
    void overlayStylingAppliesCustomOpacity() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        float opacity = 0.5f;
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withLineColor(Color.BLUE)
                .withOpacity(opacity)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        StandardXYItemRenderer renderer = (StandardXYItemRenderer) basePlot.getRenderer(1);
        Color paintColor = (Color) renderer.getSeriesPaint(0);
        assertEquals(Color.BLUE.getRed(), paintColor.getRed());
        assertEquals(Color.BLUE.getGreen(), paintColor.getGreen());
        assertEquals(Color.BLUE.getBlue(), paintColor.getBlue());
        assertEquals(Math.round(opacity * 255), paintColor.getAlpha(),
                "Opacity should be applied to the color's alpha channel");
    }

    @Test
    void overlayOpacityDefaultsToFullyOpaque() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withLineColor(Color.RED)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        StandardXYItemRenderer renderer = (StandardXYItemRenderer) basePlot.getRenderer(1);
        Color paintColor = (Color) renderer.getSeriesPaint(0);
        assertEquals(255, paintColor.getAlpha(), "Default opacity should be 1.0 (fully opaque)");
    }

    @Test
    void overlayOpacityValidation() {
        ChartBuilder.StyledOverlayStage stage = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(new ClosePriceIndicator(series));

        assertThrows(IllegalArgumentException.class, () -> stage.withOpacity(-0.1f), "Opacity must be >= 0.0");
        assertThrows(IllegalArgumentException.class, () -> stage.withOpacity(1.1f), "Opacity must be <= 1.0");
        assertDoesNotThrow(() -> stage.withOpacity(0.0f), "Opacity 0.0 should be valid");
        assertDoesNotThrow(() -> stage.withOpacity(1.0f), "Opacity 1.0 should be valid");
        assertDoesNotThrow(() -> stage.withOpacity(0.5f), "Opacity 0.5 should be valid");
        assertDoesNotThrow(stage::toChart);
    }

    @Test
    void overlayOpacityCanBeChainedWithOtherStylingMethods() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        float opacity = 0.75f;
        Color customColor = new Color(128, 0, 128); // Purple
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withOpacity(opacity)
                .withLineColor(customColor)
                .withLineWidth(2.5f)
                .withConnectAcrossNaN(true)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        StandardXYItemRenderer renderer = (StandardXYItemRenderer) basePlot.getRenderer(1);
        Color paintColor = (Color) renderer.getSeriesPaint(0);
        assertEquals(customColor.getRed(), paintColor.getRed());
        assertEquals(customColor.getGreen(), paintColor.getGreen());
        assertEquals(customColor.getBlue(), paintColor.getBlue());
        assertEquals(Math.round(opacity * 255), paintColor.getAlpha(),
                "Opacity should be preserved when chained with other styling methods");

        BasicStroke stroke = (BasicStroke) renderer.getSeriesStroke(0);
        assertEquals(2.5f, stroke.getLineWidth(), "Line width should still be applied");
    }

    @Test
    void overlayOpacityCanBeSetAfterOtherStylingMethods() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        float opacity = 0.6f;
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withLineColor(Color.CYAN)
                .withLineWidth(3.0f)
                .withOpacity(opacity)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        StandardXYItemRenderer renderer = (StandardXYItemRenderer) basePlot.getRenderer(1);
        Color paintColor = (Color) renderer.getSeriesPaint(0);
        assertEquals(Math.round(opacity * 255), paintColor.getAlpha(),
                "Opacity should work when set after other styling methods");
    }

    @Test
    void overlayOpacityEdgeCases() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // Test fully transparent (0.0)
        JFreeChart chartTransparent = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withLineColor(Color.GREEN)
                .withOpacity(0.0f)
                .toChart();
        XYPlot basePlotTransparent = ((CombinedDomainXYPlot) chartTransparent.getPlot()).getSubplots().get(0);
        StandardXYItemRenderer rendererTransparent = (StandardXYItemRenderer) basePlotTransparent.getRenderer(1);
        Color paintColorTransparent = (Color) rendererTransparent.getSeriesPaint(0);
        assertEquals(0, paintColorTransparent.getAlpha(), "Opacity 0.0 should result in fully transparent color");

        // Test fully opaque (1.0)
        JFreeChart chartOpaque = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withLineColor(Color.GREEN)
                .withOpacity(1.0f)
                .toChart();
        XYPlot basePlotOpaque = ((CombinedDomainXYPlot) chartOpaque.getPlot()).getSubplots().get(0);
        StandardXYItemRenderer rendererOpaque = (StandardXYItemRenderer) basePlotOpaque.getRenderer(1);
        Color paintColorOpaque = (Color) rendererOpaque.getSeriesPaint(0);
        assertEquals(255, paintColorOpaque.getAlpha(), "Opacity 1.0 should result in fully opaque color");
    }

    @Test
    void withConnectAcrossNaNFalseCreatesMultipleSegmentsForNaNValues() {
        IndicatorWithNaN indicator = new IndicatorWithNaN(series, new int[] { 2, 3, 4 });
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(indicator)
                .withConnectAcrossNaN(false)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        TimeSeriesCollection dataset = (TimeSeriesCollection) basePlot.getDataset(1);
        assertTrue(dataset.getSeriesCount() > 1,
                "When connectGaps is false, NaN values should create multiple series segments");
    }

    @Test
    void withConnectAcrossNaNTrueCreatesSingleSegmentForNaNValues() {
        IndicatorWithNaN indicator = new IndicatorWithNaN(series, new int[] { 2, 3, 4 });
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(indicator)
                .withConnectAcrossNaN(true)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        TimeSeriesCollection dataset = (TimeSeriesCollection) basePlot.getDataset(1);
        assertEquals(1, dataset.getSeriesCount(),
                "When connectGaps is true, NaN values should be skipped and non-NaN values connected in a single segment");
    }

    @Test
    void withConnectAcrossNaNDefaultsToFalse() {
        IndicatorWithNaN indicator = new IndicatorWithNaN(series, new int[] { 2, 3, 4 });
        JFreeChart chart = chartWorkflow.builder().withSeries(series).withIndicatorOverlay(indicator).toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        TimeSeriesCollection dataset = (TimeSeriesCollection) basePlot.getDataset(1);
        assertTrue(dataset.getSeriesCount() > 1,
                "By default (connectGaps not set), NaN values should create multiple series segments");
    }

    @Test
    void withConnectAcrossNaNCanBeChainedWithOtherStyling() {
        IndicatorWithNaN indicator = new IndicatorWithNaN(series, new int[] { 2, 3, 4 });
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(indicator)
                .withLineColor(Color.MAGENTA)
                .withLineWidth(2.5f)
                .withConnectAcrossNaN(true)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        TimeSeriesCollection dataset = (TimeSeriesCollection) basePlot.getDataset(1);
        assertEquals(1, dataset.getSeriesCount(),
                "withConnectAcrossNaN should work when chained with other styling methods");

        StandardXYItemRenderer renderer = (StandardXYItemRenderer) basePlot.getRenderer(1);
        assertEquals(Color.MAGENTA, renderer.getSeriesPaint(0), "Color should still be applied");
        BasicStroke stroke = (BasicStroke) renderer.getSeriesStroke(0);
        assertEquals(2.5f, stroke.getLineWidth(), "Line width should still be applied");
    }

    @Test
    void withConnectAcrossNaNTrueSkipsNaNButConnectsValidValues() {
        // Create indicator with valid values at indices 0,1, NaN at 2,3,4, then valid
        // at 5,6
        IndicatorWithNaN indicator = new IndicatorWithNaN(series, new int[] { 2, 3, 4 });
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(indicator)
                .withConnectAcrossNaN(true)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        TimeSeriesCollection dataset = (TimeSeriesCollection) basePlot.getDataset(1);
        assertEquals(1, dataset.getSeriesCount(),
                "All valid values should be in a single connected segment when connectGaps is true");

        // Verify the segment contains values from before and after the NaN gap
        org.jfree.data.time.TimeSeries timeSeries = dataset.getSeries(0);
        assertTrue(timeSeries.getItemCount() > 0, "The connected segment should contain valid values");
    }

    @Test
    void withConnectAcrossNaNFalseSplitsOnNaN() {
        // Create indicator with valid values at indices 0,1, NaN at 2,3,4, then valid
        // at 5,6
        IndicatorWithNaN indicator = new IndicatorWithNaN(series, new int[] { 2, 3, 4 });
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(indicator)
                .withConnectAcrossNaN(false)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        TimeSeriesCollection dataset = (TimeSeriesCollection) basePlot.getDataset(1);
        assertEquals(2, dataset.getSeriesCount(),
                "NaN values should split the series into two segments (before and after the gap)");
    }

    @Test
    void withLabelSetsCustomLabelInChartLegend() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        String customLabel = "Custom Close Price Label";
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withLabel(customLabel)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        TimeSeriesCollection dataset = (TimeSeriesCollection) basePlot.getDataset(1);
        assertEquals(customLabel, dataset.getSeriesKey(0), "Custom label should appear in the chart legend");
    }

    @Test
    void withLabelDefaultsToIndicatorToStringWhenNotSet() {
        ConstantIndicator indicator = constantIndicator(100.0, "TestIndicator");
        JFreeChart chart = chartWorkflow.builder().withSeries(series).withIndicatorOverlay(indicator).toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        TimeSeriesCollection dataset = (TimeSeriesCollection) basePlot.getDataset(1);
        assertEquals("TestIndicator", dataset.getSeriesKey(0),
                "When no label is set, should fall back to indicator.toString()");
    }

    @Test
    void withLabelCanBeChainedWithOtherStylingMethods() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        String customLabel = "Styled Indicator";
        Color customColor = new Color(128, 0, 128); // Purple color
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withLabel(customLabel)
                .withLineColor(customColor)
                .withLineWidth(2.5f)
                .withConnectAcrossNaN(true)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        TimeSeriesCollection dataset = (TimeSeriesCollection) basePlot.getDataset(1);
        assertEquals(customLabel, dataset.getSeriesKey(0),
                "Label should be preserved when chained with other styling methods");

        StandardXYItemRenderer renderer = (StandardXYItemRenderer) basePlot.getRenderer(1);
        assertEquals(customColor, renderer.getSeriesPaint(0), "Color should still be applied");
        BasicStroke stroke = (BasicStroke) renderer.getSeriesStroke(0);
        assertEquals(2.5f, stroke.getLineWidth(), "Line width should still be applied");
    }

    @Test
    void withLabelUsedForSecondaryAxisWhenApplicable() {
        ConstantIndicator baseIndicator = constantIndicator(5, "base");
        ConstantIndicator farIndicator = constantIndicator(500, "far-indicator");
        String customLabel = "Custom Secondary Axis Label";
        JFreeChart chart = chartWorkflow.builder()
                .withIndicator(baseIndicator)
                .withIndicatorOverlay(farIndicator)
                .withLabel(customLabel)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        assertNotNull(basePlot.getRangeAxis(1), "Secondary axis should be created for the far overlay");
        assertEquals(customLabel, basePlot.getRangeAxis(1).getLabel(),
                "Custom label should be used for the secondary axis label");
        assertTrue(plotContainsSeries(basePlot, customLabel), "Custom label should appear in the dataset");
    }

    @Test
    void withLabelCanBeSetAfterOtherStylingMethods() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        String customLabel = "Label Set Last";
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(closePrice)
                .withLineColor(Color.CYAN)
                .withLineWidth(3.0f)
                .withLabel(customLabel)
                .toChart();

        XYPlot basePlot = ((CombinedDomainXYPlot) chart.getPlot()).getSubplots().get(0);
        TimeSeriesCollection dataset = (TimeSeriesCollection) basePlot.getDataset(1);
        assertEquals(customLabel, dataset.getSeriesKey(0), "Label should work when set after other styling methods");
    }

    private ConstantIndicator constantIndicator(double value, String name) {
        return new ConstantIndicator(series, value, name);
    }

    private boolean plotContainsSeries(XYPlot plot, String seriesName) {
        for (int i = 0; i < plot.getDatasetCount(); i++) {
            XYDataset dataset = plot.getDataset(i);
            if (dataset instanceof TimeSeriesCollection collection) {
                for (int j = 0; j < collection.getSeriesCount(); j++) {
                    if (seriesName.equals(collection.getSeriesKey(j))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static final class ConstantIndicator implements Indicator<Num> {
        private final Num value;
        private final String name;
        private final BarSeries series;

        private ConstantIndicator(BarSeries series, double value, String name) {
            this.series = series;
            this.value = series.numFactory().numOf(value);
            this.name = name;
        }

        @Override
        public Num getValue(int index) {
            return value;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }
    }

    /**
     * Test indicator that returns NaN for specified indices and valid values for
     * others.
     */
    private static final class IndicatorWithNaN implements Indicator<Num> {
        private final BarSeries series;
        private final int[] nanIndices;
        private final Num validValue;

        IndicatorWithNaN(BarSeries series, int[] nanIndices) {
            this.series = series;
            this.nanIndices = nanIndices.clone();
            this.validValue = series.numFactory().numOf(100.0);
        }

        @Override
        public Num getValue(int index) {
            for (int nanIndex : nanIndices) {
                if (index == nanIndex) {
                    return NaN.NaN;
                }
            }
            return validValue;
        }

        @Override
        public String toString() {
            return "TestIndicatorWithNaN";
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }
    }

    private record TestChannel(Num upper, Num lower, Num median) implements PriceChannel {
    }

    private static final class ConstantChannelIndicator implements Indicator<PriceChannel> {
        private final BarSeries series;
        private final PriceChannel channel;
        private final String name;

        private ConstantChannelIndicator(BarSeries series, double upper, double lower, double median, String name) {
            this.series = series;
            this.channel = new TestChannel(series.numFactory().numOf(upper), series.numFactory().numOf(lower),
                    series.numFactory().numOf(median));
            this.name = name;
        }

        @Override
        public PriceChannel getValue(int index) {
            return channel;
        }

        @Override
        public String toString() {
            return name;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }
    }
}
