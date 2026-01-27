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
package ta4jexamples.charting.storage;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

import ta4jexamples.charting.ChartingTestFixtures;

/**
 * Unit tests for {@link FileSystemChartStorage}.
 */
class FileSystemChartStorageTest {

    private FileSystemChartStorage storage;
    private BarSeries barSeries;
    private JFreeChart chart;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        storage = new FileSystemChartStorage(tempDir);
        barSeries = ChartingTestFixtures.standardDailySeries();
        chart = ChartFactory.createCandlestickChart("Test Chart", "Date", "Price",
                ChartingTestFixtures.seriesToDataset(barSeries), true);
    }

    @Test
    void testConstructorWithNullPath() {
        assertThrows(NullPointerException.class, () -> new FileSystemChartStorage(null));
    }

    @Test
    void testSaveWithValidInput() {
        Optional<Path> result = storage.save(chart, barSeries, "Test Strategy", 800, 600);

        assertTrue(result.isPresent(), "Save should return a path");
        assertTrue(Files.exists(result.get()), "Chart file should exist");
        assertTrue(Files.isRegularFile(result.get()), "Saved path should be a file");
    }

    @Test
    void testSaveCreatesDirectories() {
        Optional<Path> result = storage.save(chart, barSeries, "Test Strategy", 800, 600);

        assertTrue(result.isPresent());
        Path parent = result.get().getParent();
        assertTrue(Files.exists(parent), "Parent directories should be created");
        assertTrue(Files.isDirectory(parent), "Parent should be a directory");
    }

    @Test
    void testSaveWithNullChart() {
        assertThrows(NullPointerException.class, () -> storage.save(null, barSeries, "Test Strategy", 800, 600));
    }

    @Test
    void testSaveWithNullSeries() {
        assertThrows(NullPointerException.class, () -> storage.save(chart, null, "Test Strategy", 800, 600));
    }

    @Test
    void testPathSanitization() {
        BarSeries seriesWithSpecialChars = ChartingTestFixtures.seriesWithSpecialChars();

        Optional<Path> result = storage.save(chart, seriesWithSpecialChars, "Test:Strategy/Path", 800, 600);

        assertTrue(result.isPresent());
        // Note: path may contain file system separators, but not special chars in
        // component names
        // The path components themselves should be sanitized
        String fileName = result.get().getFileName().toString();
        assertFalse(fileName.contains(":"), "File name should not contain ':'");
        assertFalse(fileName.contains("?"), "File name should not contain '?'");
        assertFalse(fileName.contains("*"), "File name should not contain '*'");
        assertFalse(fileName.contains("<") || fileName.contains(">"), "File name should not contain angle brackets");
    }

    @Test
    void testPathSanitizationWithNullSeriesName() {
        BarSeries seriesWithNullName = new BaseBarSeriesBuilder().build();

        String chartTitle = "Test Strategy";
        Optional<Path> result = storage.save(chart, seriesWithNullName, chartTitle, 800, 600);

        assertTrue(result.isPresent());
        assertTrue(result.get().toString().endsWith(chartTitle.replaceAll(" ", "_") + ".jpg"),
                "Should use chart title for null series name");
    }

    @Test
    void testPathSanitizationWithEmptySeriesName() {
        BarSeries seriesWithEmptyName = new BaseBarSeriesBuilder().withName("").build();

        String chartTitle = "Test Strategy";
        Optional<Path> result = storage.save(chart, seriesWithEmptyName, chartTitle, 800, 600);

        assertTrue(result.isPresent());
        assertTrue(result.get().toString().endsWith(chartTitle.replaceAll(" ", "_") + ".jpg"),
                "Should use chart title for empty series name");
    }

    @Test
    void testPathSanitizationWhitespaceHandling() {
        BarSeries seriesWithWhitespace = new BaseBarSeriesBuilder().withName("Series   Name  With   Spaces").build();

        Optional<Path> result = storage.save(chart, seriesWithWhitespace, "Strategy  Name", 800, 600);

        assertTrue(result.isPresent());
        String pathString = result.get().toString();
        // Whitespace should be replaced with underscores
        assertFalse(pathString.contains("  "), "Multiple consecutive spaces should be collapsed");
    }

    @Test
    void testPathSanitizationRemovesLeadingAndTrailingDots() {
        BarSeries seriesWithDots = new BaseBarSeriesBuilder().withName("...Series...").build();

        Optional<Path> result = storage.save(chart, seriesWithDots, "...Strategy...", 800, 600);

        assertTrue(result.isPresent());
        String pathString = result.get().toString();
        assertFalse(pathString.contains("..."), "Leading and trailing dots should be removed");
    }

    @Test
    void testSaveWithDifferentChartSizes() {
        Optional<Path> result1 = storage.save(chart, barSeries, "Test Strategy", 1024, 768);
        assertTrue(result1.isPresent(), "Should save with 1024x768");

        Optional<Path> result2 = storage.save(chart, barSeries, "Test Strategy 2", 1920, 1080);
        assertTrue(result2.isPresent(), "Should save with 1920x1080");
    }

    @Test
    void testSaveWithNullChartTitleUsesSeriesName() {
        Optional<Path> result = storage.save(chart, barSeries, null, 800, 600);

        assertTrue(result.isPresent());
    }

    @Test
    void testSaveWithLongChartTitle() {
        String longTitle = "Test Strategy with a very long name that might cause issues "
                + "with path length limits on some file systems";
        Optional<Path> result = storage.save(chart, barSeries, longTitle, 800, 600);

        assertTrue(result.isPresent());
    }

    @Test
    void testSaveIncludesSeriesPeriodDescription() {
        Optional<Path> result = storage.save(chart, barSeries, "Test Strategy", 800, 600);

        assertTrue(result.isPresent());
        // Path should have multiple levels (series/period/title)
        Path parentDir = result.get().getParent();
        assertNotNull(parentDir, "Should have parent directory");
    }

}
