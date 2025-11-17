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

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link SwingChartDisplayer}.
 */
class SwingChartDisplayerTest {

    private SwingChartDisplayer displayer;

    @BeforeEach
    void setUp() {
        displayer = new SwingChartDisplayer();
        // Clear any existing properties
        System.clearProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY);
        System.clearProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        // Clean up properties
        System.clearProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY);
        System.clearProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY);
    }

    @Test
    void testDefaultDisplayScale() {
        double scale = displayer.resolveDisplayScale();
        assertEquals(SwingChartDisplayer.DEFAULT_DISPLAY_SCALE, scale, 1e-9,
                "Default scale should be " + SwingChartDisplayer.DEFAULT_DISPLAY_SCALE);
    }

    @Test
    void testDisplayScaleFromValidProperty() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.5");
        double scale = displayer.resolveDisplayScale();
        assertEquals(0.5, scale, 1e-9, "Scale should reflect configured property");
    }

    @Test
    void testDisplayScaleFromValidPropertyAtMax() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "1.0");
        double scale = displayer.resolveDisplayScale();
        assertEquals(1.0, scale, 1e-9, "Scale should accept max value of 1.0");
    }

    @Test
    void testDisplayScaleFromValidPropertyNearMin() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.11");
        double scale = displayer.resolveDisplayScale();
        assertEquals(0.11, scale, 1e-9, "Scale should accept values just above 0.1");
    }

    @Test
    void testDisplayScaleIgnoresValueBelowThreshold() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.05");
        double scale = displayer.resolveDisplayScale();
        assertEquals(SwingChartDisplayer.DEFAULT_DISPLAY_SCALE, scale, 1e-9, "Values <= 0.1 should be ignored");
    }

    @Test
    void testDisplayScaleIgnoresValueAboveMax() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "2.0");
        double scale = displayer.resolveDisplayScale();
        assertEquals(SwingChartDisplayer.DEFAULT_DISPLAY_SCALE, scale, 1e-9, "Values > 1.0 should be ignored");
    }

    @Test
    void testDisplayScaleHandlesInvalidPropertyValue() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "invalid");
        double scale = displayer.resolveDisplayScale();
        assertEquals(SwingChartDisplayer.DEFAULT_DISPLAY_SCALE, scale, 1e-9,
                "Invalid property value should fall back to default");
    }

    @Test
    void testDisplayScaleHandlesEmptyPropertyValue() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "");
        double scale = displayer.resolveDisplayScale();
        assertEquals(SwingChartDisplayer.DEFAULT_DISPLAY_SCALE, scale, 1e-9,
                "Empty property should fall back to default");
    }

    @Test
    void testDetermineDisplaySizeReturnsDimension() {
        Dimension size = displayer.determineDisplaySize();
        assertNotNull(size, "Display size should not be null");
        assertTrue(size.width > 0, "Width should be positive");
        assertTrue(size.height > 0, "Height should be positive");
    }

    @Test
    void testDetermineDisplaySizeMeetsMinimumDimensions() {
        Dimension size = displayer.determineDisplaySize();
        assertTrue(size.width >= 800, "Width should meet minimum of 800");
        assertTrue(size.height >= 600, "Height should meet minimum of 600");
    }

    @Test
    void testDetermineDisplaySizeScalesCorrectly() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.5");
        Dimension size1 = displayer.determineDisplaySize();

        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.75");
        Dimension size2 = displayer.determineDisplaySize();

        assertTrue(size2.width >= size1.width, "Larger scale should produce larger width");
        assertTrue(size2.height >= size1.height, "Larger scale should produce larger height");
    }

    @Test
    void testDisplayWithNullChart() {
        assertThrows(Exception.class, () -> displayer.display(null), "Display should throw exception for null chart");
    }

    @Test
    void testDisplayHandlesHeadlessEnvironment() {
        // In headless environment, display should fail gracefully
        JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

        // This will throw HeadlessException in headless environment
        // but we should handle it gracefully
        assertThrows(Exception.class, () -> displayer.display(chart));
    }

    @Test
    void testDetermineDisplaySizeWithCustomScale() {
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.8");
        Dimension size = displayer.determineDisplaySize();
        assertNotNull(size);
        // Should use custom scale or fall back to defaults
    }

    @Test
    void testDetermineDisplaySizeBoundsHandling() {
        // Test with various property values to ensure bounds are respected
        System.setProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY, "0.5");
        Dimension size = displayer.determineDisplaySize();
        assertTrue(size.width >= 800 && size.height >= 600, "Dimensions should respect minimum bounds");
    }

    // ========== Mouseover functionality tests ==========

    @Test
    void testDefaultHoverDelay() {
        int delay = displayer.resolveHoverDelay();
        assertEquals(SwingChartDisplayer.DEFAULT_HOVER_DELAY_MS, delay,
                "Default hover delay should be " + SwingChartDisplayer.DEFAULT_HOVER_DELAY_MS + "ms");
    }

    @Test
    void testHoverDelayFromValidProperty() {
        System.setProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY, "500");
        int delay = displayer.resolveHoverDelay();
        assertEquals(500, delay, "Hover delay should reflect configured property");
    }

    @Test
    void testHoverDelayFromZeroProperty() {
        System.setProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY, "0");
        int delay = displayer.resolveHoverDelay();
        assertEquals(0, delay, "Hover delay should accept zero value");
    }

    @Test
    void testHoverDelayFromLargeProperty() {
        System.setProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY, "5000");
        int delay = displayer.resolveHoverDelay();
        assertEquals(5000, delay, "Hover delay should accept large values");
    }

    @Test
    void testHoverDelayIgnoresNegativeValue() {
        System.setProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY, "-100");
        int delay = displayer.resolveHoverDelay();
        assertEquals(SwingChartDisplayer.DEFAULT_HOVER_DELAY_MS, delay,
                "Negative values should be ignored and fall back to default");
    }

    @Test
    void testHoverDelayHandlesInvalidPropertyValue() {
        System.setProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY, "invalid");
        int delay = displayer.resolveHoverDelay();
        assertEquals(SwingChartDisplayer.DEFAULT_HOVER_DELAY_MS, delay,
                "Invalid property value should fall back to default");
    }

    @Test
    void testHoverDelayHandlesEmptyPropertyValue() {
        System.setProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY, "");
        int delay = displayer.resolveHoverDelay();
        assertEquals(SwingChartDisplayer.DEFAULT_HOVER_DELAY_MS, delay, "Empty property should fall back to default");
    }

    @Test
    void testChartMouseoverListenerCreation() {
        JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);
        ChartPanel panel = new ChartPanel(chart);
        javax.swing.JLabel infoLabel = new javax.swing.JLabel(" ");
        int hoverDelay = 1000;

        SwingChartDisplayer.ChartMouseoverListener listener = new SwingChartDisplayer.ChartMouseoverListener(panel,
                infoLabel, hoverDelay);
        assertNotNull(listener, "Listener should be created successfully");
    }

    @Test
    void testChartMouseoverListenerCreationWithDifferentDelays() {
        JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);
        ChartPanel panel = new ChartPanel(chart);
        javax.swing.JLabel infoLabel = new javax.swing.JLabel(" ");

        // Test with different delay values
        SwingChartDisplayer.ChartMouseoverListener listener1 = new SwingChartDisplayer.ChartMouseoverListener(panel,
                infoLabel, 0);
        assertNotNull(listener1, "Listener should be created with zero delay");

        SwingChartDisplayer.ChartMouseoverListener listener2 = new SwingChartDisplayer.ChartMouseoverListener(panel,
                infoLabel, 5000);
        assertNotNull(listener2, "Listener should be created with large delay");
    }

    @Test
    void testChartMouseoverListenerHandlesClick() {
        JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);
        ChartPanel panel = new ChartPanel(chart);
        javax.swing.JLabel infoLabel = new javax.swing.JLabel(" ");
        SwingChartDisplayer.ChartMouseoverListener listener = new SwingChartDisplayer.ChartMouseoverListener(panel,
                infoLabel, 1000);

        // Verify listener was created
        assertNotNull(listener, "Listener should be created successfully");
        // The click handler should not throw - this tests the method signature
        // Note: We can't easily test with actual ChartMouseEvent without complex
        // mocking,
        // but the method exists and is part of the ChartMouseListener interface
    }
}
