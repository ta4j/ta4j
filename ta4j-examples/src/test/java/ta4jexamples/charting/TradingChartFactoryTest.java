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
package ta4jexamples.charting;

import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.Layer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;

import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

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
    void testCreateAnalysisChart() {
        JFreeChart chart = factory.createAnalysisChart(barSeries, AnalysisType.MOVING_AVERAGE_10,
                AnalysisType.MOVING_AVERAGE_20);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getTitle(), "Chart should have a title");
    }

    @Test
    void testCreateAnalysisChartWithSingleType() {
        JFreeChart chart = factory.createAnalysisChart(barSeries, AnalysisType.MOVING_AVERAGE_50);

        assertNotNull(chart, "Chart should not be null");
    }

    @Test
    void testCreateAnalysisChartWithNoTypes() {
        JFreeChart chart = factory.createAnalysisChart(barSeries);

        assertNotNull(chart, "Chart should be created even without analysis types");
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
        org.jfree.chart.axis.DateAxis domainAxis = (org.jfree.chart.axis.DateAxis) plot.getDomainAxis();

        assertNotNull(domainAxis, "Domain axis should be a DateAxis");
        assertTrue(domainAxis.isAutoRange(), "Domain axis should be auto-ranging");
    }

    @Test
    void testChartRangeAxisConfiguration() {
        JFreeChart chart = factory.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        XYPlot plot = (XYPlot) chart.getPlot();
        org.jfree.chart.axis.NumberAxis rangeAxis = (org.jfree.chart.axis.NumberAxis) plot.getRangeAxis();

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

}
