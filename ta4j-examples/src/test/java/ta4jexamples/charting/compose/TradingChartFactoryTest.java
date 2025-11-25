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

import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.ui.TextAnchor;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYSeriesCollection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

import ta4jexamples.charting.AnalysisCriterionIndicator;
import ta4jexamples.charting.ChartingTestFixtures;
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
}
