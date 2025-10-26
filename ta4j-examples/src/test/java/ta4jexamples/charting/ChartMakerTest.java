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
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ui.Layer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;

import java.util.Collection;
import java.util.Comparator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ChartMaker}.
 *
 * <p>
 * This test class focuses on testing the integration between ChartMaker and its
 * collaborators. Unit tests for specific components are located in:
 * </p>
 * <ul>
 * <li>{@link FileSystemChartStorageTest} - Chart storage functionality</li>
 * <li>{@link SwingChartDisplayerTest} - Chart display functionality</li>
 * <li>{@link TradingChartFactoryTest} - Chart creation functionality</li>
 * </ul>
 */
public class ChartMakerTest {

    private ChartMaker chartMaker;
    private BarSeries barSeries;
    private TradingRecord tradingRecord;

    @BeforeEach
    public void setUp() {
        chartMaker = new ChartMaker();
        barSeries = createTestBarSeries();
        tradingRecord = createTestTradingRecord(barSeries);
    }

    @Test
    public void testDefaultConstructor() {
        ChartMaker maker = new ChartMaker();
        assertNotNull(maker);
    }

    @Test
    public void testConstructorWithSaveDirectory() {
        String saveDir = "test/charts";
        ChartMaker maker = new ChartMaker(saveDir);
        assertNotNull(maker);
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
        JFreeChart chart = chartMaker.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getTitle(), "Chart title should not be null");
        assertTrue(chart.getTitle().getText().contains("Test Strategy"), "Chart title should contain strategy name");
    }

    @Test
    public void testGenerateChartAddsTradeMarkers() {
        JFreeChart chart = chartMaker.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        XYPlot plot = chart.getXYPlot();

        assertTrue(plot.getDatasetCount() > 1, "Trade dataset should be present on the chart");
        assertNotNull(plot.getDataset(1), "Trade dataset should not be null");
        assertInstanceOf(XYLineAndShapeRenderer.class, plot.getRenderer(1),
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
                () -> chartMaker.createTradingRecordChart(null, "Test Strategy", tradingRecord));
    }

    @Test
    public void testGenerateChartWithNullStrategyName() {
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createTradingRecordChart(barSeries, null, tradingRecord));
    }

    @Test
    public void testGenerateChartWithEmptyStrategyName() {
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createTradingRecordChart(barSeries, "", tradingRecord));
    }

    @Test
    public void testGenerateChartWithNullTradingRecord() {
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createTradingRecordChart(barSeries, "Test Strategy", null));
    }

    @Test
    public void testGenerateChartWithIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = chartMaker.createIndicatorChart(barSeries, closePrice, sma);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getTitle(), "Chart title should not be null");
    }

    @Test
    public void testGenerateChartWithNullSeriesForIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class, () -> chartMaker.createIndicatorChart(null, closePrice));
    }

    @Test
    public void testGenerateChartWithNullIndicators() {
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createIndicatorChart(barSeries, (Indicator<org.ta4j.core.num.Num>[]) null));
    }

    @Test
    public void testCreateIndicatorChartWithNullElement() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createIndicatorChart(barSeries, closePrice, null));
    }

    @Test
    public void testGenerateChartWithAnalysisTypes() {
        JFreeChart chart = chartMaker.createAnalysisChart(barSeries, AnalysisType.MOVING_AVERAGE_10,
                AnalysisType.MOVING_AVERAGE_20);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getTitle(), "Chart title should not be null");
    }

    @Test
    public void testGenerateChartWithNullSeriesForAnalysis() {
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createAnalysisChart(null, AnalysisType.MOVING_AVERAGE_10));
    }

    @Test
    public void testGenerateChartWithNullAnalysisTypes() {
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createAnalysisChart(barSeries, (AnalysisType[]) null));
    }

    @Test
    public void testCreateAnalysisChartWithNullElement() {
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createAnalysisChart(barSeries, AnalysisType.MOVING_AVERAGE_10, null));
    }

    @Test
    public void testGenerateChartAsBytes() {
        byte[] chartBytes = chartMaker.createTradingRecordChartBytes(barSeries, "Test Strategy", tradingRecord);

        assertNotNull(chartBytes, "Chart bytes should not be null");
        assertTrue(chartBytes.length > 0, "Chart bytes should not be empty");
    }

    @Test
    public void testGenerateChartAsBytesWithNullChart() {
        assertThrows(IllegalArgumentException.class, () -> chartMaker.getChartAsByteArray(null));
    }

    @Test
    public void testGetChartAsByteArray() {
        JFreeChart chart = chartMaker.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        byte[] bytes = chartMaker.getChartAsByteArray(chart);

        assertNotNull(bytes, "Bytes should not be null");
        assertTrue(bytes.length > 0, "Bytes should not be empty");
    }

    // Display scale tests moved to SwingChartDisplayerTest

    @Test
    public void testGenerateAndSaveChartImageWithoutSaveDirectory() {
        assertTrue(chartMaker.saveTradingRecordChart(barSeries, "Test Strategy", tradingRecord).isEmpty(),
                "Result should be empty when save directory is not configured");
    }

    @Test
    public void testGenerateAndSaveChartImageWithSaveDirectory() throws IOException {
        // Create temporary directory for testing
        Path tempDir = Files.createTempDirectory("chartmaker-test");
        try {
            ChartMaker makerWithSave = new ChartMaker(tempDir.toString());
            TradingRecord emptyRecord = new BaseTradingRecord();
            Optional<Path> result = makerWithSave.saveTradingRecordChart(barSeries, "TestStrat", emptyRecord);
            result.ifPresent(path -> assertTrue(Files.exists(path), "Saved chart path should exist"));
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
                () -> chartMaker.saveTradingRecordChart(null, "Test Strategy", tradingRecord));
    }

    @Test
    public void testGenerateAndDisplayTradingRecordChart() {
        // This test just ensures the method doesn't throw an exception
        // In a real test environment, we might mock the display functionality
        try {
            chartMaker.displayTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            assertTrue(e.getMessage().contains("headless") || e.getMessage().contains("display"),
                    "Exception should be related to display functionality");
        }
    }

    @Test
    public void testDisplayChartWithNullChart() {
        assertThrows(IllegalArgumentException.class, () -> chartMaker.displayChart(null));
    }

    @Test
    public void testGenerateAndDisplayChartWithAnalysisTypes() {
        try {
            chartMaker.displayAnalysisChart(barSeries, AnalysisType.MOVING_AVERAGE_10);
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
            chartMaker.displayIndicatorChart(barSeries, closePrice);
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            assertTrue(e.getMessage().contains("headless") || e.getMessage().contains("display"),
                    "Exception should be related to display functionality");
        }
    }

    // Chart title, empty record, single bar, time periods, and multiple analysis
    // tests moved to TradingChartFactoryTest

    @Test
    public void testErrorHandlingWithInvalidData() {
        // Test with series that might cause issues
        BarSeries problematicSeries = createProblematicBarSeries();
        JFreeChart chart = chartMaker.createTradingRecordChart(problematicSeries, "Test Strategy", tradingRecord);

        // Should handle gracefully and return a chart (possibly empty)
        assertNotNull(chart, "Chart should not be null even with problematic data");
    }

    @Test
    public void testPathSanitizationSimple() {
        // Test that sanitization doesn't crash the system (without file creation)
        BarSeries seriesWithSpecialChars = createSeriesWithSpecialChars();
        JFreeChart chart = chartMaker.createTradingRecordChart(seriesWithSpecialChars, "Test Strategy",
                new BaseTradingRecord());

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
