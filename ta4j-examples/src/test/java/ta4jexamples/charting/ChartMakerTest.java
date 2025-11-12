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
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.Num;

import java.util.Collection;
import java.util.Comparator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

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
        barSeries = ChartingTestFixtures.standardDailySeries();
        tradingRecord = ChartingTestFixtures.completedTradeRecord(barSeries);
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
    public void testCreateTradingRecordChartWithIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = chartMaker.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord, closePrice,
                sma);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getTitle(), "Chart title should not be null");
        assertInstanceOf(CombinedDomainXYPlot.class, chart.getPlot(), "Combined plot expected for mixed chart");
        CombinedDomainXYPlot combinedPlot = (CombinedDomainXYPlot) chart.getPlot();
        assertEquals(1 + 2, combinedPlot.getSubplots().size(),
                "Main OHLC subplot plus each indicator subplot should be present");
        XYPlot mainPlot = combinedPlot.getSubplots().get(0);
        assertTrue(mainPlot.getDatasetCount() > 1, "Trading markers should be attached to main subplot");
        assertInstanceOf(XYLineAndShapeRenderer.class, mainPlot.getRenderer(1),
                "Trading markers should use line-and-shape renderer");
        assertTrue(mainPlot.getAnnotations().stream().anyMatch(XYTextAnnotation.class::isInstance),
                "Combined chart should display trade annotations");
    }

    @Test
    public void testCreateTradingRecordChartWithIndicatorsRejectsNullVarargs() {
        assertThrows(IllegalArgumentException.class, () -> chartMaker.createTradingRecordChart(barSeries,
                "Test Strategy", tradingRecord, (Indicator<Num>[]) null));
    }

    @Test
    public void testCreateTradingRecordChartWithIndicatorsRejectsNullElement() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord, closePrice, null));
    }

    @Test
    public void testSaveTradingRecordChartWithIndicatorsRejectsNullVarargs() {
        assertThrows(IllegalArgumentException.class, () -> chartMaker.saveTradingRecordChart(barSeries, "Test Strategy",
                tradingRecord, (Indicator<Num>[]) null));
    }

    @Test
    public void testSaveTradingRecordChartWithIndicatorsRejectsNullElement() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.saveTradingRecordChart(barSeries, "Test Strategy", tradingRecord, closePrice, null));
    }

    @Test
    public void testCreateTradingRecordChartBytesWithIndicatorsRejectsNullVarargs() {
        assertThrows(IllegalArgumentException.class, () -> chartMaker.createTradingRecordChartBytes(barSeries,
                "Test Strategy", tradingRecord, (Indicator<Num>[]) null));
    }

    @Test
    public void testCreateTradingRecordChartBytesWithIndicatorsRejectsNullElement() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class, () -> chartMaker.createTradingRecordChartBytes(barSeries,
                "Test Strategy", tradingRecord, closePrice, null));
    }

    @Test
    public void testDisplayTradingRecordChartWithIndicatorsRejectsNullVarargs() {
        assertThrows(IllegalArgumentException.class, () -> chartMaker.displayTradingRecordChart(barSeries,
                "Test Strategy", tradingRecord, (Indicator<Num>[]) null));
    }

    @Test
    public void testDisplayTradingRecordChartWithIndicatorsRejectsNullElement() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class, () -> chartMaker.displayTradingRecordChart(barSeries,
                "Test Strategy", tradingRecord, closePrice, null));
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
                () -> chartMaker.createIndicatorChart(barSeries, (Indicator<Num>[]) null));
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
    public void testGenerateChartAsBytesWithIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        byte[] chartBytes = chartMaker.createTradingRecordChartBytes(barSeries, "Test Strategy", tradingRecord,
                closePrice, sma);

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
    public void testSaveTradingRecordChartWithIndicatorsWithoutSaveDirectory() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertTrue(chartMaker.saveTradingRecordChart(barSeries, "Test Strategy", tradingRecord, closePrice).isEmpty(),
                "No-op storage should still return empty optional for indicator overload");
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
    public void testSaveTradingRecordChartWithIndicators() throws IOException {
        Path tempDir = Files.createTempDirectory("chartmaker-indicator-save");
        try {
            ChartMaker makerWithSave = new ChartMaker(tempDir.toString());
            ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
            SMAIndicator sma = new SMAIndicator(closePrice, 5);

            Optional<Path> result = makerWithSave.saveTradingRecordChart(barSeries, "Strategy", tradingRecord,
                    closePrice, sma);

            assertTrue(result.isPresent(), "Combined chart should be persisted when storage is configured");
            result.ifPresent(path -> assertTrue(Files.exists(path), "Persisted chart file should exist"));
        } finally {
            if (Files.exists(tempDir)) {
                Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
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
    public void testDisplayTradingRecordChartWithIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        try {
            chartMaker.displayTradingRecordChart(barSeries, "Test Strategy", tradingRecord, closePrice, sma);
        } catch (Exception e) {
            String message = e.getMessage();
            if (message != null) {
                assertTrue(message.contains("headless") || message.contains("display"),
                        "Exception should be related to display functionality");
            }
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
        BarSeries problematicSeries = ChartingTestFixtures.problematicSeries();
        JFreeChart chart = chartMaker.createTradingRecordChart(problematicSeries, "Test Strategy", tradingRecord);

        // Should handle gracefully and return a chart (possibly empty)
        assertNotNull(chart, "Chart should not be null even with problematic data");
    }

    @Test
    public void testPathSanitizationSimple() {
        // Test that sanitization doesn't crash the system (without file creation)
        BarSeries seriesWithSpecialChars = ChartingTestFixtures.seriesWithSpecialChars();
        JFreeChart chart = chartMaker.createTradingRecordChart(seriesWithSpecialChars, "Test Strategy",
                new BaseTradingRecord());

        assertNotNull(chart, "Chart should not be null even with special chars in series name");
    }

    // ========== Dual-Axis Chart Tests ==========

    @Test
    public void testCreateDualAxisChart() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = chartMaker.createDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA");

        assertNotNull(chart, "Dual-axis chart should not be null");
        assertNotNull(chart.getTitle(), "Chart should have a title");
        assertTrue(chart.getTitle().getText().contains(barSeries.getName()) || barSeries.getName() == null,
                "Chart title should contain series name");
    }

    @Test
    public void testCreateDualAxisChartWithCustomTitle() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = chartMaker.createDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA",
                "Custom Chart Title");

        assertNotNull(chart, "Dual-axis chart should not be null");
        assertNotNull(chart.getTitle(), "Chart should have a title");
        assertEquals("Custom Chart Title", chart.getTitle().getText(), "Chart title should match custom title");
    }

    @Test
    public void testCreateDualAxisChartWithNullSeries() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createDualAxisChart(null, closePrice, "Price", sma, "SMA"));
    }

    @Test
    public void testCreateDualAxisChartWithNullPrimaryIndicator() {
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 5);

        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createDualAxisChart(barSeries, null, "Price", sma, "SMA"));
    }

    @Test
    public void testCreateDualAxisChartWithNullSecondaryIndicator() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);

        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createDualAxisChart(barSeries, closePrice, "Price", null, "SMA"));
    }

    @Test
    public void testCreateDualAxisChartWithNullPrimaryLabel() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createDualAxisChart(barSeries, closePrice, null, sma, "SMA"));
    }

    @Test
    public void testCreateDualAxisChartWithEmptyPrimaryLabel() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createDualAxisChart(barSeries, closePrice, "", sma, "SMA"));
    }

    @Test
    public void testCreateDualAxisChartWithNullSecondaryLabel() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createDualAxisChart(barSeries, closePrice, "Price", sma, null));
    }

    @Test
    public void testCreateDualAxisChartWithEmptySecondaryLabel() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        assertThrows(IllegalArgumentException.class,
                () -> chartMaker.createDualAxisChart(barSeries, closePrice, "Price", sma, ""));
    }

    @Test
    public void testDisplayDualAxisChart() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        // This test just ensures the method doesn't throw an exception
        // In a real test environment, we might mock the display functionality
        try {
            chartMaker.displayDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA");
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            String message = e.getMessage();
            if (message != null) {
                assertTrue(message.contains("headless") || message.contains("display"),
                        "Exception should be related to display functionality");
            }
            // HeadlessException can have null message, which is also acceptable
        }
    }

    @Test
    public void testDisplayDualAxisChartWithCustomTitles() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        try {
            chartMaker.displayDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA", "Custom Chart",
                    "Custom Window");
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            String message = e.getMessage();
            if (message != null) {
                assertTrue(message.contains("headless") || message.contains("display"),
                        "Exception should be related to display functionality");
            }
            // HeadlessException can have null message, which is also acceptable
        }
    }

    @Test
    public void testDisplayChartWithWindowTitle() {
        JFreeChart chart = chartMaker.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

        try {
            chartMaker.displayChart(chart, "Custom Window Title");
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            String message = e.getMessage();
            if (message != null) {
                assertTrue(message.contains("headless") || message.contains("display"),
                        "Exception should be related to display functionality");
            }
            // HeadlessException can have null message, which is also acceptable
        }
    }

    @Test
    public void testDisplayChartWithNullWindowTitle() {
        JFreeChart chart = chartMaker.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

        try {
            chartMaker.displayChart(chart, null);
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            String message = e.getMessage();
            if (message != null) {
                assertTrue(message.contains("headless") || message.contains("display"),
                        "Exception should be related to display functionality");
            }
            // HeadlessException can have null message, which is also acceptable
        }
    }

    @Test
    public void testDisplayChartWithEmptyWindowTitle() {
        JFreeChart chart = chartMaker.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

        try {
            chartMaker.displayChart(chart, "");
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            String message = e.getMessage();
            if (message != null) {
                assertTrue(message.contains("headless") || message.contains("display"),
                        "Exception should be related to display functionality");
            }
            // HeadlessException can have null message, which is also acceptable
        }
    }

    @Test
    public void testChartLegendNotDuplicatedWhenReusingChartMaker() {
        // Create a ChartMaker with save directory to enable save functionality
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("chartmaker-test");
            ChartMaker makerWithSave = new ChartMaker(tempDir.toString());

            // Create a chart
            JFreeChart chart = makerWithSave.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

            // Count legend items before display
            int legendItemCountBefore = countLegendItems(chart);

            // Display the chart (this might modify it)
            try {
                makerWithSave.displayChart(chart);
            } catch (Exception e) {
                // Headless environment - that's OK for this test
            }

            // Count legend items after display
            int legendItemCountAfter = countLegendItems(chart);

            // The legend items should not have changed
            assertEquals(legendItemCountBefore, legendItemCountAfter,
                    "Legend items should not be duplicated when reusing ChartMaker instance");

            // Save the chart image (which uses the same chart instance)
            makerWithSave.saveChartImage(chart, barSeries, "Test Chart");

            // Count legend items after save
            int legendItemCountAfterSave = countLegendItems(chart);

            // The legend items should still not have changed
            assertEquals(legendItemCountBefore, legendItemCountAfterSave,
                    "Legend items should not be duplicated after save operation");
        } catch (IOException e) {
            fail("Failed to create temporary directory: " + e.getMessage());
        } finally {
            // Clean up
            if (tempDir != null && Files.exists(tempDir)) {
                try {
                    Files.walk(tempDir).sorted(Comparator.reverseOrder()).forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            // Ignore cleanup errors
                        }
                    });
                } catch (IOException e) {
                    // Ignore cleanup errors
                }
            }
        }
    }

    private int countLegendItems(JFreeChart chart) {
        // Count subtitles which include legends in JFreeChart
        int subtitleCount = chart.getSubtitleCount();
        // Filter to count only LegendTitle instances
        int legendCount = 0;
        for (int i = 0; i < subtitleCount; i++) {
            if (chart.getSubtitle(i) instanceof org.jfree.chart.title.LegendTitle) {
                legendCount++;
            }
        }
        return legendCount;
    }

}
