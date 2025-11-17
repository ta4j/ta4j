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
import org.junit.Assume;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;

import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ChartHandle}.
 */
class ChartHandleTest {

    private ChartMaker chartMaker;
    private BarSeries barSeries;
    private TradingRecord tradingRecord;
    private ChartHandle handle;

    @BeforeEach
    void setUp() {
        chartMaker = new ChartMaker();
        barSeries = ChartingTestFixtures.standardDailySeries();
        tradingRecord = ChartingTestFixtures.completedTradeRecord(barSeries);
        handle = chartMaker.builder().withTradingRecord(barSeries, "Test Strategy", tradingRecord).build();
    }

    @Test
    void testGetChart() {
        JFreeChart chart = handle.getChart();
        assertNotNull(chart, "Chart should not be null");
    }

    @Test
    void testGetSeries() {
        BarSeries series = handle.getSeries();
        assertNotNull(series, "Series should not be null");
        assertEquals(barSeries, series, "Series should match");
    }

    @Test
    void testDisplay() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        ChartHandle result = handle.display();
        assertSame(handle, result, "display() should return this for chaining");
    }

    @Test
    void testDisplayWithWindowTitle() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        ChartHandle result = handle.display("Custom Window Title");
        assertSame(handle, result, "display() should return this for chaining");
    }

    @Test
    void testSaveWithDirectoryAndFilename() throws IOException {
        Path tempDir = Files.createTempDirectory("chart-handle-test");
        try {
            ChartHandle result = handle.save(tempDir.toString(), "test-chart");
            assertSame(handle, result, "save() should return this for chaining");

            // Verify file was created
            boolean fileExists = Files.list(tempDir)
                    .anyMatch(path -> path.toString().contains("test-chart") && path.toString().endsWith(".jpg"));
            assertTrue(fileExists, "Chart file should be saved");
        } finally {
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
    void testSaveWithPathAndFilename() throws IOException {
        Path tempDir = Files.createTempDirectory("chart-handle-test");
        try {
            ChartHandle result = handle.save(tempDir, "test-chart");
            assertSame(handle, result, "save() should return this for chaining");

            // Verify file was created
            boolean fileExists = Files.list(tempDir)
                    .anyMatch(path -> path.toString().contains("test-chart") && path.toString().endsWith(".jpg"));
            assertTrue(fileExists, "Chart file should be saved");
        } finally {
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
    void testSaveWithFilenameOnly() throws IOException {
        // This test verifies that save() works with filename only
        // It will save to current directory since no constructor directory is set
        Path currentDir = Path.of(System.getProperty("user.dir"));
        ChartHandle result = handle.save("test-chart-filename-only");
        assertSame(handle, result, "save() should return this for chaining");

        // Verify file was created in current directory
        boolean fileExists = Files.list(currentDir)
                .anyMatch(path -> path.toString().contains("test-chart-filename-only")
                        && path.toString().endsWith(".jpg"));
        assertTrue(fileExists, "Chart file should be saved to current directory");

        // Clean up
        Files.list(currentDir)
                .filter(path -> path.toString().contains("test-chart-filename-only")
                        && path.toString().endsWith(".jpg"))
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        // Ignore cleanup errors
                    }
                });
    }

    @Test
    void testSaveToDirectory() throws IOException {
        Path tempDir = Files.createTempDirectory("chart-handle-test");
        try {
            ChartHandle result = handle.saveToDirectory(tempDir.toString());
            assertSame(handle, result, "saveToDirectory() should return this for chaining");

            // Verify file was created with auto-generated filename
            boolean fileExists = Files.list(tempDir).anyMatch(path -> path.toString().endsWith(".jpg"));
            assertTrue(fileExists, "Chart file should be saved with auto-generated filename");
        } finally {
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
    void testSaveToDirectoryWithPath() throws IOException {
        Path tempDir = Files.createTempDirectory("chart-handle-test");
        try {
            ChartHandle result = handle.saveToDirectory(tempDir);
            assertSame(handle, result, "saveToDirectory() should return this for chaining");

            // Verify file was created
            boolean fileExists = Files.list(tempDir).anyMatch(path -> path.toString().endsWith(".jpg"));
            assertTrue(fileExists, "Chart file should be saved");
        } finally {
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
    void testFluentChaining() throws IOException {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        Path tempDir = Files.createTempDirectory("chart-handle-test");
        try {
            ChartHandle result = handle.display()
                    .display("Custom Title")
                    .save(tempDir.toString(), "chained-chart")
                    .saveToDirectory(tempDir);

            assertSame(handle, result, "All methods should return this for chaining");

            // Verify files were created
            long fileCount = Files.list(tempDir).filter(path -> path.toString().endsWith(".jpg")).count();
            assertTrue(fileCount >= 2, "Multiple save operations should create multiple files");
        } finally {
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
    void testSaveWithConstructorDirectory() throws IOException {
        Path tempDir = Files.createTempDirectory("chart-handle-test");
        try {
            ChartMaker makerWithDir = new ChartMaker(tempDir.toString());
            ChartHandle handleWithDir = makerWithDir.builder()
                    .withTradingRecord(barSeries, "Test Strategy", tradingRecord)
                    .build();

            ChartHandle result = handleWithDir.save("constructor-dir-chart");
            assertSame(handleWithDir, result, "save() should return this for chaining");

            // Verify file was created in constructor directory
            boolean fileExists = Files.list(tempDir)
                    .anyMatch(path -> path.toString().contains("constructor-dir-chart")
                            && path.toString().endsWith(".jpg"));
            assertTrue(fileExists, "Chart file should be saved to constructor directory");
        } finally {
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
}
