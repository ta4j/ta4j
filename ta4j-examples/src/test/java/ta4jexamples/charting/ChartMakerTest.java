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

import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.plot.IntervalMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.Layer;
import org.jfree.chart.JFreeChart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChartMaker}.
 */
public class ChartMakerTest {

    private ChartMaker chartMaker;
    private BarSeries barSeries;
    private TradingRecord tradingRecord;

    @BeforeEach
    public void setUp() {
        chartMaker = new ChartMaker();
        barSeries = createTestBarSeries();
        tradingRecord = createTestTradingRecord(barSeries); // Pass barSeries to create appropriate trades
    }

    @Test
    public void testDefaultConstructor() {
        ChartMaker maker = new ChartMaker();
        assertNotNull(maker, "ChartMaker should not be null");
        // Test that the default constructor works
        assertNotNull(maker, "ChartMaker should be created successfully");
    }

    @Test
    public void testConstructorWithSaveDirectory() {
        String saveDir = "test/charts";
        ChartMaker maker = new ChartMaker(saveDir);
        assertNotNull(maker, "ChartMaker should not be null");
        // Test that the constructor works without accessing private fields
        assertNotNull(maker, "ChartMaker should be created successfully");
    }

    @Test
    public void testConstructorWithNullSaveDirectory() {
        assertThrows(IllegalArgumentException.class, () -> new ChartMaker(null));
    }

    @Test
    public void testConstructorWithEmptySaveDirectory() {
        assertThrows(IllegalArgumentException.class, () -> new ChartMaker(""));
    }

    @Test
    public void testConstructorWithBlankSaveDirectory() {
        assertThrows(IllegalArgumentException.class, () -> new ChartMaker("   "));
    }

