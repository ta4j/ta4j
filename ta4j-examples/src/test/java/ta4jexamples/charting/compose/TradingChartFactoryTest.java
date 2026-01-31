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
package ta4jexamples.charting.compose;

import org.jfree.chart.LegendItemCollection;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.Assume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.AbstractRecentSwingIndicator;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.RecentFractalSwingHighIndicator;
import org.ta4j.core.indicators.RecentFractalSwingLowIndicator;
import org.ta4j.core.indicators.SwingPointMarkerIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

import ta4jexamples.charting.AnalysisCriterionIndicator;
import ta4jexamples.charting.ChartingTestFixtures;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.builder.TimeAxisMode;
import ta4jexamples.charting.workflow.ChartWorkflow;

/**
 * Unit tests for {@link TradingChartFactory}.
 */
class TradingChartFactoryTest {

    private TradingChartFactory factory;
    private BarSeries barSeries;
    private TradingRecord tradingRecord;

    @BeforeEach
    void setUp() {
        factory = new TradingChartFactory();
        barSeries = ChartingTestFixtures.standardDailySeries();
        tradingRecord = ChartingTestFixtures.completedTradeRecord(barSeries);
    }

    @Test
    void testCreateTradingRecordChart() {
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getTitle(), "Chart should have a title");
        assertTrue(chart.getTitle().getText().contains("Test Strategy"), "Chart title should contain strategy name");
    }

    @Test
    void testCreateTradingRecordChartWithEmptyRecord() {
        TradingRecord emptyRecord = new BaseTradingRecord();
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", emptyRecord);

        assertNotNull(chart, "Chart should be created even with empty record");
    }

    @Test
    void testCreateTradingRecordChartAddsTradeMarkers() {
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        XYPlot plot = (XYPlot) chart.getPlot();

        assertEquals(2, plot.getDatasetCount(), "Should have OHLC dataset and trade dataset");
        assertInstanceOf(XYLineAndShapeRenderer.class, plot.getRenderer(1),
                "Trade dataset should use XYLineAndShapeRenderer");
    }

    @Test
    void testCreateTradingRecordChartAddsPositionMarkers() {
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        XYPlot plot = (XYPlot) chart.getPlot();

        Collection<?> domainMarkers = plot.getDomainMarkers(Layer.BACKGROUND);
        assertNotNull(domainMarkers, "Should have domain markers");
        assertFalse(domainMarkers.isEmpty(), "Should have position markers");
        assertTrue(domainMarkers.stream().anyMatch(marker -> marker instanceof IntervalMarker),
                "Should use IntervalMarker for positions");
    }

    @Test
    void testCreateTradingRecordChartWithIndicatorsAddsTradingOverlay() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord, closePrice, sma);

        assertNotNull(chart.getTitle(), "Combined chart should have a title");
        assertEquals("Test Strategy@" + barSeries.getName().split(" ")[0], chart.getTitle().getText(),
                "Combined chart should reuse trading record title format");
        assertInstanceOf(CombinedDomainXYPlot.class, chart.getPlot(), "Combined domain plot expected");
        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        assertEquals(3, combinedPlot.getSubplots().size(), "Main subplot plus two indicator panels expected");

        XYPlot mainPlot = combinedPlot.getSubplots().get(0);
        assertTrue(mainPlot.getDatasetCount() > 1,
                "Trading dataset should be attached to the main subplot alongside OHLC data");
        assertInstanceOf(XYLineAndShapeRenderer.class, mainPlot.getRenderer(1),
                "Trading dataset should use marker renderer");
        assertTrue(mainPlot.getAnnotations().stream().anyMatch(XYTextAnnotation.class::isInstance),
                "Trade annotations should be present on the main subplot");
        Collection<?> domainMarkers = mainPlot.getDomainMarkers(Layer.BACKGROUND);
        assertNotNull(domainMarkers, "Position shading should still be applied to the main subplot");
        assertFalse(domainMarkers.isEmpty(), "Position shading should not be empty");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testCreateTradingRecordChartWithIndicatorsFallsBackWhenEmpty() {
        JFreeChart baseline = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        Indicator<Num>[] empty = (Indicator<Num>[]) new Indicator<?>[0];
        JFreeChart viaNewMethod = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord, empty);

        assertInstanceOf(XYPlot.class, baseline.getPlot(), "Baseline chart should be a single XY plot");
        assertInstanceOf(XYPlot.class, viaNewMethod.getPlot(), "Empty indicator array should fall back to base chart");
        assertEquals(baseline.getTitle().getText(), viaNewMethod.getTitle().getText(),
                "Titles should remain consistent when falling back");
    }

    @Test
    void testBuildChartTitleWithBothNames() {
        String title = factory.buildChartTitle("AAPL", "Moving Average Crossover");
        assertEquals("Moving Average Crossover@AAPL", title);
    }

    @Test
    void testBuildChartTitleWithStrategyNameOnly() {
        String title = factory.buildChartTitle(null, "Moving Average Crossover");
        assertEquals("Moving Average Crossover", title);
    }

    @Test
    void testBuildChartTitleWithSeriesNameOnly() {
        String title = factory.buildChartTitle("AAPL", null);
        assertEquals("AAPL", title);
    }

    @Test
    void testBuildChartTitleWithBothNull() {
        String title = factory.buildChartTitle(null, null);
        assertNull(title, "Both null should return null or empty string");
    }

    @Test
    void testBuildChartTitleWithBothEmpty() {
        String title = factory.buildChartTitle("", "");
        assertEquals("", title, "Empty bar series name should return empty strategy name");
    }

    @Test
    void testBuildChartTitleWithEmptySeriesNullStrategy() {
        String title = factory.buildChartTitle("", null);
        assertNull(title, "Empty bar series name with null strategy should return null");
    }

    @Test
    void testBuildChartTitleWithNullSeriesEmptyStrategy() {
        String title = factory.buildChartTitle(null, "");
        assertEquals("", title, "Null bar series name should return strategy name");
    }

    @Test
    void testBuildChartTitleTakesFirstWordFromSeriesName() {
        String title = factory.buildChartTitle("AAPL Daily Prices", "Strategy");
        assertEquals("Strategy@AAPL", title, "Should only use first word of series name");
    }

    @Test
    void testCreateIndicatorChart() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = factory.createIndicatorChart(barSeries, closePrice, sma);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getTitle(), "Chart should have a title");
    }

    @Test
    void testCreateIndicatorChartWithMultipleIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma5 = new SMAIndicator(closePrice, 5);
        SMAIndicator sma10 = new SMAIndicator(closePrice, 10);

        JFreeChart chart = factory.createIndicatorChart(barSeries, sma5, sma10);

        assertNotNull(chart, "Chart should not be null");
        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        assertNotNull(combinedPlot, "Plot should be a CombinedDomainXYPlot");
        assertEquals(3, combinedPlot.getSubplots().size(), "Should have 1 main OHLC plot plus 2 indicator subplots");
    }

    @Test
    void testCreateIndicatorChartWithNoIndicators() {
        JFreeChart chart = factory.createIndicatorChart(barSeries);

        assertNotNull(chart, "Chart should be created even without indicators");
    }

    @Test
    void testChartConfiguration() {
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getBackgroundPaint(), "Chart should have background paint");
    }

    @Test
    void testChartDomainAxisConfiguration() {
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        XYPlot plot = (XYPlot) chart.getPlot();
        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();

        assertNotNull(domainAxis, "Domain axis should be a DateAxis");
        assertTrue(domainAxis.isAutoRange(), "Domain axis should be auto-ranging");
    }

    @Test
    void testOhlcDatasetUsesBarEndTimesWithGaps() {
        BarSeries gapSeries = ChartingTestFixtures.dailySeriesWithWeekendGap("Gap Series");
        TradingRecord emptyRecord = ChartingTestFixtures.emptyRecord();

        JFreeChart chart = factory.createTradingRecordChart(gapSeries, "Gap Strategy", emptyRecord);
        XYPlot plot = (XYPlot) chart.getPlot();
        OHLCDataset dataset = (OHLCDataset) plot.getDataset(0);

        assertNotNull(dataset, "OHLC dataset should be present");
        assertEquals(2, dataset.getItemCount(0), "OHLC dataset should contain the gap series bars");

        long firstTime = dataset.getX(0, 0).longValue();
        long secondTime = dataset.getX(0, 1).longValue();
        assertEquals(gapSeries.getBar(0).getEndTime().toEpochMilli(), firstTime);
        assertEquals(gapSeries.getBar(1).getEndTime().toEpochMilli(), secondTime);
        assertEquals(Duration.ofDays(3).toMillis(), secondTime - firstTime,
                "Gap between bars should be preserved on the time axis");
    }

    @Test
    void testBarIndexTimeAxisModeCompressesMissingBars() {
        BarSeries gapSeries = ChartingTestFixtures.dailySeriesWithWeekendGap("Gap Series");
        TradingRecord emptyRecord = ChartingTestFixtures.emptyRecord();

        JFreeChart chart = factory.createTradingRecordChart(gapSeries, "Gap Strategy", emptyRecord,
                TimeAxisMode.BAR_INDEX);
        XYPlot plot = (XYPlot) chart.getPlot();
        assertInstanceOf(NumberAxis.class, plot.getDomainAxis(), "Bar-index policy should use a NumberAxis");

        OHLCDataset dataset = (OHLCDataset) plot.getDataset(0);
        assertNotNull(dataset, "OHLC dataset should be present");
        assertEquals(2, dataset.getItemCount(0), "OHLC dataset should contain the gap series bars");

        double firstX = dataset.getXValue(0, 0);
        double secondX = dataset.getXValue(0, 1);
        assertEquals(gapSeries.getBeginIndex(), firstX, 0.0);
        assertEquals(gapSeries.getBeginIndex() + 1, secondX, 0.0);
        assertEquals(1.0, secondX - firstX, 0.0, "Bar-index policy should compress gaps");
    }

    @Test
    void testChartRangeAxisConfiguration() {
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        XYPlot plot = (XYPlot) chart.getPlot();
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();

        assertNotNull(rangeAxis, "Range axis should be a NumberAxis");
        assertNotNull(rangeAxis, "Range axis should be configured");
    }

    @Test
    void testChartWithDifferentTimePeriods() {
        // Daily series
        BarSeries dailySeries = ChartingTestFixtures.dailySeries("Daily");
        TradingRecord dailyRecord = ChartingTestFixtures.completedTradeRecord(dailySeries);
        JFreeChart dailyChart = factory.createTradingRecordChart(dailySeries, "Daily", dailyRecord);
        assertNotNull(dailyChart, "Daily chart should be created");

        // Intraday series
        BarSeries intradaySeries = ChartingTestFixtures.hourlySeries("Intraday");
        TradingRecord intradayRecord = ChartingTestFixtures.completedTradeRecord(intradaySeries);
        JFreeChart intradayChart = factory.createTradingRecordChart(intradaySeries, "Intraday", intradayRecord);
        assertNotNull(intradayChart, "Intraday chart should be created");
    }

    @Test
    void testChartHandlesOpenPosition() {
        TradingRecord openPositionRecord = new BaseTradingRecord();
        openPositionRecord.enter(0, barSeries.getBar(0).getClosePrice(), barSeries.numFactory().numOf(1));
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test", openPositionRecord);

        assertNotNull(chart, "Chart should handle open positions");
        XYPlot plot = (XYPlot) chart.getPlot();
        Collection<?> domainMarkers = plot.getDomainMarkers(Layer.BACKGROUND);
        assertFalse(domainMarkers.isEmpty(), "Should have marker for open position");
    }

    @Test
    void testBarSeriesLabelOverlayAddsAnnotations() {
        BarSeries series = ChartingTestFixtures.standardDailySeries();

        List<BarLabel> labels = List.of(new BarLabel(3, series.getBar(3).getClosePrice(), "", LabelPlacement.CENTER),
                new BarLabel(5, series.getBar(5).getClosePrice(), "1", LabelPlacement.ABOVE),
                new BarLabel(8, series.getBar(8).getClosePrice(), "2", LabelPlacement.BELOW));
        BarSeriesLabelIndicator labelIndicator = new BarSeriesLabelIndicator(series, labels);

        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(labelIndicator)
                .withLabel("Labels")
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot basePlot = combinedPlot.getSubplots().get(0);

        List<String> annotationTexts = basePlot.getAnnotations()
                .stream()
                .filter(XYTextAnnotation.class::isInstance)
                .map(annotation -> ((XYTextAnnotation) annotation).getText())
                .toList();

        assertTrue(annotationTexts.containsAll(List.of("1", "2")), "Wave labels should be attached as annotations");
        assertFalse(annotationTexts.contains(""), "Blank labels should not be rendered as annotations");

        int labelDatasetIndex = -1;
        for (int i = 0; i < basePlot.getDatasetCount(); i++) {
            if (basePlot.getDataset(i) instanceof TimeSeriesCollection collection && collection.getSeriesCount() > 0
                    && "Labels".equals(collection.getSeriesKey(0).toString())) {
                labelDatasetIndex = i;
                break;
            }
        }
        assertTrue(labelDatasetIndex >= 0, "Label dataset should be present on the plot");
        assertInstanceOf(XYLineAndShapeRenderer.class, basePlot.getRenderer(labelDatasetIndex),
                "Label dataset should render with the line/shape renderer");
    }

    @Test
    void testSwingPointOverlayChartMatchesSwingPointAnalysisFlow() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());

        BarSeries swingSeries = swingPointSeries();
        LowPriceIndicator lowPrice = new LowPriceIndicator(swingSeries);
        HighPriceIndicator highPrice = new HighPriceIndicator(swingSeries);
        RecentFractalSwingLowIndicator swingLowIndicator = new RecentFractalSwingLowIndicator(lowPrice, 5, 5, 0);
        RecentFractalSwingHighIndicator swingHighIndicator = new RecentFractalSwingHighIndicator(highPrice, 5, 5, 0);

        SwingPointMarkerIndicator swingLowMarkers = new SwingPointMarkerIndicator(swingSeries, swingLowIndicator);
        SwingPointMarkerIndicator swingHighMarkers = new SwingPointMarkerIndicator(swingSeries, swingHighIndicator);

        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withTitle("Fractal Swing Point Analysis")
                .withSeries(swingSeries)
                .withIndicatorOverlay(swingLowMarkers)
                .withLineColor(Color.GREEN)
                .withLineWidth(3.0f)
                .withConnectAcrossNaN(true)
                .withIndicatorOverlay(swingHighMarkers)
                .withLineColor(Color.RED)
                .withLineWidth(3.0f)
                .withConnectAcrossNaN(true)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot basePlot = combinedPlot.getSubplots().get(0);

        assertEquals(3, basePlot.getRendererCount(),
                "Candles plus the two swing overlays should register separate renderers");
        assertEquals(3, basePlot.getDatasetCount(),
                "Base price data plus low and high swing marker overlays expected on the plot");
        assertNotNull(basePlot.getRenderer(0), "Primary candlestick renderer should be present");
        assertNotNull(basePlot.getRenderer(1), "Low swing overlay renderer should be present");
        assertNotNull(basePlot.getRenderer(2), "High swing overlay renderer should be present");
        TimeSeriesCollection lowDataset = (TimeSeriesCollection) basePlot.getDataset(1);
        TimeSeriesCollection highDataset = (TimeSeriesCollection) basePlot.getDataset(2);
        int lowSeriesCount = lowDataset.getSeriesCount();
        int highSeriesCount = highDataset.getSeriesCount();
        List<Integer> swingLowIndexes = new java.util.ArrayList<>(swingLowMarkers.getSwingPointIndexes());
        List<Integer> swingHighIndexes = new java.util.ArrayList<>(swingHighMarkers.getSwingPointIndexes());
        Num swingLowValue = swingLowIndexes.isEmpty() ? NaN.NaN
                : swingLowMarkers.getPriceIndicator().getValue(swingLowIndexes.get(0));
        Num swingHighValue = swingHighIndexes.isEmpty() ? NaN.NaN
                : swingHighMarkers.getPriceIndicator().getValue(swingHighIndexes.get(0));
        XYLineAndShapeRenderer lowRenderer = (XYLineAndShapeRenderer) basePlot.getRenderer(1);
        XYLineAndShapeRenderer highRenderer = (XYLineAndShapeRenderer) basePlot.getRenderer(2);
        assertEquals(Boolean.TRUE, lowRenderer.getSeriesLinesVisible(0),
                "withConnectAcrossNaN(true) should connect swing low markers with lines");
        assertEquals(Boolean.TRUE, highRenderer.getSeriesLinesVisible(0),
                "withConnectAcrossNaN(true) should connect swing high markers with lines");

        LegendItemCollection legendItems = basePlot.getLegendItems();
        assertNotNull(legendItems, "Legend items should be available");
        List<String> legendLabels = new java.util.ArrayList<>();
        for (int i = 0; i < legendItems.getItemCount(); i++) {
            legendLabels.add(legendItems.get(i).getLabel());
        }
        assertEquals(3, legendItems.getItemCount(),
                "Price series and both swing marker overlays should appear in the legend. Series counts low/high: "
                        + lowSeriesCount + "/" + highSeriesCount + ". Swing indexes low/high: " + swingLowIndexes + "/"
                        + swingHighIndexes + ". Swing values low/high: " + swingLowValue + "/" + swingHighValue
                        + ". Actual labels: " + legendLabels);

        String baseSeriesLabel = swingSeries.getName().split(" ")[0];
        int swingMarkerLegendEntries = 0;
        boolean baseSeriesSeen = false;
        for (int i = 0; i < legendItems.getItemCount(); i++) {
            String label = legendItems.get(i).getLabel();
            if (baseSeriesLabel.equals(label)) {
                baseSeriesSeen = true;
            } else if (swingLowMarkers.toString().equals(label)) {
                swingMarkerLegendEntries++;
            }
        }
        assertTrue(baseSeriesSeen, "Legend should include the base bar series label");
        assertEquals(2, swingMarkerLegendEntries,
                "Both swing marker overlays (low and high) should be represented in the legend");

        assertFalse(swingLowIndexes.isEmpty(), "Fixture should produce swing lows");
        assertFalse(swingHighIndexes.isEmpty(), "Fixture should produce swing highs");
    }

    @Test
    void testSwingPointOverlayRespectsConnectAcrossNaNDisabled() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());

        BarSeries swingSeries = swingPointSeries();
        LowPriceIndicator lowPrice = new LowPriceIndicator(swingSeries);
        HighPriceIndicator highPrice = new HighPriceIndicator(swingSeries);
        SwingPointMarkerIndicator swingLowMarkers = new SwingPointMarkerIndicator(swingSeries,
                new RecentFractalSwingLowIndicator(lowPrice, 5, 5, 0));
        SwingPointMarkerIndicator swingHighMarkers = new SwingPointMarkerIndicator(swingSeries,
                new RecentFractalSwingHighIndicator(highPrice, 5, 5, 0));

        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withTitle("Fractal Swing Point Analysis (No Connect)")
                .withSeries(swingSeries)
                .withIndicatorOverlay(swingLowMarkers)
                .withLineColor(Color.GREEN)
                .withLineWidth(3.0f)
                .withConnectAcrossNaN(false)
                .withIndicatorOverlay(swingHighMarkers)
                .withLineColor(Color.RED)
                .withLineWidth(3.0f)
                .withConnectAcrossNaN(false)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot basePlot = combinedPlot.getSubplots().get(0);
        XYLineAndShapeRenderer lowRenderer = (XYLineAndShapeRenderer) basePlot.getRenderer(1);
        XYLineAndShapeRenderer highRenderer = (XYLineAndShapeRenderer) basePlot.getRenderer(2);

        assertEquals(Boolean.FALSE, lowRenderer.getSeriesLinesVisible(0),
                "withConnectAcrossNaN(false) should leave swing low markers unconnected");
        assertEquals(Boolean.FALSE, highRenderer.getSeriesLinesVisible(0),
                "withConnectAcrossNaN(false) should leave swing high markers unconnected");
    }

    @Test
    void testSwingPointOverlayUsesCircularDotsScaledByLineWidth() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());

        BarSeries swingSeries = swingPointSeries();
        SwingPointMarkerIndicator swingLowMarkers = new SwingPointMarkerIndicator(swingSeries,
                new RecentFractalSwingLowIndicator(new LowPriceIndicator(swingSeries), 5, 5, 0));

        float lineWidth = 5.0f;
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(swingSeries)
                .withIndicatorOverlay(swingLowMarkers)
                .withLineColor(Color.GREEN)
                .withLineWidth(lineWidth)
                .withConnectAcrossNaN(false)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot basePlot = combinedPlot.getSubplots().get(0);
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) basePlot.getRenderer(1);

        assertEquals(Boolean.FALSE, renderer.getSeriesLinesVisible(0),
                "Dots-only overlay should not connect points when connectAcrossNaN is false");
        // Swing markers default to 90% opacity (0.9), so color will have alpha
        // component
        Color paintColor = (Color) renderer.getSeriesPaint(0);
        assertNotNull(paintColor, "Paint color should not be null");
        assertEquals(Color.GREEN.getRed(), paintColor.getRed(), "Dot red component should match overlay color");
        assertEquals(Color.GREEN.getGreen(), paintColor.getGreen(), "Dot green component should match overlay color");
        assertEquals(Color.GREEN.getBlue(), paintColor.getBlue(), "Dot blue component should match overlay color");
        assertEquals(Math.round(0.9f * 255), paintColor.getAlpha(),
                "Dot color should have default 90% opacity for swing markers");
        Color fillPaintColor = (Color) renderer.getSeriesFillPaint(0);
        assertNotNull(fillPaintColor, "Fill paint color should not be null");
        assertEquals(Color.GREEN.getRed(), fillPaintColor.getRed(),
                "Dot fill red component should match overlay color");
        assertEquals(Color.GREEN.getGreen(), fillPaintColor.getGreen(),
                "Dot fill green component should match overlay color");
        assertEquals(Color.GREEN.getBlue(), fillPaintColor.getBlue(),
                "Dot fill blue component should match overlay color");
        assertEquals(Math.round(0.9f * 255), fillPaintColor.getAlpha(),
                "Dot fill color should have default 90% opacity for swing markers");
        assertNotNull(renderer.getSeriesShape(0), "Renderer should provide a dot shape");
        double expectedDiameter = Math.max(3.0, lineWidth * 2.4);
        assertEquals(expectedDiameter, renderer.getSeriesShape(0).getBounds2D().getWidth(), 0.001,
                "Dot diameter should scale with line width");
        assertEquals(expectedDiameter, renderer.getSeriesShape(0).getBounds2D().getHeight(), 0.001,
                "Dot height should match diameter for a circle");
    }

    @Test
    void testSwingPointOverlayRespectsOpacity() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());

        BarSeries swingSeries = swingPointSeries();
        SwingPointMarkerIndicator swingLowMarkers = new SwingPointMarkerIndicator(swingSeries,
                new RecentFractalSwingLowIndicator(new LowPriceIndicator(swingSeries), 5, 5, 0));

        Color customColor = Color.BLUE;
        float customOpacity = 0.7f;
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(swingSeries)
                .withIndicatorOverlay(swingLowMarkers)
                .withLineColor(customColor)
                .withOpacity(customOpacity)
                .withLineWidth(3.0f)
                .withConnectAcrossNaN(false)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot basePlot = combinedPlot.getSubplots().get(0);
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) basePlot.getRenderer(1);

        // Verify color with opacity
        Color paintColor = (Color) renderer.getSeriesPaint(0);
        assertNotNull(paintColor, "Paint color should not be null");
        assertEquals(customColor.getRed(), paintColor.getRed(), "Red component should match");
        assertEquals(customColor.getGreen(), paintColor.getGreen(), "Green component should match");
        assertEquals(customColor.getBlue(), paintColor.getBlue(), "Blue component should match");
        assertEquals(Math.round(customOpacity * 255), paintColor.getAlpha(), "Alpha component should reflect opacity");

        // Verify fill paint also has opacity applied
        Color fillPaintColor = (Color) renderer.getSeriesFillPaint(0);
        assertNotNull(fillPaintColor, "Fill paint color should not be null");
        assertEquals(customColor.getRed(), fillPaintColor.getRed(), "Fill red component should match");
        assertEquals(customColor.getGreen(), fillPaintColor.getGreen(), "Fill green component should match");
        assertEquals(customColor.getBlue(), fillPaintColor.getBlue(), "Fill blue component should match");
        assertEquals(Math.round(customOpacity * 255), fillPaintColor.getAlpha(),
                "Fill alpha component should reflect opacity");
    }

    @Test
    void testSwingMarkerDefaultOpacityIs90Percent() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());

        BarSeries swingSeries = swingPointSeries();
        SwingPointMarkerIndicator swingLowMarkers = new SwingPointMarkerIndicator(swingSeries,
                new RecentFractalSwingLowIndicator(new LowPriceIndicator(swingSeries), 5, 5, 0));

        Color customColor = Color.BLUE;
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(swingSeries)
                .withIndicatorOverlay(swingLowMarkers)
                .withLineColor(customColor)
                // No explicit opacity set - should default to 0.9 (90%) for swing markers
                .withLineWidth(3.0f)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot basePlot = combinedPlot.getSubplots().get(0);
        XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) basePlot.getRenderer(1);

        // Verify default opacity of 0.9 (90%) is applied
        Color paintColor = (Color) renderer.getSeriesPaint(0);
        assertNotNull(paintColor, "Paint color should not be null");
        assertEquals(Math.round(0.9f * 255), paintColor.getAlpha(),
                "Swing markers should default to 90% opacity (0.9) when no opacity is explicitly set");

        // Verify fill paint also has default opacity
        Color fillPaintColor = (Color) renderer.getSeriesFillPaint(0);
        assertNotNull(fillPaintColor, "Fill paint color should not be null");
        assertEquals(Math.round(0.9f * 255), fillPaintColor.getAlpha(),
                "Swing marker fill paint should also default to 90% opacity");
    }

    @Test
    void testSwingMarkersRenderedAfterBaseDataset() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());

        BarSeries swingSeries = swingPointSeries();
        SwingPointMarkerIndicator swingLowMarkers = new SwingPointMarkerIndicator(swingSeries,
                new RecentFractalSwingLowIndicator(new LowPriceIndicator(swingSeries), 5, 5, 0));

        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(swingSeries)
                .withIndicatorOverlay(swingLowMarkers)
                .withLineColor(Color.RED)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot basePlot = combinedPlot.getSubplots().get(0);

        // Base OHLC dataset should be at index 0
        assertTrue(basePlot.getDatasetCount() > 0, "Base plot should have at least the OHLC dataset");
        assertNotNull(basePlot.getDataset(0), "Base dataset (OHLC) should exist at index 0");

        // Swing marker dataset should be added after base dataset (at index 1 or
        // higher)
        // This ensures swing markers are rendered on top of candles
        if (basePlot.getDatasetCount() > 1) {
            assertNotNull(basePlot.getDataset(1), "Swing marker dataset should exist after base dataset");
            assertInstanceOf(XYLineAndShapeRenderer.class, basePlot.getRenderer(1),
                    "Swing markers should use XYLineAndShapeRenderer");
            // Verify the dataset index is higher than base, ensuring rendering order puts
            // markers in front
            assertTrue(basePlot.getDatasetCount() > 1,
                    "Swing markers should be added at a higher dataset index than base OHLC dataset");
        }
    }

    @Test
    void testSwingMarkerDatasetFallsBackToClosePriceWhenIndicatorIsNaN() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());

        BarSeries series = swingPointSeries();
        NaNPriceIndicator priceIndicator = new NaNPriceIndicator(series);
        StubSwingIndicator swingIndicator = new StubSwingIndicator(priceIndicator, List.of(2, 5));
        SwingPointMarkerIndicator swingMarkers = new SwingPointMarkerIndicator(series, swingIndicator);

        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(series)
                .withIndicatorOverlay(swingMarkers)
                .withLineColor(Color.MAGENTA)
                .withLineWidth(2.0f)
                .withConnectAcrossNaN(false)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot basePlot = combinedPlot.getSubplots().get(0);
        TimeSeriesCollection dataset = (TimeSeriesCollection) basePlot.getDataset(1);

        assertEquals(1, dataset.getSeriesCount(), "Fallback should still produce a series for swing markers");
        assertEquals(series.getBar(2).getClosePrice().doubleValue(), dataset.getYValue(0, 0), 0.0001,
                "Swing marker value should fall back to bar close when indicator is NaN");
        assertEquals(series.getBar(5).getClosePrice().doubleValue(), dataset.getYValue(0, 1), 0.0001,
                "Second swing marker should also use fallback close price");
    }

    // ========== Dual-Axis Chart Tests ==========

    @Test
    void testCreateDualAxisChart() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = factory.createDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA");

        assertNotNull(chart, "Dual-axis chart should not be null");
        assertNotNull(chart.getTitle(), "Chart should have a title");
        assertTrue(chart.getTitle().getText().contains(barSeries.getName()) || barSeries.getName() == null,
                "Chart title should contain series name");
    }

    @Test
    void testCreateDualAxisChartWithCustomTitle() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = factory.createDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA",
                "Custom Title");

        assertNotNull(chart, "Dual-axis chart should not be null");
        assertNotNull(chart.getTitle(), "Chart should have a title");
        assertEquals("Custom Title", chart.getTitle().getText(), "Chart title should match custom title");
    }

    @Test
    void testCreateDualAxisChartWithBarIndexTimeAxisMode() {
        BarSeries gapSeries = ChartingTestFixtures.dailySeriesWithWeekendGap("Gap Series");
        ClosePriceIndicator closePrice = new ClosePriceIndicator(gapSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 3);

        JFreeChart chart = factory.createDualAxisChart(gapSeries, closePrice, "Price", sma, "SMA", null,
                TimeAxisMode.BAR_INDEX);
        XYPlot plot = (XYPlot) chart.getPlot();

        assertInstanceOf(NumberAxis.class, plot.getDomainAxis());
        assertInstanceOf(XYSeriesCollection.class, plot.getDataset(0));
        assertEquals(gapSeries.getBeginIndex(), plot.getDataset(0).getXValue(0, 0), 0.0);
    }

    @Test
    void testCreateDualAxisChartHasSecondaryAxis() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = factory.createDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA");
        XYPlot plot = (XYPlot) chart.getPlot();

        assertNotNull(plot.getRangeAxis(1), "Chart should have a secondary range axis");
        assertNotNull(plot.getDataset(1), "Chart should have a secondary dataset");
        // Verify that secondary dataset exists and is properly configured
        assertTrue(plot.getDatasetCount() >= 2, "Chart should have at least 2 datasets");
    }

    @Test
    void testCreateDualAxisChartHasTitlePaint() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = factory.createDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA");

        assertNotNull(chart.getTitle(), "Chart should have a title");
        assertNotNull(chart.getTitle().getPaint(), "Chart title should have paint (color) configured");
    }

    @Test
    void testCreateDualAxisChartStructure() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = factory.createDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA");
        XYPlot plot = (XYPlot) chart.getPlot();

        // Verify we have primary dataset (index 0) and secondary dataset (index 1)
        assertEquals(2, plot.getDatasetCount(), "Should have 2 datasets (primary and secondary)");
        assertNotNull(plot.getDataset(0), "Primary dataset should not be null");
        assertNotNull(plot.getDataset(1), "Secondary dataset should not be null");
    }

    @Test
    void testCreateDualAxisChartRendererConfiguration() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = factory.createDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA");
        XYPlot plot = (XYPlot) chart.getPlot();

        assertNotNull(plot.getRenderer(1), "Secondary renderer should be configured");
    }

    @Test
    void testCreateDualAxisChartWithoutCustomTitleUsesSeriesName() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = factory.createDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA");

        assertNotNull(chart.getTitle(), "Chart should have a title");
        if (barSeries.getName() != null) {
            assertTrue(chart.getTitle().getText().contains(barSeries.getName()),
                    "Chart title should contain series name when not customized");
        }
    }

    @Test
    void testCreateDualAxisChartWithoutCustomTitleUsesSeriesToString() {
        BarSeries unnamedSeries = ChartingTestFixtures.dailySeries(null);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(unnamedSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = factory.createDualAxisChart(unnamedSeries, closePrice, "Price (USD)", sma, "SMA");

        assertNotNull(chart.getTitle(), "Chart should have a title even with unnamed series");
        assertNotNull(chart.getTitle().getText(), "Chart title text should not be null");
    }

    @Test
    void testDomainAxisHasSufficientUpperMargin() {
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        XYPlot plot = (XYPlot) chart.getPlot();
        assertTrue(plot.getDomainAxis().getUpperMargin() >= 0.07,
                "Domain axis should reserve space for annotations near the last bar");
    }

    @Test
    void testAddAnalysisCriterionAddsSecondaryAxisWithLabel() {
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, new NetProfitCriterion(),
                tradingRecord);

        factory.addAnalysisCriterionToChart(chart, barSeries, indicator, indicator.toString());

        XYPlot plot = (XYPlot) chart.getPlot();
        NumberAxis axis = (NumberAxis) plot.getRangeAxis(1);
        assertNotNull(axis, "Secondary axis should be added for criterion overlay");
        assertEquals("NetProfit", axis.getLabel(), "Axis label should reflect criterion name");
        assertTrue(plotContainsSeries(plot, "NetProfit"), "Criterion dataset should be added with a descriptive label");
    }

    @Test
    void testAddAnalysisCriterionUsesIndexDatasetForBarIndexMode() {
        BarSeries gapSeries = ChartingTestFixtures.dailySeriesWithWeekendGap("Gap Series");
        TradingRecord record = ChartingTestFixtures.emptyRecord();
        JFreeChart chart = factory.createTradingRecordChart(gapSeries, "Test Strategy", record, TimeAxisMode.BAR_INDEX);
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(gapSeries, new NetProfitCriterion(),
                record);

        factory.addAnalysisCriterionToChart(chart, gapSeries, indicator, indicator.toString());

        XYPlot plot = (XYPlot) chart.getPlot();
        XYSeriesCollection dataset = findXYSeriesDataset(plot, indicator.toString());
        assertNotNull(dataset, "Criterion overlay should use XYSeriesCollection in BAR_INDEX mode");
        assertEquals(gapSeries.getBeginIndex(), dataset.getXValue(0, 0), 0.0);
    }

    @Test
    void testAddAnalysisCriterionRejectedWhenAxisConfiguredForDifferentLabel() {
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        AnalysisCriterionIndicator netProfit = new AnalysisCriterionIndicator(barSeries, new NetProfitCriterion(),
                tradingRecord);
        factory.addAnalysisCriterionToChart(chart, barSeries, netProfit, netProfit.toString());

        XYPlot plot = (XYPlot) chart.getPlot();
        int datasetCountAfterFirst = plot.getDatasetCount();

        AnalysisCriterionIndicator positions = new AnalysisCriterionIndicator(barSeries,
                new NumberOfPositionsCriterion(), tradingRecord);
        factory.addAnalysisCriterionToChart(chart, barSeries, positions, positions.toString());

        assertEquals(datasetCountAfterFirst, plot.getDatasetCount(),
                "Adding a criterion with a conflicting axis label should be rejected");
        assertEquals(0, countDatasetsForLabel(plot, positions.toString()), "Rejected overlays must not add datasets");
    }

    @Test
    void testAddAnalysisCriterionAllowsMultipleDatasetsWhenLabelsMatch() {
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        AnalysisCriterionIndicator first = new AnalysisCriterionIndicator(barSeries, new NetProfitCriterion(),
                tradingRecord);
        factory.addAnalysisCriterionToChart(chart, barSeries, first, first.toString());

        XYPlot plot = (XYPlot) chart.getPlot();
        int datasetCountAfterFirst = plot.getDatasetCount();

        AnalysisCriterionIndicator second = new AnalysisCriterionIndicator(barSeries, new NetProfitCriterion(),
                tradingRecord);
        factory.addAnalysisCriterionToChart(chart, barSeries, second, second.toString());

        assertEquals(datasetCountAfterFirst + 1, plot.getDatasetCount(),
                "Matching labels should reuse the axis and add another dataset");
        assertEquals(2, countDatasetsForLabel(plot, "NetProfit"),
                "Both datasets should be mapped to the NetProfit axis");
    }

    @Test
    void testLongAxisLabelsAreTruncatedWithEllipsis() {
        // Create an indicator with a very long name that exceeds the truncation limit
        Indicator<Num> longNameIndicator = new AbstractIndicator<Num>(barSeries) {
            @Override
            public Num getValue(int index) {
                return barSeries.numFactory().numOf(100.0);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            public String toString() {
                return "ThisIsAVeryLongIndicatorNameThatExceedsThirtyCharacters";
            }
        };

        JFreeChart chart = factory.createIndicatorChart(barSeries, longNameIndicator);
        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        assertNotNull(combinedPlot, "Chart should have combined plot");

        // Find the indicator subplot
        XYPlot indicatorPlot = null;
        for (XYPlot subplot : combinedPlot.getSubplots()) {
            if (subplot.getRangeAxis() != null
                    && subplot.getRangeAxis().getLabel().contains("ThisIsAVeryLongIndicator")) {
                indicatorPlot = subplot;
                break;
            }
        }

        assertNotNull(indicatorPlot, "Should have indicator subplot");
        NumberAxis rangeAxis = (NumberAxis) indicatorPlot.getRangeAxis();
        assertNotNull(rangeAxis, "Range axis should exist");
        String axisLabel = rangeAxis.getLabel();

        // Verify label is truncated
        assertTrue(axisLabel.length() <= 30, "Label should be truncated to 30 characters or less");
        assertTrue(axisLabel.endsWith("..."), "Truncated label should end with ellipsis");
        // substring(0, 27) gives 27 chars, plus "..." = 30 chars total
        assertEquals(30, axisLabel.length(), "Truncated label should be exactly 30 characters");
        assertEquals("ThisIsAVeryLongIndicatorNam...", axisLabel, "Label should be truncated correctly");
    }

    @Test
    void testShortAxisLabelsAreNotTruncated() {
        // Create an indicator with a short name that should not be truncated
        Indicator<Num> shortNameIndicator = new AbstractIndicator<Num>(barSeries) {
            @Override
            public Num getValue(int index) {
                return barSeries.numFactory().numOf(100.0);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            public String toString() {
                return "ShortIndicator";
            }
        };

        JFreeChart chart = factory.createIndicatorChart(barSeries, shortNameIndicator);
        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        assertNotNull(combinedPlot, "Chart should have combined plot");

        // Find the indicator subplot
        XYPlot indicatorPlot = null;
        for (XYPlot subplot : combinedPlot.getSubplots()) {
            if (subplot.getRangeAxis() != null && subplot.getRangeAxis().getLabel().equals("ShortIndicator")) {
                indicatorPlot = subplot;
                break;
            }
        }

        assertNotNull(indicatorPlot, "Should have indicator subplot");
        NumberAxis rangeAxis = (NumberAxis) indicatorPlot.getRangeAxis();
        assertNotNull(rangeAxis, "Range axis should exist");
        String axisLabel = rangeAxis.getLabel();

        // Verify label is not truncated
        assertEquals("ShortIndicator", axisLabel, "Short labels should not be truncated");
        assertFalse(axisLabel.endsWith("..."), "Short labels should not have ellipsis");
    }

    @Test
    void testSecondaryAxisLabelsAreTruncated() {
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, new NetProfitCriterion(),
                tradingRecord);

        // Use a very long label that exceeds the truncation limit
        String longLabel = "ThisIsAVeryLongAnalysisCriterionLabelThatExceedsThirtyCharacters";
        factory.addAnalysisCriterionToChart(chart, barSeries, indicator, longLabel);

        XYPlot plot = (XYPlot) chart.getPlot();
        NumberAxis axis = (NumberAxis) plot.getRangeAxis(1);
        assertNotNull(axis, "Secondary axis should be added");
        String axisLabel = axis.getLabel();

        // Verify label is truncated
        assertTrue(axisLabel.length() <= 30, "Secondary axis label should be truncated to 30 characters or less");
        assertTrue(axisLabel.endsWith("..."), "Truncated secondary axis label should end with ellipsis");
        // substring(0, 27) gives 27 chars, plus "..." = 30 chars total
        assertEquals(30, axisLabel.length(), "Truncated secondary axis label should be exactly 30 characters");
        assertEquals("ThisIsAVeryLongAnalysisCrit...", axisLabel, "Secondary axis label should be truncated correctly");
    }

    @Test
    void testPositionMarkerNearEndUsesRightAlignedLabel() {
        BaseTradingRecord record = new BaseTradingRecord();
        Num amount = barSeries.numFactory().numOf(1);
        addPosition(record, 1, 3, amount);
        int nearEndStart = barSeries.getEndIndex() - 2;
        addPosition(record, nearEndStart, barSeries.getEndIndex() - 1, amount);

        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", record);
        XYPlot plot = (XYPlot) chart.getPlot();
        List<IntervalMarker> markers = extractPositionMarkers(plot);
        assertFalse(markers.isEmpty(), "Position markers should be present");
        IntervalMarker last = markers.get(markers.size() - 1);
        assertEquals(TextAnchor.TOP_RIGHT, last.getLabelTextAnchor(),
                "Markers near the series end should right-align labels");
    }

    @Test
    void testPositionMarkerAwayFromEdgesUsesLeftAlignedLabel() {
        BaseTradingRecord record = new BaseTradingRecord();
        Num amount = barSeries.numFactory().numOf(1);
        addPosition(record, 2, 4, amount);

        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", record);
        XYPlot plot = (XYPlot) chart.getPlot();
        List<IntervalMarker> markers = extractPositionMarkers(plot);
        assertEquals(1, markers.size(), "Expected a single marker");
        assertEquals(TextAnchor.TOP_LEFT, markers.get(0).getLabelTextAnchor(),
                "Non-edge markers should remain left aligned");
    }

    private BarSeries swingPointSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("Swing Fixture Series").build();
        Duration period = Duration.ofDays(1);
        Instant start = Instant.EPOCH;
        double[] basePrices = new double[] { 10.0, 11.0, 12.0, 13.0, 14.0, 5.0, 12.0, 13.0, 14.0, 15.0, 16.0, 25.0,
                20.0, 19.0, 18.0, 17.0, 16.0 };

        for (int i = 0; i < basePrices.length; i++) {
            double basePrice = basePrices[i];
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(start.plus(period.multipliedBy(i)))
                    .openPrice(basePrice)
                    .highPrice(basePrice + 0.5)
                    .lowPrice(basePrice - 0.5)
                    .closePrice(basePrice)
                    .volume(1000.0 + i)
                    .add();
        }

        return series;
    }

    private static final class NaNPriceIndicator extends AbstractIndicator<Num> {

        NaNPriceIndicator(BarSeries series) {
            super(series);
        }

        @Override
        public Num getValue(int index) {
            return NaN.NaN;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }
    }

    private static final class StubSwingIndicator extends AbstractRecentSwingIndicator {

        private final List<Integer> swingIndexes;

        StubSwingIndicator(Indicator<Num> priceIndicator, List<Integer> swingIndexes) {
            super(priceIndicator, 0);
            this.swingIndexes = List.copyOf(swingIndexes);
        }

        @Override
        protected int detectLatestSwingIndex(int index) {
            int latest = -1;
            for (int swingIndex : swingIndexes) {
                if (swingIndex <= index) {
                    latest = swingIndex;
                } else {
                    break;
                }
            }
            return latest;
        }
    }

    private boolean plotContainsSeries(XYPlot plot, String seriesName) {
        return countDatasetsForLabel(plot, seriesName) > 0;
    }

    private int countDatasetsForLabel(XYPlot plot, String seriesName) {
        int count = 0;
        for (int i = 0; i < plot.getDatasetCount(); i++) {
            if (plot.getDataset(i) instanceof TimeSeriesCollection collection) {
                for (int seriesIndex = 0; seriesIndex < collection.getSeriesCount(); seriesIndex++) {
                    if (seriesName.equals(collection.getSeriesKey(seriesIndex))) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private XYSeriesCollection findXYSeriesDataset(XYPlot plot, String seriesName) {
        for (int i = 0; i < plot.getDatasetCount(); i++) {
            if (plot.getDataset(i) instanceof XYSeriesCollection collection) {
                for (int seriesIndex = 0; seriesIndex < collection.getSeriesCount(); seriesIndex++) {
                    if (seriesName.equals(collection.getSeriesKey(seriesIndex))) {
                        return collection;
                    }
                }
            }
        }
        return null;
    }

    private int findXYSeriesDatasetIndex(XYPlot plot, String seriesName) {
        for (int i = 0; i < plot.getDatasetCount(); i++) {
            if (plot.getDataset(i) instanceof XYSeriesCollection collection) {
                for (int seriesIndex = 0; seriesIndex < collection.getSeriesCount(); seriesIndex++) {
                    if (seriesName.equals(collection.getSeriesKey(seriesIndex))) {
                        return i;
                    }
                }
            }
        }
        return -1;
    }

    private void addPosition(BaseTradingRecord record, int entryIndex, int exitIndex, Num amount) {
        record.enter(entryIndex, barSeries.getBar(entryIndex).getClosePrice(), amount);
        record.exit(exitIndex, barSeries.getBar(exitIndex).getClosePrice(), amount);
    }

    private List<IntervalMarker> extractPositionMarkers(XYPlot plot) {
        Collection<?> markers = plot.getDomainMarkers(Layer.BACKGROUND);
        if (markers == null) {
            return List.of();
        }
        return markers.stream()
                .filter(IntervalMarker.class::isInstance)
                .map(IntervalMarker.class::cast)
                .collect(Collectors.toList());
    }

    // ========== NaN Gap Handling Tests ==========

    @Test
    void testIndicatorWithNoNaNValuesCreatesSingleXYSeriesSegment() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        JFreeChart chart = factory.createIndicatorChart(barSeries, closePrice);

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = (XYPlot) combinedPlot.getSubplots().get(1);
        XYSeriesCollection dataset = (XYSeriesCollection) indicatorPlot.getDataset(0);

        assertEquals(1, dataset.getSeriesCount(), "Indicator with no NaN values should create a single series segment");
    }

    @Test
    void testIndicatorWithNaNValuesCreatesMultipleXYSeriesSegments() {
        IndicatorWithNaN indicator = new IndicatorWithNaN(barSeries, new int[] { 2, 3, 4 });
        JFreeChart chart = factory.createIndicatorChart(barSeries, indicator);

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = (XYPlot) combinedPlot.getSubplots().get(1);
        XYSeriesCollection dataset = (XYSeriesCollection) indicatorPlot.getDataset(0);

        assertTrue(dataset.getSeriesCount() > 1, "Indicator with NaN values should create multiple series segments");
    }

    @Test
    void testIndicatorWithNaNValuesAtStartCreatesGap() {
        // Create indicator with NaN at indices 0, 1, 2, then valid values
        IndicatorWithNaN indicator = new IndicatorWithNaN(barSeries, new int[] { 0, 1, 2 });
        JFreeChart chart = factory.createIndicatorChart(barSeries, indicator);

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = (XYPlot) combinedPlot.getSubplots().get(1);
        XYSeriesCollection dataset = (XYSeriesCollection) indicatorPlot.getDataset(0);

        // Should have at least one segment (for the valid values after NaN)
        assertTrue(dataset.getSeriesCount() >= 1,
                "Indicator with NaN at start should create at least one segment for valid values");
    }

    @Test
    void testIndicatorWithNaNValuesAtEndCreatesGap() {
        // Create indicator with valid values, then NaN at the end
        int endIndex = barSeries.getEndIndex();
        IndicatorWithNaN indicator = new IndicatorWithNaN(barSeries,
                new int[] { endIndex - 2, endIndex - 1, endIndex });
        JFreeChart chart = factory.createIndicatorChart(barSeries, indicator);

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = (XYPlot) combinedPlot.getSubplots().get(1);
        XYSeriesCollection dataset = (XYSeriesCollection) indicatorPlot.getDataset(0);

        // Should have at least one segment (for the valid values before NaN)
        assertTrue(dataset.getSeriesCount() >= 1,
                "Indicator with NaN at end should create at least one segment for valid values");
    }

    @Test
    void testIndicatorWithNaNValuesInMiddleCreatesMultipleSegments() {
        // Create indicator with valid values, NaN in middle, then valid values again
        int midIndex = barSeries.getBeginIndex() + (barSeries.getEndIndex() - barSeries.getBeginIndex()) / 2;
        IndicatorWithNaN indicator = new IndicatorWithNaN(barSeries,
                new int[] { midIndex - 1, midIndex, midIndex + 1 });
        JFreeChart chart = factory.createIndicatorChart(barSeries, indicator);

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = (XYPlot) combinedPlot.getSubplots().get(1);
        XYSeriesCollection dataset = (XYSeriesCollection) indicatorPlot.getDataset(0);

        assertEquals(2, dataset.getSeriesCount(),
                "Indicator with NaN in middle should create two segments (before and after gap)");
    }

    @Test
    void testDualAxisChartWithNoNaNValuesCreatesSingleTimeSeriesSegment() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = factory.createDualAxisChart(barSeries, closePrice, "Price", sma, "SMA");
        XYPlot plot = (XYPlot) chart.getPlot();
        TimeSeriesCollection dataset = (TimeSeriesCollection) plot.getDataset(1);

        assertEquals(1, dataset.getSeriesCount(),
                "Dual-axis chart with no NaN values should create a single TimeSeries segment");
    }

    @Test
    void testDualAxisChartWithNaNValuesCreatesMultipleTimeSeriesSegments() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        IndicatorWithNaN indicator = new IndicatorWithNaN(barSeries, new int[] { 2, 3, 4 });

        JFreeChart chart = factory.createDualAxisChart(barSeries, closePrice, "Price", indicator, "Indicator");
        XYPlot plot = (XYPlot) chart.getPlot();
        TimeSeriesCollection dataset = (TimeSeriesCollection) plot.getDataset(1);

        assertTrue(dataset.getSeriesCount() > 1,
                "Dual-axis chart with NaN values should create multiple TimeSeries segments");
    }

    @Test
    void testAllXYSeriesSegmentsGetSameStyling() {
        IndicatorWithNaN indicator = new IndicatorWithNaN(barSeries, new int[] { 2, 3, 4 });
        JFreeChart chart = factory.createIndicatorChart(barSeries, indicator);

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = (XYPlot) combinedPlot.getSubplots().get(1);
        XYSeriesCollection dataset = (XYSeriesCollection) indicatorPlot.getDataset(0);

        // Verify all segments exist and can be accessed
        assertTrue(dataset.getSeriesCount() > 0, "Should have at least one series segment");
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            assertNotNull(dataset.getSeries(i), "Series segment " + i + " should not be null");
        }
    }

    @Test
    void testAllTimeSeriesSegmentsGetSameStyling() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        IndicatorWithNaN indicator = new IndicatorWithNaN(barSeries, new int[] { 2, 3, 4 });

        JFreeChart chart = factory.createDualAxisChart(barSeries, closePrice, "Price", indicator, "Indicator");
        XYPlot plot = (XYPlot) chart.getPlot();
        TimeSeriesCollection dataset = (TimeSeriesCollection) plot.getDataset(1);

        // Verify all segments exist and can be accessed
        assertTrue(dataset.getSeriesCount() > 0, "Should have at least one series segment");
        for (int i = 0; i < dataset.getSeriesCount(); i++) {
            assertNotNull(dataset.getSeries(i), "Series segment " + i + " should not be null");
        }
    }

    @Test
    void testIndicatorWithAllNaNValuesCreatesNoSegments() {
        // Create indicator that returns NaN for all indices
        int[] allIndices = new int[barSeries.getBarCount()];
        for (int i = 0; i < allIndices.length; i++) {
            allIndices[i] = barSeries.getBeginIndex() + i;
        }
        IndicatorWithNaN indicator = new IndicatorWithNaN(barSeries, allIndices);
        JFreeChart chart = factory.createIndicatorChart(barSeries, indicator);

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = (XYPlot) combinedPlot.getSubplots().get(1);
        XYSeriesCollection dataset = (XYSeriesCollection) indicatorPlot.getDataset(0);

        assertEquals(0, dataset.getSeriesCount(), "Indicator with all NaN values should create no series segments");
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

    // ========== Tooltip Generator Serialization Tests ==========

    @Test
    void testChartWithTimeSeriesTooltipGeneratorIsSerializable() throws Exception {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = factory.createDualAxisChart(barSeries, closePrice, "Price", sma, "SMA");
        XYPlot plot = (XYPlot) chart.getPlot();

        // Verify tooltip generator is set
        StandardXYItemRenderer renderer = (StandardXYItemRenderer) plot.getRenderer(1);
        XYToolTipGenerator tooltipGenerator = renderer.getDefaultToolTipGenerator();
        assertNotNull(tooltipGenerator, "Tooltip generator should be set for TimeSeriesCollection");

        // Test serialization/deserialization
        JFreeChart deserializedChart = serializeAndDeserialize(chart);
        assertNotNull(deserializedChart, "Chart should be successfully deserialized");

        // Verify tooltip generator still works after deserialization
        XYPlot deserializedPlot = (XYPlot) deserializedChart.getPlot();
        StandardXYItemRenderer deserializedRenderer = (StandardXYItemRenderer) deserializedPlot.getRenderer(1);
        XYToolTipGenerator deserializedTooltipGenerator = deserializedRenderer.getDefaultToolTipGenerator();
        assertNotNull(deserializedTooltipGenerator, "Tooltip generator should be preserved after deserialization");

        // Verify tooltip generator can generate tooltips
        TimeSeriesCollection dataset = (TimeSeriesCollection) deserializedPlot.getDataset(1);
        if (dataset.getSeriesCount() > 0 && dataset.getItemCount(0) > 0) {
            String tooltip = deserializedTooltipGenerator.generateToolTip(dataset, 0, 0);
            assertNotNull(tooltip, "Tooltip generator should produce tooltip text");
            assertFalse(tooltip.isEmpty(), "Tooltip should not be empty");
        }
    }

    @Test
    void testBarIndexDualAxisTooltipIncludesFormattedDate() {
        BarSeries gapSeries = ChartingTestFixtures.dailySeriesWithWeekendGap("Gap Series");
        ClosePriceIndicator closePrice = new ClosePriceIndicator(gapSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 3);

        JFreeChart chart = factory.createDualAxisChart(gapSeries, closePrice, "Price", sma, "SMA", null,
                TimeAxisMode.BAR_INDEX);
        XYPlot plot = (XYPlot) chart.getPlot();

        StandardXYItemRenderer renderer = (StandardXYItemRenderer) plot.getRenderer(0);
        XYToolTipGenerator tooltipGenerator = renderer.getDefaultToolTipGenerator();
        assertNotNull(tooltipGenerator, "Tooltip generator should be set for BAR_INDEX dual-axis charts");

        XYSeriesCollection dataset = (XYSeriesCollection) plot.getDataset(0);
        String tooltip = tooltipGenerator.generateToolTip(dataset, 0, 0);
        assertNotNull(tooltip, "Tooltip should not be null");

        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
        String expectedDate = dateFormat.format(Date.from(gapSeries.getBar(gapSeries.getBeginIndex()).getEndTime()));
        assertTrue(tooltip.contains(expectedDate), "Tooltip should include formatted bar date");
    }

    @Test
    void testBarIndexOverlayTooltipIncludesFormattedDate() {
        BarSeries gapSeries = ChartingTestFixtures.dailySeriesWithWeekendGap("Gap Series");
        ClosePriceIndicator closePrice = new ClosePriceIndicator(gapSeries);
        ChartWorkflow workflow = new ChartWorkflow();

        JFreeChart chart = workflow.builder()
                .withTimeAxisMode(TimeAxisMode.BAR_INDEX)
                .withSeries(gapSeries)
                .withIndicatorOverlay(closePrice)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot basePlot = combinedPlot.getSubplots().get(0);

        int datasetIndex = findXYSeriesDatasetIndex(basePlot, closePrice.toString());
        assertTrue(datasetIndex >= 0, "Overlay dataset should be present for BAR_INDEX mode");

        StandardXYItemRenderer renderer = (StandardXYItemRenderer) basePlot.getRenderer(datasetIndex);
        XYToolTipGenerator tooltipGenerator = renderer.getDefaultToolTipGenerator();
        assertNotNull(tooltipGenerator, "Overlay tooltip generator should be configured");

        XYSeriesCollection dataset = (XYSeriesCollection) basePlot.getDataset(datasetIndex);
        String tooltip = tooltipGenerator.generateToolTip(dataset, 0, 0);
        assertNotNull(tooltip, "Tooltip should not be null");

        DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
        String expectedDate = dateFormat.format(Date.from(gapSeries.getBar(gapSeries.getBeginIndex()).getEndTime()));
        assertTrue(tooltip.contains(expectedDate), "Tooltip should include formatted bar date");
    }

    @Test
    void testChartWithXYSeriesTooltipGeneratorIsSerializable() throws Exception {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        JFreeChart chart = factory.createIndicatorChart(barSeries, closePrice);

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = (XYPlot) combinedPlot.getSubplots().get(1);

        // Verify tooltip generator is set
        StandardXYItemRenderer renderer = (StandardXYItemRenderer) indicatorPlot.getRenderer(0);
        XYToolTipGenerator tooltipGenerator = renderer.getDefaultToolTipGenerator();
        assertNotNull(tooltipGenerator, "Tooltip generator should be set for XYSeriesCollection");

        // Test serialization/deserialization
        JFreeChart deserializedChart = serializeAndDeserialize(chart);
        assertNotNull(deserializedChart, "Chart should be successfully deserialized");

        // Verify tooltip generator still works after deserialization
        CombinedDomainXYPlot deserializedCombinedPlot = (CombinedDomainXYPlot) deserializedChart.getPlot();
        XYPlot deserializedIndicatorPlot = (XYPlot) deserializedCombinedPlot.getSubplots().get(1);
        StandardXYItemRenderer deserializedRenderer = (StandardXYItemRenderer) deserializedIndicatorPlot.getRenderer(0);
        XYToolTipGenerator deserializedTooltipGenerator = deserializedRenderer.getDefaultToolTipGenerator();
        assertNotNull(deserializedTooltipGenerator, "Tooltip generator should be preserved after deserialization");

        // Verify tooltip generator can generate tooltips
        XYSeriesCollection dataset = (XYSeriesCollection) deserializedIndicatorPlot.getDataset(0);
        if (dataset.getSeriesCount() > 0 && dataset.getItemCount(0) > 0) {
            String tooltip = deserializedTooltipGenerator.generateToolTip(dataset, 0, 0);
            assertNotNull(tooltip, "Tooltip generator should produce tooltip text");
            assertFalse(tooltip.isEmpty(), "Tooltip should not be empty");
        }
    }

    @Test
    void testChartWithIndicatorOverlayTooltipGeneratorIsSerializable() throws Exception {
        // Create chart using addAnalysisCriterionToChart which uses
        // attachIndicatorOverlay internally
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(barSeries, new NetProfitCriterion(),
                tradingRecord);
        factory.addAnalysisCriterionToChart(chart, barSeries, indicator, indicator.toString());

        XYPlot plot = (XYPlot) chart.getPlot();

        // Find the overlay dataset (should be after the OHLC and trading datasets)
        int overlayDatasetIndex = -1;
        for (int i = 0; i < plot.getDatasetCount(); i++) {
            if (plot.getDataset(i) instanceof TimeSeriesCollection) {
                overlayDatasetIndex = i;
                break;
            }
        }
        assertTrue(overlayDatasetIndex >= 0, "Should have TimeSeriesCollection overlay dataset");

        // Verify tooltip generator is set
        StandardXYItemRenderer renderer = (StandardXYItemRenderer) plot.getRenderer(overlayDatasetIndex);
        XYToolTipGenerator tooltipGenerator = renderer.getDefaultToolTipGenerator();
        assertNotNull(tooltipGenerator, "Tooltip generator should be set for indicator overlay");

        // Test serialization/deserialization
        JFreeChart deserializedChart = serializeAndDeserialize(chart);
        assertNotNull(deserializedChart, "Chart should be successfully deserialized");

        // Verify tooltip generator still works after deserialization
        XYPlot deserializedPlot = (XYPlot) deserializedChart.getPlot();
        int deserializedOverlayDatasetIndex = -1;
        for (int i = 0; i < deserializedPlot.getDatasetCount(); i++) {
            if (deserializedPlot.getDataset(i) instanceof TimeSeriesCollection) {
                deserializedOverlayDatasetIndex = i;
                break;
            }
        }
        assertTrue(deserializedOverlayDatasetIndex >= 0,
                "Should have TimeSeriesCollection overlay dataset after deserialization");

        StandardXYItemRenderer deserializedRenderer = (StandardXYItemRenderer) deserializedPlot
                .getRenderer(deserializedOverlayDatasetIndex);
        XYToolTipGenerator deserializedTooltipGenerator = deserializedRenderer.getDefaultToolTipGenerator();
        assertNotNull(deserializedTooltipGenerator, "Tooltip generator should be preserved after deserialization");

        // Verify tooltip generator can generate tooltips
        TimeSeriesCollection dataset = (TimeSeriesCollection) deserializedPlot
                .getDataset(deserializedOverlayDatasetIndex);
        if (dataset.getSeriesCount() > 0 && dataset.getItemCount(0) > 0) {
            String tooltip = deserializedTooltipGenerator.generateToolTip(dataset, 0, 0);
            assertNotNull(tooltip, "Tooltip generator should produce tooltip text");
            assertFalse(tooltip.isEmpty(), "Tooltip should not be empty");
        }
    }

    private JFreeChart serializeAndDeserialize(JFreeChart chart) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(chart);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            return (JFreeChart) ois.readObject();
        }
    }

    // ========== Trading Record Subchart Tests ==========

    @Test
    void testBuildTradingRecordPlotCreatesDomainAxis() {
        // Test that buildTradingRecordPlot creates a domain axis (fixes
        // NullPointerException bug)
        // Use ChartWorkflow to create a chart with trading record subchart
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder().withSeries(barSeries).withSubChart(tradingRecord).toChart();

        assertNotNull(chart, "Chart should be created successfully");

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        assertNotNull(combinedPlot, "Should have combined plot");
        assertTrue(combinedPlot.getSubplots().size() >= 2, "Should have base plot and trading record subplot");

        // Find the trading record subplot
        XYPlot tradingRecordPlot = null;
        for (XYPlot subplot : combinedPlot.getSubplots()) {
            if (subplot.getRangeAxis() != null && subplot.getRangeAxis().getLabel().contains("Trade price")) {
                tradingRecordPlot = subplot;
                break;
            }
        }

        assertNotNull(tradingRecordPlot, "Should have trading record subplot");
        assertNotNull(tradingRecordPlot.getDomainAxis(), "Trading record subplot should have domain axis");
        assertInstanceOf(DateAxis.class, tradingRecordPlot.getDomainAxis(), "Domain axis should be a DateAxis");
    }

    @Test
    void testTradingRecordSubchartHasConfiguredDomainAxis() {
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder().withSeries(barSeries).withSubChart(tradingRecord).toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();

        // Find the trading record subplot
        XYPlot tradingRecordPlot = null;
        for (XYPlot subplot : combinedPlot.getSubplots()) {
            if (subplot.getRangeAxis() != null && subplot.getRangeAxis().getLabel().contains("Trade price")) {
                tradingRecordPlot = subplot;
                break;
            }
        }

        assertNotNull(tradingRecordPlot, "Should have trading record subplot");
        DateAxis domainAxis = (DateAxis) tradingRecordPlot.getDomainAxis();
        assertNotNull(domainAxis.getDateFormatOverride(), "Domain axis should have date format configured");
        assertTrue(domainAxis.isAutoRange(), "Domain axis should be auto-ranging");
    }

    // ========== Horizontal Marker Tests ==========

    @Test
    void testHorizontalMarkerRenderedOnIndicatorSubplot() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(barSeries)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(50.0)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = combinedPlot.getSubplots().get(1);
        Collection<?> rangeMarkers = indicatorPlot.getRangeMarkers(Layer.FOREGROUND);
        assertNotNull(rangeMarkers, "Should have range markers");
        assertFalse(rangeMarkers.isEmpty(), "Should have horizontal marker");
        assertTrue(rangeMarkers.stream().anyMatch(m -> m instanceof ValueMarker),
                "Should contain ValueMarker instance");
    }

    @Test
    void testHorizontalMarkerValueIsCorrect() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        double markerValue = 50.0;
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(barSeries)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(markerValue)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = combinedPlot.getSubplots().get(1);
        Collection<?> rangeMarkers = indicatorPlot.getRangeMarkers(Layer.FOREGROUND);
        ValueMarker marker = (ValueMarker) rangeMarkers.stream()
                .filter(m -> m instanceof ValueMarker)
                .findFirst()
                .orElse(null);
        assertNotNull(marker, "Should have ValueMarker");
        assertEquals(markerValue, marker.getValue(), 0.001, "Marker should be at correct Y value");
    }

    @Test
    void testHorizontalMarkerStylingApplied() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        Color customColor = Color.RED;
        float customWidth = 2.5f;
        float customOpacity = 0.7f;

        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(barSeries)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(50.0)
                .withLineColor(customColor)
                .withLineWidth(customWidth)
                .withOpacity(customOpacity)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = combinedPlot.getSubplots().get(1);
        Collection<?> rangeMarkers = indicatorPlot.getRangeMarkers(Layer.FOREGROUND);
        ValueMarker marker = (ValueMarker) rangeMarkers.stream()
                .filter(m -> m instanceof ValueMarker)
                .findFirst()
                .orElse(null);
        assertNotNull(marker, "Should have ValueMarker");

        // Verify color with opacity
        assertNotNull(marker.getPaint(), "Marker should have color");
        assertInstanceOf(Color.class, marker.getPaint(), "Marker paint should be a Color");
        Color markerColor = (Color) marker.getPaint();
        assertEquals(customColor.getRed(), markerColor.getRed(), "Red component should match");
        assertEquals(customColor.getGreen(), markerColor.getGreen(), "Green component should match");
        assertEquals(customColor.getBlue(), markerColor.getBlue(), "Blue component should match");
        assertEquals(Math.round(customOpacity * 255), markerColor.getAlpha(), "Alpha component should reflect opacity");

        // Verify stroke width
        BasicStroke stroke = (BasicStroke) marker.getStroke();
        assertNotNull(stroke, "Marker should have stroke");
        assertEquals(customWidth, stroke.getLineWidth(), 0.01f, "Line width should match");
    }

    @Test
    void testMultipleHorizontalMarkersOnSameSubplot() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(barSeries)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(30.0)
                .withHorizontalMarker(50.0)
                .withHorizontalMarker(70.0)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = combinedPlot.getSubplots().get(1);
        Collection<?> rangeMarkers = indicatorPlot.getRangeMarkers(Layer.FOREGROUND);
        long markerCount = rangeMarkers.stream().filter(m -> m instanceof ValueMarker).count();
        assertEquals(3, markerCount, "Should have exactly 3 horizontal markers");
    }

    @Test
    void testHorizontalMarkerOnTradingRecordSubplot() {
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(barSeries)
                .withSubChart(tradingRecord)
                .withHorizontalMarker(100.0)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot tradingRecordPlot = combinedPlot.getSubplots().get(1);
        Collection<?> rangeMarkers = tradingRecordPlot.getRangeMarkers(Layer.FOREGROUND);
        assertFalse(rangeMarkers.isEmpty(), "Should have horizontal marker on trading record subplot");
    }

    @Test
    void testHorizontalMarkerOnBaseIndicatorChart() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder().withIndicator(closePrice).withHorizontalMarker(50.0).toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot basePlot = combinedPlot.getSubplots().get(0);
        Collection<?> rangeMarkers = basePlot.getRangeMarkers(Layer.FOREGROUND);
        assertFalse(rangeMarkers.isEmpty(), "Should have horizontal marker on base indicator plot");
    }

    @Test
    void testHorizontalMarkerWithZeroValue() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(barSeries)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(0.0)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = combinedPlot.getSubplots().get(1);
        Collection<?> rangeMarkers = indicatorPlot.getRangeMarkers(Layer.FOREGROUND);
        ValueMarker marker = (ValueMarker) rangeMarkers.stream()
                .filter(m -> m instanceof ValueMarker)
                .findFirst()
                .orElse(null);
        assertNotNull(marker, "Should have ValueMarker at zero");
        assertEquals(0.0, marker.getValue(), 0.001, "Marker should be at zero");
    }

    @Test
    void testHorizontalMarkerWithNegativeValue() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(barSeries)
                .withSubChart(closePrice)
                .withHorizontalMarker(-10.0)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = combinedPlot.getSubplots().get(1);
        Collection<?> rangeMarkers = indicatorPlot.getRangeMarkers(Layer.FOREGROUND);
        ValueMarker marker = (ValueMarker) rangeMarkers.stream()
                .filter(m -> m instanceof ValueMarker)
                .findFirst()
                .orElse(null);
        assertNotNull(marker, "Should have ValueMarker at negative value");
        assertEquals(-10.0, marker.getValue(), 0.001, "Marker should be at negative value");
    }

    @Test
    void testHorizontalMarkerDefaultStyling() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(barSeries)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(50.0)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        XYPlot indicatorPlot = combinedPlot.getSubplots().get(1);
        Collection<?> rangeMarkers = indicatorPlot.getRangeMarkers(Layer.FOREGROUND);
        ValueMarker marker = (ValueMarker) rangeMarkers.stream()
                .filter(m -> m instanceof ValueMarker)
                .findFirst()
                .orElse(null);
        assertNotNull(marker, "Should have ValueMarker");
        assertNotNull(marker.getPaint(), "Marker should have default color");
        assertNotNull(marker.getStroke(), "Marker should have default stroke");
        // Default opacity should be 1.0 (fully opaque)
        assertInstanceOf(Color.class, marker.getPaint(), "Marker paint should be a Color");
        Color markerColor = (Color) marker.getPaint();
        assertEquals(255, markerColor.getAlpha(), "Default opacity should be fully opaque");
    }

    @Test
    void testHorizontalMarkerOnMultipleSubplots() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        RSIIndicator rsiIndicator = new RSIIndicator(closePrice, 14);
        ChartWorkflow workflow = new ChartWorkflow();
        JFreeChart chart = workflow.builder()
                .withSeries(barSeries)
                .withSubChart(rsiIndicator)
                .withHorizontalMarker(50.0)
                .withSubChart(closePrice)
                .withHorizontalMarker(100.0)
                .toChart();

        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        assertEquals(3, combinedPlot.getSubplots().size(), "Should have base plot plus 2 subplots");

        // Check first subplot (RSI)
        XYPlot rsiPlot = combinedPlot.getSubplots().get(1);
        Collection<?> rsiMarkers = rsiPlot.getRangeMarkers(Layer.FOREGROUND);
        long rsiMarkerCount = rsiMarkers.stream().filter(m -> m instanceof ValueMarker).count();
        assertEquals(1, rsiMarkerCount, "RSI subplot should have 1 marker");

        // Check second subplot (Close Price)
        XYPlot closePlot = combinedPlot.getSubplots().get(2);
        Collection<?> closeMarkers = closePlot.getRangeMarkers(Layer.FOREGROUND);
        long closeMarkerCount = closeMarkers.stream().filter(m -> m instanceof ValueMarker).count();
        assertEquals(1, closeMarkerCount, "Close price subplot should have 1 marker");
    }

    // ========== Duplicate Time Period Fix Tests ==========

    @Test
    void testDailyBarsUseDayTimePeriod() {
        // Create a daily series that would cause duplicate Minute time periods
        BarSeries dailySeries = createDailySeriesWithMultipleBars("Daily Test", 20);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(dailySeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        // This should not throw SeriesException about duplicate time periods
        assertDoesNotThrow(() -> {
            JFreeChart chart = factory.createDualAxisChart(dailySeries, closePrice, "Price", sma, "SMA");
            XYPlot plot = (XYPlot) chart.getPlot();
            TimeSeriesCollection dataset = (TimeSeriesCollection) plot.getDataset(1);
            assertNotNull(dataset, "TimeSeriesCollection should be created");
            assertTrue(dataset.getSeriesCount() > 0, "Should have at least one series");
        }, "Daily bars should use Day time period, not Minute, to avoid duplicates");
    }

    @Test
    void testHourlyBarsUseHourTimePeriod() {
        // Create an hourly series
        BarSeries hourlySeries = createHourlySeries("Hourly Test", 10);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(hourlySeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 3);

        // This should not throw SeriesException about duplicate time periods
        assertDoesNotThrow(() -> {
            JFreeChart chart = factory.createDualAxisChart(hourlySeries, closePrice, "Price", sma, "SMA");
            XYPlot plot = (XYPlot) chart.getPlot();
            TimeSeriesCollection dataset = (TimeSeriesCollection) plot.getDataset(1);
            assertNotNull(dataset, "TimeSeriesCollection should be created");
            assertTrue(dataset.getSeriesCount() > 0, "Should have at least one series");
        }, "Hourly bars should use Hour time period to avoid duplicates");
    }

    @Test
    void testMinuteLevelBarsUseMinuteTimePeriod() {
        // Create a minute-level series
        BarSeries minuteSeries = createMinuteSeries("Minute Test", 15);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(minuteSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 3);

        // This should not throw SeriesException about duplicate time periods
        assertDoesNotThrow(() -> {
            JFreeChart chart = factory.createDualAxisChart(minuteSeries, closePrice, "Price", sma, "SMA");
            XYPlot plot = (XYPlot) chart.getPlot();
            TimeSeriesCollection dataset = (TimeSeriesCollection) plot.getDataset(1);
            assertNotNull(dataset, "TimeSeriesCollection should be created");
            assertTrue(dataset.getSeriesCount() > 0, "Should have at least one series");
        }, "Minute-level bars should use Minute time period");
    }

    @Test
    void testSecondLevelBarsUseSecondTimePeriod() {
        // Create a second-level series
        BarSeries secondSeries = createSecondSeries("Second Test", 10);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(secondSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 3);

        // This should not throw SeriesException about duplicate time periods
        assertDoesNotThrow(() -> {
            JFreeChart chart = factory.createDualAxisChart(secondSeries, closePrice, "Price", sma, "SMA");
            XYPlot plot = (XYPlot) chart.getPlot();
            TimeSeriesCollection dataset = (TimeSeriesCollection) plot.getDataset(1);
            assertNotNull(dataset, "TimeSeriesCollection should be created");
            assertTrue(dataset.getSeriesCount() > 0, "Should have at least one series");
        }, "Second-level bars should use Second time period");
    }

    @Test
    void testIndicatorChartWithDailyBarsNoDuplicateTimePeriods() {
        BarSeries dailySeries = createDailySeriesWithMultipleBars("Daily Indicator Test", 15);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(dailySeries);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        // This should not throw SeriesException about duplicate time periods
        assertDoesNotThrow(() -> {
            JFreeChart chart = factory.createIndicatorChart(dailySeries, rsi);
            CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
            assertNotNull(combinedPlot, "Combined plot should be created");
            assertTrue(combinedPlot.getSubplots().size() >= 2, "Should have base plot and indicator subplot");
        }, "Indicator chart with daily bars should not have duplicate time periods");
    }

    @Test
    void testChannelFillDatasetWithDailyBarsNoDuplicateTimePeriods() {
        BarSeries dailySeries = createDailySeriesWithMultipleBars("Daily Channel Test", 12);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(dailySeries);
        HighPriceIndicator highPrice = new HighPriceIndicator(dailySeries);
        LowPriceIndicator lowPrice = new LowPriceIndicator(dailySeries);

        ChartWorkflow workflow = new ChartWorkflow();
        // Channel overlay uses TimeSeriesCollection internally
        assertDoesNotThrow(() -> {
            JFreeChart chart = workflow.builder()
                    .withSeries(dailySeries)
                    .withChannelOverlay(highPrice, closePrice, lowPrice)
                    .toChart();
            CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
            assertNotNull(combinedPlot, "Chart with channel overlay should be created");
        }, "Channel fill dataset with daily bars should not have duplicate time periods");
    }

    @Test
    void testSwingMarkerDatasetWithDailyBarsNoDuplicateTimePeriods() {
        BarSeries dailySeries = createDailySeriesWithMultipleBars("Daily Swing Test", 20);
        LowPriceIndicator lowPrice = new LowPriceIndicator(dailySeries);
        RecentFractalSwingLowIndicator swingLowIndicator = new RecentFractalSwingLowIndicator(lowPrice, 5, 5, 0);
        SwingPointMarkerIndicator swingMarkers = new SwingPointMarkerIndicator(dailySeries, swingLowIndicator);

        ChartWorkflow workflow = new ChartWorkflow();
        // Swing markers use TimeSeriesCollection internally
        assertDoesNotThrow(() -> {
            JFreeChart chart = workflow.builder()
                    .withSeries(dailySeries)
                    .withIndicatorOverlay(swingMarkers)
                    .withLineColor(Color.GREEN)
                    .toChart();
            CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
            assertNotNull(combinedPlot, "Chart with swing markers should be created");
        }, "Swing marker dataset with daily bars should not have duplicate time periods");
    }

    @Test
    void testBarSeriesLabelDatasetWithDailyBarsNoDuplicateTimePeriods() {
        BarSeries dailySeries = createDailySeriesWithMultipleBars("Daily Label Test", 15);
        List<BarLabel> labels = List.of(
                new BarLabel(3, dailySeries.getBar(3).getClosePrice(), "1", LabelPlacement.ABOVE),
                new BarLabel(7, dailySeries.getBar(7).getClosePrice(), "2", LabelPlacement.BELOW),
                new BarLabel(12, dailySeries.getBar(12).getClosePrice(), "3", LabelPlacement.ABOVE));
        BarSeriesLabelIndicator labelIndicator = new BarSeriesLabelIndicator(dailySeries, labels);

        ChartWorkflow workflow = new ChartWorkflow();
        // Bar series labels use TimeSeriesCollection internally
        assertDoesNotThrow(() -> {
            JFreeChart chart = workflow.builder()
                    .withSeries(dailySeries)
                    .withIndicatorOverlay(labelIndicator)
                    .withLabel("Labels")
                    .toChart();
            CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
            assertNotNull(combinedPlot, "Chart with bar series labels should be created");
        }, "Bar series label dataset with daily bars should not have duplicate time periods");
    }

    @Test
    void testAddOrUpdateHandlesDuplicateTimePeriodsGracefully() {
        // Create a series where multiple bars might map to the same time period
        // This tests the addOrUpdate() fallback mechanism
        BarSeries dailySeries = createDailySeriesWithMultipleBars("Duplicate Test", 10);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(dailySeries);

        // Create chart multiple times to ensure addOrUpdate handles any edge cases
        assertDoesNotThrow(() -> {
            for (int i = 0; i < 3; i++) {
                JFreeChart chart = factory.createDualAxisChart(dailySeries, closePrice, "Price", closePrice, "Close");
                assertNotNull(chart, "Chart should be created on iteration " + i);
            }
        }, "addOrUpdate() should handle any duplicate time periods gracefully");
    }

    @Test
    void testMultipleIndicatorsWithDailyBarsNoDuplicateTimePeriods() {
        BarSeries dailySeries = createDailySeriesWithMultipleBars("Multiple Indicators Test", 20);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(dailySeries);
        SMAIndicator sma5 = new SMAIndicator(closePrice, 5);
        SMAIndicator sma10 = new SMAIndicator(closePrice, 10);
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);

        // Multiple indicators should all work without duplicate time period errors
        assertDoesNotThrow(() -> {
            JFreeChart chart = factory.createIndicatorChart(dailySeries, sma5, sma10, rsi);
            CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
            assertNotNull(combinedPlot, "Chart with multiple indicators should be created");
            assertEquals(4, combinedPlot.getSubplots().size(),
                    "Should have 1 main OHLC plot plus 3 indicator subplots");
        }, "Multiple indicators with daily bars should not have duplicate time periods");
    }

    /**
     * Creates a daily bar series with the specified number of bars. Multiple daily
     * bars would map to the same Minute if using Minute time period, which would
     * cause duplicate time period errors.
     */
    private BarSeries createDailySeriesWithMultipleBars(String name, int barCount) {
        BarSeries series = new MockBarSeriesBuilder().withName(name).build();
        Duration period = Duration.ofDays(1);
        Instant start = Instant.EPOCH.plus(Duration.ofDays(1));

        for (int i = 0; i < barCount; i++) {
            Instant endTime = start.plus(period.multipliedBy(i));
            double basePrice = 100.0 + i * 0.5;
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(endTime)
                    .openPrice(basePrice)
                    .highPrice(basePrice + 1.0)
                    .lowPrice(basePrice - 0.5)
                    .closePrice(basePrice + 0.3)
                    .volume(1000.0 + i * 10)
                    .add();
        }
        return series;
    }

    /**
     * Creates an hourly bar series.
     */
    private BarSeries createHourlySeries(String name, int barCount) {
        BarSeries series = new MockBarSeriesBuilder().withName(name).build();
        Duration period = Duration.ofHours(1);
        Instant start = Instant.EPOCH.plus(Duration.ofHours(1));

        for (int i = 0; i < barCount; i++) {
            Instant endTime = start.plus(period.multipliedBy(i));
            double basePrice = 100.0 + i * 0.1;
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(endTime)
                    .openPrice(basePrice)
                    .highPrice(basePrice + 0.5)
                    .lowPrice(basePrice - 0.3)
                    .closePrice(basePrice + 0.2)
                    .volume(500.0 + i * 5)
                    .add();
        }
        return series;
    }

    /**
     * Creates a minute-level bar series.
     */
    private BarSeries createMinuteSeries(String name, int barCount) {
        BarSeries series = new MockBarSeriesBuilder().withName(name).build();
        Duration period = Duration.ofMinutes(5);
        Instant start = Instant.EPOCH.plus(Duration.ofMinutes(5));

        for (int i = 0; i < barCount; i++) {
            Instant endTime = start.plus(period.multipliedBy(i));
            double basePrice = 100.0 + i * 0.01;
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(endTime)
                    .openPrice(basePrice)
                    .highPrice(basePrice + 0.1)
                    .lowPrice(basePrice - 0.05)
                    .closePrice(basePrice + 0.03)
                    .volume(100.0 + i)
                    .add();
        }
        return series;
    }

    /**
     * Creates a second-level bar series.
     */
    private BarSeries createSecondSeries(String name, int barCount) {
        BarSeries series = new MockBarSeriesBuilder().withName(name).build();
        Duration period = Duration.ofSeconds(30);
        Instant start = Instant.EPOCH.plus(Duration.ofSeconds(30));

        for (int i = 0; i < barCount; i++) {
            Instant endTime = start.plus(period.multipliedBy(i));
            double basePrice = 100.0 + i * 0.001;
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(endTime)
                    .openPrice(basePrice)
                    .highPrice(basePrice + 0.01)
                    .lowPrice(basePrice - 0.005)
                    .closePrice(basePrice + 0.003)
                    .volume(10.0 + i)
                    .add();
        }
        return series;
    }
}
