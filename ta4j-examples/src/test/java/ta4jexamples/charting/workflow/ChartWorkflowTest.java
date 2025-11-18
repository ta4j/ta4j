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
import org.jfree.chart.title.LegendTitle;
import org.jfree.chart.ui.Layer;
import org.junit.Assume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.num.Num;

import java.awt.*;
import java.util.Collection;
import java.util.Comparator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ChartWorkflow}.
 *
 * <p>
 * This test class focuses on testing the integration between ChartWorkflow and
 * its collaborators. Unit tests for specific components are located in:
 * </p>
 * <ul>
 * <li>{@link FileSystemChartStorageTest} - Chart storage functionality</li>
 * <li>{@link SwingChartDisplayerTest} - Chart display functionality</li>
 * <li>{@link TradingChartFactoryTest} - Chart creation functionality</li>
 * </ul>
 */
public class ChartWorkflowTest {

    private ChartWorkflow chartWorkflow;
    private BarSeries barSeries;
    private TradingRecord tradingRecord;

    @BeforeEach
    public void setUp() {
        chartWorkflow = new ChartWorkflow();
        barSeries = ChartingTestFixtures.standardDailySeries();
        tradingRecord = ChartingTestFixtures.completedTradeRecord(barSeries);
    }

    @Test
    public void testDefaultConstructor() {
        ChartWorkflow maker = new ChartWorkflow();
        assertNotNull(maker);
    }

    @Test
    public void testConstructorWithSaveDirectory() {
        String saveDir = "test/charts";
        ChartWorkflow maker = new ChartWorkflow(saveDir);
        assertNotNull(maker);
    }

    @Test
    public void testConstructorWithNullSaveDirectory() {
        assertThrows(IllegalArgumentException.class, () -> new ChartWorkflow(null));
    }

    @Test
    public void testConstructorWithEmptySaveDirectory() {
        assertThrows(IllegalArgumentException.class, () -> new ChartWorkflow(""));
    }

    @Test
    public void testConstructorWithBlankSaveDirectory() {
        assertThrows(IllegalArgumentException.class, () -> new ChartWorkflow("   "));
    }

    @Test
    public void testBuilder() {
        ChartBuilder builder = chartWorkflow.builder();
        assertNotNull(builder, "Builder should not be null");
    }

    @Test
    public void testBuilderProducesChart() {
        JFreeChart chart = chartWorkflow.builder()
                .withSeries(barSeries)
                .withTradingRecordOverlay(tradingRecord)
                .toChart();
        assertNotNull(chart, "Chart should not be null");
        assertInstanceOf(CombinedDomainXYPlot.class, chart.getPlot(),
                "Chart built through builder should use CombinedDomainXYPlot");
    }