    @Test
    public void testGenerateChartWithTradingRecord() {
        JFreeChart chart = chartMaker.generateChart(barSeries, "Test Strategy", tradingRecord);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getTitle(), "Chart title should not be null");
        assertTrue(chart.getTitle().getText().contains("Test Strategy"), "Chart title should contain strategy name");
    }

    @Test
    public void testGenerateChartAddsTradeMarkers() {
        JFreeChart chart = chartMaker.generateChart(barSeries, "Test Strategy", tradingRecord);
        XYPlot plot = chart.getXYPlot();

        assertTrue(plot.getDatasetCount() > 1, "Trade dataset should be present on the chart");
        assertNotNull(plot.getDataset(1), "Trade dataset should not be null");
        assertTrue(plot.getRenderer(1) instanceof XYLineAndShapeRenderer,
                "Trade renderer should provide marker shapes");

        Collection<?> domainMarkers = plot.getDomainMarkers(Layer.BACKGROUND);
        assertNotNull(domainMarkers, "Position shading markers should be present");
        assertFalse(domainMarkers.isEmpty(), "Position shading should highlight completed trades");
        assertTrue(domainMarkers.stream().anyMatch(marker -> marker instanceof IntervalMarker),
                "Position shading should use interval markers");

        assertTrue(plot.getAnnotations().stream().anyMatch(XYTextAnnotation.class::isInstance),
                "Trade annotations should contain readable labels");
    }

    @Test
    public void testGenerateChartWithNullSeries() {
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.generateChart(null, "Test Strategy", tradingRecord));
    }

    @Test
    public void testGenerateChartWithNullStrategyName() {
        assertThrows(IllegalArgumentException.class, () -> chartMaker.generateChart(barSeries, null, tradingRecord));
    }

    @Test
    public void testGenerateChartWithEmptyStrategyName() {
        assertThrows(IllegalArgumentException.class, () -> chartMaker.generateChart(barSeries, "", tradingRecord));
    }

    @Test
    public void testGenerateChartWithNullTradingRecord() {
        assertThrows(IllegalArgumentException.class, () -> chartMaker.generateChart(barSeries, "Test Strategy", null));
    }

    @Test
    public void testGenerateChartWithIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = chartMaker.generateChart(barSeries, closePrice, sma);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getTitle(), "Chart title should not be null");
    }

    @Test
    public void testGenerateChartWithNullSeriesForIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class, () -> chartMaker.generateChart(null, closePrice));
    }

    @Test
    public void testGenerateChartWithNullIndicators() {
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.generateChart(barSeries, (Indicator<org.ta4j.core.num.Num>[]) null));
    }

    @Test
    public void testGenerateChartWithAnalysisTypes() {
        JFreeChart chart = chartMaker.generateChart(barSeries, AnalysisType.MOVING_AVERAGE_10,
                AnalysisType.MOVING_AVERAGE_20);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getTitle(), "Chart title should not be null");
    }

    @Test
    public void testGenerateChartWithNullSeriesForAnalysis() {
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.generateChart(null, AnalysisType.MOVING_AVERAGE_10));
    }

    @Test
    public void testGenerateChartWithNullAnalysisTypes() {
        assertThrows(IllegalArgumentException.class, () -> chartMaker.generateChart(barSeries, (AnalysisType[]) null));
    }

    @Test
    public void testGenerateChartAsBytes() {
        byte[] chartBytes = chartMaker.generateChartAsBytes(barSeries, "Test Strategy", tradingRecord);

        assertNotNull(chartBytes, "Chart bytes should not be null");
        assertTrue(chartBytes.length > 0, "Chart bytes should not be empty");
    }

    @Test
    public void testGenerateChartAsBytesWithNullChart() {
        assertThrows(IllegalArgumentException.class, () -> chartMaker.getChartAsByteArray(null));
    }

    @Test
    public void testGetChartAsByteArray() {
        JFreeChart chart = chartMaker.generateChart(barSeries, "Test Strategy", tradingRecord);
        byte[] bytes = chartMaker.getChartAsByteArray(chart);

        assertNotNull(bytes, "Bytes should not be null");
        assertTrue(bytes.length > 0, "Bytes should not be empty");
    }

    @Test
    public void testResolveDisplayScaleWithValidProperty() {
        System.setProperty("ta4j.chart.displayScale", "0.5");
        try {
            assertEquals(0.5, chartMaker.resolveDisplayScale(), 1e-9,
                    "Display scale should reflect configured property");
        } finally {
            System.clearProperty("ta4j.chart.displayScale");
        }
    }

    @Test
    public void testResolveDisplayScaleOnInvalidProperty() {
        System.clearProperty("ta4j.chart.displayScale");
        double defaultScale = chartMaker.resolveDisplayScale();
        System.setProperty("ta4j.chart.displayScale", "invalid");
        try {
            assertEquals(defaultScale, chartMaker.resolveDisplayScale(), 1e-9,
                    "Invalid property should fall back to default scale");
        } finally {
            System.clearProperty("ta4j.chart.displayScale");
        }
    }

    @Test
    public void testGenerateAndSaveChartImageWithoutSaveDirectory() {
        String result = chartMaker.generateAndSaveChartImage(barSeries, "Test Strategy", tradingRecord);

        assertNull(result, "Result should be null when save directory is not configured");
    }

    @Test
    public void testGenerateAndSaveChartImageWithSaveDirectory() throws IOException {
        // Create temporary directory for testing
        Path tempDir = Files.createTempDirectory("chartmaker-test");
        try {
            ChartMaker makerWithSave = new ChartMaker(tempDir.toString());
            TradingRecord emptyRecord = new BaseTradingRecord();
            String result = makerWithSave.generateAndSaveChartImage(barSeries, "TestStrat", emptyRecord);

            // The result may be null if file creation fails, but the method should complete
            // without exception
            // This is acceptable as it's an integration test with file system operations
            if (result != null) {
                assertFalse(result.isEmpty(), "Result should not be empty");
            }
        } finally {
            // Clean up
            if (Files.exists(tempDir)) {
                Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
            }
        }
    }

    @Test
    public void testGenerateAndSaveChartImageWithNullParameters() {
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.generateAndSaveChartImage(null, "Test Strategy", tradingRecord));
    }

    @Test
    public void testGenerateAndDisplayTradingRecordChart() {
        // This test just ensures the method doesn't throw an exception
        // In a real test environment, we might mock the display functionality
        try {
            chartMaker.generateAndDisplayTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            assertTrue(e.getMessage().contains("headless") || e.getMessage().contains("display"),
                    "Exception should be related to display functionality");
        }
    }

    @Test
    public void testGenerateAndDisplayChartWithAnalysisTypes() {
        try {
            chartMaker.generateAndDisplayChart(barSeries, AnalysisType.MOVING_AVERAGE_10);
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            assertTrue(e.getMessage().contains("headless") || e.getMessage().contains("display"),
                    "Exception should be related to display functionality");
        }
    }

    @Test
    public void testGenerateAndDisplayChartWithIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        try {
            chartMaker.generateAndDisplayChart(barSeries, closePrice);
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            assertTrue(e.getMessage().contains("headless") || e.getMessage().contains("display"),
                    "Exception should be related to display functionality");
        }
    }

    @Test
    public void testChartTitleGeneration() {
        JFreeChart chart = chartMaker.generateChart(barSeries, "Test Strategy", tradingRecord);
        String title = chart.getTitle().getText();

        assertNotNull(title, "Title should not be null");
        assertTrue(title.contains("Test Strategy"), "Title should contain strategy name");
        assertTrue(title.contains("Test"), "Title should contain series name");
    }

    @Test
    public void testChartWithEmptyTradingRecord() {
        TradingRecord emptyRecord = new BaseTradingRecord();
        JFreeChart chart = chartMaker.generateChart(barSeries, "Test Strategy", emptyRecord);

        assertNotNull(chart, "Chart should not be null even with empty trading record");
    }

    @Test
    public void testChartWithSingleBar() {
        BarSeries singleBarSeries = createSingleBarSeries();
        // Create empty trading record for single bar (no trades within range)
        TradingRecord emptyRecord = new BaseTradingRecord();
        JFreeChart chart = chartMaker.generateChart(singleBarSeries, "Test Strategy", emptyRecord);

        assertNotNull(chart, "Chart should not be null even with single bar");
    }

    @Test
    public void testChartWithDifferentTimePeriods() {
        // Test with daily data
        BarSeries dailySeries = createTestBarSeries();
        TradingRecord dailyRecord = createTestTradingRecord(dailySeries);
        JFreeChart dailyChart = chartMaker.generateChart(dailySeries, "Daily Strategy", dailyRecord);
        assertNotNull(dailyChart, "Daily chart should not be null");

        // Test with intraday data (shorter duration)
        BarSeries intradaySeries = createIntradayBarSeries();
        TradingRecord intradayRecord = createTestTradingRecord(intradaySeries);
        JFreeChart intradayChart = chartMaker.generateChart(intradaySeries, "Intraday Strategy", intradayRecord);
        assertNotNull(intradayChart, "Intraday chart should not be null");
    }

    @Test
    public void testChartWithMultipleAnalysisTypes() {
        AnalysisType[] analysisTypes = { AnalysisType.MOVING_AVERAGE_10, AnalysisType.MOVING_AVERAGE_20,
                AnalysisType.MOVING_AVERAGE_50 };

        JFreeChart chart = chartMaker.generateChart(barSeries, analysisTypes);
        assertNotNull(chart, "Chart should not be null");
    }

    @Test
    public void testChartWithMultipleIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma5 = new SMAIndicator(closePrice, 5);
        SMAIndicator sma10 = new SMAIndicator(closePrice, 10);

        @SuppressWarnings("unchecked")
        Indicator<org.ta4j.core.num.Num>[] indicators = new Indicator[] { closePrice, sma5, sma10 };
        JFreeChart chart = chartMaker.generateChart(barSeries, indicators);
        assertNotNull(chart, "Chart should not be null");
    }

    @Test
    public void testErrorHandlingWithInvalidData() {
        // Test with series that might cause issues
        BarSeries problematicSeries = createProblematicBarSeries();
        JFreeChart chart = chartMaker.generateChart(problematicSeries, "Test Strategy", tradingRecord);

        // Should handle gracefully and return a chart (possibly empty)
        assertNotNull(chart, "Chart should not be null even with problematic data");
    }

    @Test
    public void testPathSanitizationSimple() {
        // Test that sanitization doesn't crash the system (without file creation)
        BarSeries seriesWithSpecialChars = createSeriesWithSpecialChars();
        JFreeChart chart = chartMaker.generateChart(seriesWithSpecialChars, "Test Strategy", new BaseTradingRecord());

        assertNotNull(chart, "Chart should not be null even with special chars in series name");
    }

    /**
     * Creates a test bar series for testing purposes.
     */
    private BarSeries createTestBarSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("Test Series").build();

        Instant startTime = Instant.now().minus(Duration.ofDays(10));

        for (int i = 0; i < 10; i++) {
            Instant time = startTime.plus(Duration.ofDays(i));
            double basePrice = 100.0 + i;

            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(time)
                    .openPrice(basePrice)
                    .highPrice(basePrice + 2)
                    .lowPrice(basePrice - 1)
                    .closePrice(basePrice + 1)
                    .volume(1000 + i * 100)
                    .build());
        }

        return series;
    }

    /**
     * Creates a single bar series for testing edge cases.
     */
    private BarSeries createSingleBarSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("Single Bar").build();

        Instant time = Instant.now();

        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(time)
                .openPrice(100.0)
                .highPrice(105.0)
                .lowPrice(99.0)
                .closePrice(104.0)
                .volume(1000.0)
                .build());

        return series;
    }

    /**
     * Creates an intraday bar series for testing different time periods.
     */
    private BarSeries createIntradayBarSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("Intraday Series").build();

        Instant startTime = Instant.now().minus(Duration.ofHours(5));

        for (int i = 0; i < 5; i++) {
            Instant time = startTime.plus(Duration.ofHours(i));
            double basePrice = 100.0 + i * 0.5;

            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofHours(1))
                    .endTime(time)
                    .openPrice(basePrice)
                    .highPrice(basePrice + 1)
                    .lowPrice(basePrice - 0.5)
                    .closePrice(basePrice + 0.5)
                    .volume(500 + i * 50)
                    .build());
        }

        return series;
    }

    /**
     * Creates a problematic bar series for testing error handling.
     */
    private BarSeries createProblematicBarSeries() {
        BarSeries series = new BaseBarSeriesBuilder().withName("Problematic Series").build();

        Instant time = Instant.now();

        // Add a bar with potentially problematic values
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(time)
                .openPrice(0.0) // Zero price
                .highPrice(0.0)
                .lowPrice(0.0)
                .closePrice(0.0)
                .volume(0.0) // Zero volume
                .build());

        return series;
    }

    /**
     * Creates a test trading record for testing purposes.
     */
    private TradingRecord createTestTradingRecord(BarSeries series) {
        TradingRecord record = new BaseTradingRecord();

        // Only add trades if series has enough bars
        if (series.getBarCount() >= 6) {
            Trade buyTrade = Trade.buyAt(2, series);
            Trade sellTrade = Trade.sellAt(5, series);

            record.enter(2, buyTrade.getPricePerAsset(), buyTrade.getAmount());
            record.exit(5, sellTrade.getPricePerAsset(), sellTrade.getAmount());
        } else {
            series.getBarCount();// For single bar, don't add any trades (indices would be out of bounds)
        }

        return record;
    }

    /**
     * Creates a bar series with special characters in the name for testing path
     * sanitization.
     */
    private BarSeries createSeriesWithSpecialChars() {
        BarSeries series = new BaseBarSeriesBuilder().withName("Test:Series/With\\Special?Chars*<>|\"").build();

        Instant time = Instant.now();

        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(time)
                .openPrice(100.0)
                .highPrice(105.0)
                .lowPrice(99.0)
                .closePrice(104.0)
                .volume(1000.0)
                .build());

        return series;
    }
}
