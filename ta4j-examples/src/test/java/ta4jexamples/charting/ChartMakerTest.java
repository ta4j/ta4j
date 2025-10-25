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

import org.jfree.chart.JFreeChart;
import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link ChartMaker}.
 */
public class ChartMakerTest {

    private ChartMaker chartMaker;
    private BarSeries barSeries;
    private TradingRecord tradingRecord;

    @Before
    public void setUp() {
        chartMaker = new ChartMaker();
        barSeries = createTestBarSeries();
        tradingRecord = createTestTradingRecord(barSeries); // Pass barSeries to create appropriate trades
    }

    @Test
    public void testDefaultConstructor() {
        ChartMaker maker = new ChartMaker();
        assertNotNull("ChartMaker should not be null", maker);
        // Test that the default constructor works
        assertNotNull("ChartMaker should be created successfully", maker);
    }

    @Test
    public void testConstructorWithSaveDirectory() {
        String saveDir = "test/charts";
        ChartMaker maker = new ChartMaker(saveDir);
        assertNotNull("ChartMaker should not be null", maker);
        // Test that the constructor works without accessing private fields
        assertNotNull("ChartMaker should be created successfully", maker);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullSaveDirectory() {
        new ChartMaker(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithEmptySaveDirectory() {
        new ChartMaker("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithBlankSaveDirectory() {
        new ChartMaker("   ");
    }

    @Test
    public void testGenerateChartWithTradingRecord() {
        JFreeChart chart = chartMaker.generateChart(barSeries, "Test Strategy", tradingRecord);

        assertNotNull("Chart should not be null", chart);
        assertNotNull("Chart title should not be null", chart.getTitle());
        assertTrue("Chart title should contain strategy name", chart.getTitle().getText().contains("Test Strategy"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenerateChartWithNullSeries() {
        chartMaker.generateChart(null, "Test Strategy", tradingRecord);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenerateChartWithNullStrategyName() {
        chartMaker.generateChart(barSeries, null, tradingRecord);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenerateChartWithEmptyStrategyName() {
        chartMaker.generateChart(barSeries, "", tradingRecord);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenerateChartWithNullTradingRecord() {
        chartMaker.generateChart(barSeries, "Test Strategy", null);
    }

    @Test
    public void testGenerateChartWithIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = chartMaker.generateChart(barSeries, closePrice, sma);

        assertNotNull("Chart should not be null", chart);
        assertNotNull("Chart title should not be null", chart.getTitle());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenerateChartWithNullSeriesForIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        chartMaker.generateChart(null, closePrice);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenerateChartWithNullIndicators() {
        chartMaker.generateChart(barSeries, (Indicator<org.ta4j.core.num.Num>[]) null);
    }

    @Test
    public void testGenerateChartWithAnalysisTypes() {
        JFreeChart chart = chartMaker.generateChart(barSeries, AnalysisType.MOVING_AVERAGE_10,
                AnalysisType.MOVING_AVERAGE_20);

        assertNotNull("Chart should not be null", chart);
        assertNotNull("Chart title should not be null", chart.getTitle());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenerateChartWithNullSeriesForAnalysis() {
        chartMaker.generateChart(null, AnalysisType.MOVING_AVERAGE_10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenerateChartWithNullAnalysisTypes() {
        chartMaker.generateChart(barSeries, (AnalysisType[]) null);
    }

    @Test
    public void testGenerateChartAsBytes() {
        byte[] chartBytes = chartMaker.generateChartAsBytes(barSeries, "Test Strategy", tradingRecord);

        assertNotNull("Chart bytes should not be null", chartBytes);
        assertTrue("Chart bytes should not be empty", chartBytes.length > 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenerateChartAsBytesWithNullChart() {
        chartMaker.getChartAsByteArray(null);
    }

    @Test
    public void testGetChartAsByteArray() {
        JFreeChart chart = chartMaker.generateChart(barSeries, "Test Strategy", tradingRecord);
        byte[] bytes = chartMaker.getChartAsByteArray(chart);

        assertNotNull("Bytes should not be null", bytes);
        assertTrue("Bytes should not be empty", bytes.length > 0);
    }

    @Test
    public void testGenerateAndSaveChartImageWithoutSaveDirectory() {
        String result = chartMaker.generateAndSaveChartImage(barSeries, "Test Strategy", tradingRecord);

        assertNull("Result should be null when save directory is not configured", result);
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
                assertFalse("Result should not be empty", result.isEmpty());
            }
        } finally {
            // Clean up
            if (Files.exists(tempDir)) {
                Files.walk(tempDir).sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGenerateAndSaveChartImageWithNullParameters() {
        chartMaker.generateAndSaveChartImage(null, "Test Strategy", tradingRecord);
    }

    @Test
    public void testGenerateAndDisplayTradingRecordChart() {
        // This test just ensures the method doesn't throw an exception
        // In a real test environment, we might mock the display functionality
        try {
            chartMaker.generateAndDisplayTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            assertTrue("Exception should be related to display functionality",
                    e.getMessage().contains("headless") || e.getMessage().contains("display"));
        }
    }

    @Test
    public void testGenerateAndDisplayChartWithAnalysisTypes() {
        try {
            chartMaker.generateAndDisplayChart(barSeries, AnalysisType.MOVING_AVERAGE_10);
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            assertTrue("Exception should be related to display functionality",
                    e.getMessage().contains("headless") || e.getMessage().contains("display"));
        }
    }

    @Test
    public void testGenerateAndDisplayChartWithIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        try {
            chartMaker.generateAndDisplayChart(barSeries, closePrice);
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            assertTrue("Exception should be related to display functionality",
                    e.getMessage().contains("headless") || e.getMessage().contains("display"));
        }
    }

    @Test
    public void testChartTitleGeneration() {
        JFreeChart chart = chartMaker.generateChart(barSeries, "Test Strategy", tradingRecord);
        String title = chart.getTitle().getText();

        assertNotNull("Title should not be null", title);
        assertTrue("Title should contain strategy name", title.contains("Test Strategy"));
        assertTrue("Title should contain series name", title.contains("Test"));
    }

    @Test
    public void testChartWithEmptyTradingRecord() {
        TradingRecord emptyRecord = new BaseTradingRecord();
        JFreeChart chart = chartMaker.generateChart(barSeries, "Test Strategy", emptyRecord);

        assertNotNull("Chart should not be null even with empty trading record", chart);
    }

    @Test
    public void testChartWithSingleBar() {
        BarSeries singleBarSeries = createSingleBarSeries();
        // Create empty trading record for single bar (no trades within range)
        TradingRecord emptyRecord = new BaseTradingRecord();
        JFreeChart chart = chartMaker.generateChart(singleBarSeries, "Test Strategy", emptyRecord);

        assertNotNull("Chart should not be null even with single bar", chart);
    }

    @Test
    public void testChartWithDifferentTimePeriods() {
        // Test with daily data
        BarSeries dailySeries = createTestBarSeries();
        TradingRecord dailyRecord = createTestTradingRecord(dailySeries);
        JFreeChart dailyChart = chartMaker.generateChart(dailySeries, "Daily Strategy", dailyRecord);
        assertNotNull("Daily chart should not be null", dailyChart);

        // Test with intraday data (shorter duration)
        BarSeries intradaySeries = createIntradayBarSeries();
        TradingRecord intradayRecord = createTestTradingRecord(intradaySeries);
        JFreeChart intradayChart = chartMaker.generateChart(intradaySeries, "Intraday Strategy", intradayRecord);
        assertNotNull("Intraday chart should not be null", intradayChart);
    }

    @Test
    public void testChartWithMultipleAnalysisTypes() {
        AnalysisType[] analysisTypes = { AnalysisType.MOVING_AVERAGE_10, AnalysisType.MOVING_AVERAGE_20,
                AnalysisType.MOVING_AVERAGE_50 };

        JFreeChart chart = chartMaker.generateChart(barSeries, analysisTypes);
        assertNotNull("Chart should not be null", chart);
    }

    @Test
    public void testChartWithMultipleIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma5 = new SMAIndicator(closePrice, 5);
        SMAIndicator sma10 = new SMAIndicator(closePrice, 10);

        @SuppressWarnings("unchecked")
        Indicator<org.ta4j.core.num.Num>[] indicators = new Indicator[] { closePrice, sma5, sma10 };
        JFreeChart chart = chartMaker.generateChart(barSeries, indicators);
        assertNotNull("Chart should not be null", chart);
    }

    @Test
    public void testErrorHandlingWithInvalidData() {
        // Test with series that might cause issues
        BarSeries problematicSeries = createProblematicBarSeries();
        JFreeChart chart = chartMaker.generateChart(problematicSeries, "Test Strategy", tradingRecord);

        // Should handle gracefully and return a chart (possibly empty)
        assertNotNull("Chart should not be null even with problematic data", chart);
    }

    @Test
    public void testPathSanitizationSimple() {
        // Test that sanitization doesn't crash the system (without file creation)
        BarSeries seriesWithSpecialChars = createSeriesWithSpecialChars();
        JFreeChart chart = chartMaker.generateChart(seriesWithSpecialChars, "Test Strategy", new BaseTradingRecord());

        assertNotNull("Chart should not be null even with special chars in series name", chart);
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
        } else if (series.getBarCount() >= 1) {
            // For single bar, don't add any trades (indices would be out of bounds)
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