    @Test
    public void testGenerateChartWithTradingRecord() {
        JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getTitle(), "Chart title should not be null");
        assertTrue(chart.getTitle().getText().contains("Test Strategy"), "Chart title should contain strategy name");
    }

    @Test
    public void testGenerateChartAddsTradeMarkers() {
        JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
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

        JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord, closePrice,
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
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.createTradingRecordChart(barSeries,
                "Test Strategy", tradingRecord, (Indicator<Num>[]) null));
    }

    @Test
    public void testCreateTradingRecordChartWithIndicatorsRejectsNullElement() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.createTradingRecordChart(barSeries,
                "Test Strategy", tradingRecord, closePrice, null));
    }

    @Test
    public void testSaveTradingRecordChartWithIndicatorsRejectsNullVarargs() {
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.saveTradingRecordChart(barSeries,
                "Test Strategy", tradingRecord, (Indicator<Num>[]) null));
    }

    @Test
    public void testSaveTradingRecordChartWithIndicatorsRejectsNullElement() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.saveTradingRecordChart(barSeries,
                "Test Strategy", tradingRecord, closePrice, null));
    }

    @Test
    public void testCreateTradingRecordChartBytesWithIndicatorsRejectsNullVarargs() {
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.createTradingRecordChartBytes(barSeries,
                "Test Strategy", tradingRecord, (Indicator<Num>[]) null));
    }

    @Test
    public void testCreateTradingRecordChartBytesWithIndicatorsRejectsNullElement() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.createTradingRecordChartBytes(barSeries,
                "Test Strategy", tradingRecord, closePrice, null));
    }

    @Test
    public void testDisplayTradingRecordChartWithIndicatorsRejectsNullVarargs() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.displayTradingRecordChart(barSeries,
                "Test Strategy", tradingRecord, (Indicator<Num>[]) null));
    }

    @Test
    public void testDisplayTradingRecordChartWithIndicatorsRejectsNullElement() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.displayTradingRecordChart(barSeries,
                "Test Strategy", tradingRecord, closePrice, null));
    }

    @Test
    public void testGenerateChartWithNullSeries() {
        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.createTradingRecordChart(null, "Test Strategy", tradingRecord));
    }

    @Test
    public void testGenerateChartWithNullStrategyName() {
        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.createTradingRecordChart(barSeries, null, tradingRecord));
    }

    @Test
    public void testGenerateChartWithEmptyStrategyName() {
        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.createTradingRecordChart(barSeries, "", tradingRecord));
    }

    @Test
    public void testGenerateChartWithNullTradingRecord() {
        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", null));
    }

    @Test
    public void testGenerateChartWithIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = chartWorkflow.createIndicatorChart(barSeries, closePrice, sma);

        assertNotNull(chart, "Chart should not be null");
        assertNotNull(chart.getTitle(), "Chart title should not be null");
    }

    @Test
    public void testGenerateChartWithNullSeriesForIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.createIndicatorChart(null, closePrice));
    }

    @Test
    public void testGenerateChartWithNullIndicators() {
        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.createIndicatorChart(barSeries, (Indicator<Num>[]) null));
    }

    @Test
    public void testCreateIndicatorChartWithNullElement() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.createIndicatorChart(barSeries, closePrice, null));
    }

    @Test
    public void testGenerateChartAsBytes() {
        byte[] chartBytes = chartWorkflow.createTradingRecordChartBytes(barSeries, "Test Strategy", tradingRecord);

        assertNotNull(chartBytes, "Chart bytes should not be null");
        assertTrue(chartBytes.length > 0, "Chart bytes should not be empty");
    }

    @Test
    public void testGenerateChartAsBytesWithIndicators() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        byte[] chartBytes = chartWorkflow.createTradingRecordChartBytes(barSeries, "Test Strategy", tradingRecord,
                closePrice, sma);

        assertNotNull(chartBytes, "Chart bytes should not be null");
        assertTrue(chartBytes.length > 0, "Chart bytes should not be empty");
    }

    @Test
    public void testGenerateChartAsBytesWithNullChart() {
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.getChartAsByteArray(null));
    }

    @Test
    public void testGetChartAsByteArray() {
        JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        byte[] bytes = chartWorkflow.getChartAsByteArray(chart);

        assertNotNull(bytes, "Bytes should not be null");
        assertTrue(bytes.length > 0, "Bytes should not be empty");
    }

    // Display scale tests moved to SwingChartDisplayerTest

    @Test
    public void testGenerateAndSaveChartImageWithoutSaveDirectory() {
        assertTrue(chartWorkflow.saveTradingRecordChart(barSeries, "Test Strategy", tradingRecord).isEmpty(),
                "Result should be empty when save directory is not configured");
    }

    @Test
    public void testSaveTradingRecordChartWithIndicatorsWithoutSaveDirectory() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        assertTrue(
                chartWorkflow.saveTradingRecordChart(barSeries, "Test Strategy", tradingRecord, closePrice).isEmpty(),
                "No-op storage should still return empty optional for indicator overload");
    }

    @Test
    public void testGenerateAndSaveChartImageWithSaveDirectory() throws IOException {
        // Create temporary directory for testing
        Path tempDir = Files.createTempDirectory("ChartWorkflow-test");
        try {
            ChartWorkflow makerWithSave = new ChartWorkflow(tempDir.toString());
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
        Path tempDir = Files.createTempDirectory("ChartWorkflow-indicator-save");
        try {
            ChartWorkflow makerWithSave = new ChartWorkflow(tempDir.toString());
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
                () -> chartWorkflow.saveTradingRecordChart(null, "Test Strategy", tradingRecord));
    }

    @Test
    public void testGenerateAndDisplayTradingRecordChart() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        try {
            chartWorkflow.displayTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        } catch (Exception e) {
            // In headless environments, this might throw an exception, which is expected
            assertTrue(e.getMessage().contains("headless") || e.getMessage().contains("display"),
                    "Exception should be related to display functionality");
        }
    }

    @Test
    public void testDisplayTradingRecordChartWithIndicators() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        try {
            chartWorkflow.displayTradingRecordChart(barSeries, "Test Strategy", tradingRecord, closePrice, sma);
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
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.displayChart(null));
    }

    @Test
    public void testGenerateAndDisplayChartWithIndicators() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        try {
            chartWorkflow.displayIndicatorChart(barSeries, closePrice);
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
        JFreeChart chart = chartWorkflow.createTradingRecordChart(problematicSeries, "Test Strategy", tradingRecord);

        // Should handle gracefully and return a chart (possibly empty)
        assertNotNull(chart, "Chart should not be null even with problematic data");
    }

    @Test
    public void testPathSanitizationSimple() {
        // Test that sanitization doesn't crash the system (without file creation)
        BarSeries seriesWithSpecialChars = ChartingTestFixtures.seriesWithSpecialChars();
        JFreeChart chart = chartWorkflow.createTradingRecordChart(seriesWithSpecialChars, "Test Strategy",
                new BaseTradingRecord());

        assertNotNull(chart, "Chart should not be null even with special chars in series name");
    }

    // ========== Dual-Axis Chart Tests ==========

    @Test
    public void testCreateDualAxisChart() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = chartWorkflow.createDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA");

        assertNotNull(chart, "Dual-axis chart should not be null");
        assertNotNull(chart.getTitle(), "Chart should have a title");
        assertTrue(chart.getTitle().getText().contains(barSeries.getName()) || barSeries.getName() == null,
                "Chart title should contain series name");
    }

    @Test
    public void testCreateDualAxisChartWithCustomTitle() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        JFreeChart chart = chartWorkflow.createDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA",
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
                () -> chartWorkflow.createDualAxisChart(null, closePrice, "Price", sma, "SMA"));
    }

    @Test
    public void testCreateDualAxisChartWithNullPrimaryIndicator() {
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(barSeries), 5);

        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.createDualAxisChart(barSeries, null, "Price", sma, "SMA"));
    }

    @Test
    public void testCreateDualAxisChartWithNullSecondaryIndicator() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);

        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.createDualAxisChart(barSeries, closePrice, "Price", null, "SMA"));
    }

    @Test
    public void testCreateDualAxisChartWithNullPrimaryLabel() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.createDualAxisChart(barSeries, closePrice, null, sma, "SMA"));
    }

    @Test
    public void testCreateDualAxisChartWithEmptyPrimaryLabel() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.createDualAxisChart(barSeries, closePrice, "", sma, "SMA"));
    }

    @Test
    public void testCreateDualAxisChartWithNullSecondaryLabel() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.createDualAxisChart(barSeries, closePrice, "Price", sma, null));
    }

    @Test
    public void testCreateDualAxisChartWithEmptySecondaryLabel() {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.createDualAxisChart(barSeries, closePrice, "Price", sma, ""));
    }

    @Test
    public void testDisplayDualAxisChart() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        // This test just ensures the method doesn't throw an exception
        // In a real test environment, we might mock the display functionality
        try {
            chartWorkflow.displayDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA");
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
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        ClosePriceIndicator closePrice = new ClosePriceIndicator(barSeries);
        SMAIndicator sma = new SMAIndicator(closePrice, 5);

        try {
            chartWorkflow.displayDualAxisChart(barSeries, closePrice, "Price (USD)", sma, "SMA", "Custom Chart",
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
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

        try {
            chartWorkflow.displayChart(chart, "Custom Window Title");
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
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

        try {
            chartWorkflow.displayChart(chart, null);
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
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

        try {
            chartWorkflow.displayChart(chart, "");
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
    public void testChartLegendNotDuplicatedWhenReusingChartWorkflow() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        // Create a ChartWorkflow with save directory to enable save functionality
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("ChartWorkflow-test");
            ChartWorkflow makerWithSave = new ChartWorkflow(tempDir.toString());

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
                    "Legend items should not be duplicated when reusing ChartWorkflow instance");

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
            if (chart.getSubtitle(i) instanceof LegendTitle) {
                legendCount++;
            }
        }
        return legendCount;
    }

    // ========== saveChartImage with custom directory tests ==========

    @Test
    public void testSaveChartImageWithPathDirectory() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
            Optional<Path> result = chartWorkflow.saveChartImage(chart, barSeries, customDir);

            assertTrue(result.isPresent(), "Chart should be saved to custom directory");
            assertTrue(Files.exists(result.get()), "Saved chart file should exist");
            assertTrue(result.get().startsWith(customDir), "Chart should be saved in the specified directory");
            assertTrue(Files.isRegularFile(result.get()), "Saved path should be a file");
            assertTrue(result.get().toString().endsWith(".jpg"), "Saved file should have .jpg extension");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithPathDirectoryCreatesDirectories() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        Path nestedDir = customDir.resolve("nested").resolve("subdirectory");
        try {
            JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
            Optional<Path> result = chartWorkflow.saveChartImage(chart, barSeries, nestedDir);

            assertTrue(result.isPresent(), "Chart should be saved even to nested directory");
            assertTrue(Files.exists(result.get()), "Saved chart file should exist");
            assertTrue(Files.exists(nestedDir), "Nested directories should be created");
            assertTrue(result.get().startsWith(nestedDir), "Chart should be saved in the nested directory");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithPathDirectoryUsesCustomDirectoryNotConstructorDirectory() throws IOException {
        Path constructorDir = Files.createTempDirectory("ChartWorkflow-constructor-dir");
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            ChartWorkflow makerWithConstructorDir = new ChartWorkflow(constructorDir.toString());
            JFreeChart chart = makerWithConstructorDir.createTradingRecordChart(barSeries, "Test Strategy",
                    tradingRecord);

            // Save to custom directory (not constructor directory)
            Optional<Path> result = makerWithConstructorDir.saveChartImage(chart, barSeries, customDir);

            assertTrue(result.isPresent(), "Chart should be saved");
            assertTrue(result.get().startsWith(customDir),
                    "Chart should be saved in custom directory, not constructor directory");
            assertFalse(result.get().startsWith(constructorDir), "Chart should NOT be saved in constructor directory");
        } finally {
            if (Files.exists(constructorDir)) {
                Files.walk(constructorDir).sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
            }
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithPathDirectoryWorksWithoutConstructorDirectory() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            // ChartWorkflow created without save directory
            ChartWorkflow makerWithoutDir = new ChartWorkflow();
            JFreeChart chart = makerWithoutDir.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

            // Should still work with custom directory parameter
            Optional<Path> result = makerWithoutDir.saveChartImage(chart, barSeries, customDir);

            assertTrue(result.isPresent(),
                    "Chart should be saved even when ChartWorkflow has no constructor directory");
            assertTrue(Files.exists(result.get()), "Saved chart file should exist");
            assertTrue(result.get().startsWith(customDir), "Chart should be saved in the specified custom directory");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithPathDirectoryRejectsNullPath() {
        JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.saveChartImage(chart, barSeries, (Path) null),
                "Should reject null Path directory");
    }

    @Test
    public void testSaveChartImageWithPathDirectoryRejectsNullChart() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            assertThrows(IllegalArgumentException.class, () -> chartWorkflow.saveChartImage(null, barSeries, customDir),
                    "Should reject null chart when saving to Path directory");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithPathDirectoryRejectsNullSeries() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
            assertThrows(IllegalArgumentException.class, () -> chartWorkflow.saveChartImage(chart, null, customDir),
                    "Should reject null BarSeries when saving to Path directory");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithStringDirectory() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
            Optional<Path> result = chartWorkflow.saveChartImage(chart, barSeries, null, customDir.toString());

            assertTrue(result.isPresent(), "Chart should be saved to custom directory");
            assertTrue(Files.exists(result.get()), "Saved chart file should exist");
            assertTrue(result.get().startsWith(customDir), "Chart should be saved in the specified directory");
            assertTrue(Files.isRegularFile(result.get()), "Saved path should be a file");
            assertTrue(result.get().toString().endsWith(".jpg"), "Saved file should have .jpg extension");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithStringDirectoryAndFileName() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
            String customFileName = "MyCustomChart";
            Optional<Path> result = chartWorkflow.saveChartImage(chart, barSeries, customFileName,
                    customDir.toString());

            assertTrue(result.isPresent(), "Chart should be saved to custom directory");
            assertTrue(Files.exists(result.get()), "Saved chart file should exist");
            assertTrue(result.get().startsWith(customDir), "Chart should be saved in the specified directory");
            assertTrue(result.get().toString().contains(customFileName), "Saved file should contain custom filename");
            assertTrue(result.get().toString().endsWith(".jpg"), "Saved file should have .jpg extension");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithStringDirectoryUsesCustomDirectoryNotConstructorDirectory() throws IOException {
        Path constructorDir = Files.createTempDirectory("ChartWorkflow-constructor-dir");
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            ChartWorkflow makerWithConstructorDir = new ChartWorkflow(constructorDir.toString());
            JFreeChart chart = makerWithConstructorDir.createTradingRecordChart(barSeries, "Test Strategy",
                    tradingRecord);

            // Save to custom directory (not constructor directory)
            Optional<Path> result = makerWithConstructorDir.saveChartImage(chart, barSeries, null,
                    customDir.toString());

            assertTrue(result.isPresent(), "Chart should be saved");
            assertTrue(result.get().startsWith(customDir),
                    "Chart should be saved in custom directory, not constructor directory");
            assertFalse(result.get().startsWith(constructorDir), "Chart should NOT be saved in constructor directory");
        } finally {
            if (Files.exists(constructorDir)) {
                Files.walk(constructorDir).sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
            }
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithStringDirectoryWorksWithoutConstructorDirectory() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            // ChartWorkflow created without save directory
            ChartWorkflow makerWithoutDir = new ChartWorkflow();
            JFreeChart chart = makerWithoutDir.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

            // Should still work with custom directory parameter
            Optional<Path> result = makerWithoutDir.saveChartImage(chart, barSeries, null, customDir.toString());

            assertTrue(result.isPresent(),
                    "Chart should be saved even when ChartWorkflow has no constructor directory");
            assertTrue(Files.exists(result.get()), "Saved chart file should exist");
            assertTrue(result.get().startsWith(customDir), "Chart should be saved in the specified custom directory");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithStringDirectoryRejectsNullDirectory() {
        JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        assertThrows(IllegalArgumentException.class,
                () -> chartWorkflow.saveChartImage(chart, barSeries, null, (String) null),
                "Should reject null String directory");
    }

    @Test
    public void testSaveChartImageWithStringDirectoryRejectsEmptyDirectory() {
        JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.saveChartImage(chart, barSeries, null, ""),
                "Should reject empty String directory");
    }

    @Test
    public void testSaveChartImageWithStringDirectoryRejectsBlankDirectory() {
        JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
        assertThrows(IllegalArgumentException.class, () -> chartWorkflow.saveChartImage(chart, barSeries, null, "   "),
                "Should reject blank String directory");
    }

    @Test
    public void testSaveChartImageWithStringDirectoryRejectsNullChart() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            assertThrows(IllegalArgumentException.class,
                    () -> chartWorkflow.saveChartImage(null, barSeries, null, customDir.toString()),
                    "Should reject null chart");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithStringDirectoryRejectsNullSeries() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
            assertThrows(IllegalArgumentException.class,
                    () -> chartWorkflow.saveChartImage(chart, null, null, customDir.toString()),
                    "Should reject null series");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithStringDirectoryAutoGeneratesFileNameWhenNull() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
            Optional<Path> result = chartWorkflow.saveChartImage(chart, barSeries, null, customDir.toString());

            assertTrue(result.isPresent(), "Chart should be saved even with null filename");
            assertTrue(Files.exists(result.get()), "Saved chart file should exist");
            // When filename is null, it should use chart title or series name
            String fileName = result.get().getFileName().toString();
            assertTrue(fileName.endsWith(".jpg"), "File should have .jpg extension");
            assertFalse(fileName.isEmpty(), "File should have a generated name");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithStringDirectoryUsesProvidedFileName() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            JFreeChart chart = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
            String expectedFileName = "MyCustomChartName";
            Optional<Path> result = chartWorkflow.saveChartImage(chart, barSeries, expectedFileName,
                    customDir.toString());

            assertTrue(result.isPresent(), "Chart should be saved");
            assertTrue(Files.exists(result.get()), "Saved chart file should exist");
            String actualFileName = result.get().getFileName().toString();
            assertTrue(actualFileName.contains(expectedFileName), "File should contain the provided filename");
            assertTrue(actualFileName.endsWith(".jpg"), "File should have .jpg extension");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
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
    public void testSaveChartImageWithPathAndStringDirectoryProduceSameResult() throws IOException {
        Path customDir = Files.createTempDirectory("ChartWorkflow-custom-dir");
        try {
            JFreeChart chart1 = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);
            JFreeChart chart2 = chartWorkflow.createTradingRecordChart(barSeries, "Test Strategy", tradingRecord);

            Optional<Path> resultPath = chartWorkflow.saveChartImage(chart1, barSeries, customDir);
            Optional<Path> resultString = chartWorkflow.saveChartImage(chart2, barSeries, null, customDir.toString());

            assertTrue(resultPath.isPresent(), "Path version should save chart");
            assertTrue(resultString.isPresent(), "String version should save chart");
            assertTrue(Files.exists(resultPath.get()), "Path version file should exist");
            assertTrue(Files.exists(resultString.get()), "String version file should exist");
            // Both should save to the same directory
            assertEquals(resultPath.get().getParent(), resultString.get().getParent(),
                    "Both methods should save to the same directory");
        } finally {
            if (Files.exists(customDir)) {
                Files.walk(customDir).sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
            }
        }
    }

}
