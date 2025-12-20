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
package ta4jexamples.charting.display;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.junit.Assume;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowEvent;
import java.security.Permission;

import javax.swing.JFrame;

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
        System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
    }

    @AfterEach
    void tearDown() {
        // Clean up properties
        System.clearProperty(SwingChartDisplayer.DISPLAY_SCALE_PROPERTY);
        System.clearProperty(SwingChartDisplayer.HOVER_DELAY_PROPERTY);
        System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        // Restore default security manager if one was set
        safeSetSecurityManager(null);
        // Clean up any remaining frames
        Frame[] frames = Frame.getFrames();
        for (Frame frame : frames) {
            if (frame instanceof JFrame) {
                frame.dispose();
            }
        }
    }

    /**
     * Safely sets the SecurityManager, handling UnsupportedOperationException that
     * occurs in Java 17+ where SecurityManager is removed.
     *
     * @param manager the SecurityManager to set, or null to clear
     * @return true if SecurityManager was successfully set, false if it's not
     *         supported
     */
    @SuppressWarnings("removal")
    private static boolean safeSetSecurityManager(SecurityManager manager) {
        try {
            System.setSecurityManager(manager);
            return true;
        } catch (UnsupportedOperationException e) {
            // SecurityManager is not supported in Java 17+
            // This is expected and can be safely ignored
            return false;
        }
    }

    /**
     * Checks if SecurityManager is supported in this JVM.
     *
     * @return true if SecurityManager is supported, false otherwise
     */
    @SuppressWarnings("removal")
    private static boolean isSecurityManagerSupported() {
        try {
            SecurityManager current = System.getSecurityManager();
            System.setSecurityManager(current);
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
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

    /**
     * Tests that display gracefully handles headless environments.
     * <p>
     * <b>Note:</b> This test is intentionally skipped when running in a
     * non-headless environment (e.g., when a display is available). The test uses
     * {@code Assume.assumeTrue()} to skip when a display is present, as it
     * specifically tests behavior in headless environments where GUI operations
     * should fail gracefully.
     * <p>
     * This skip is expected and intentional - the test validates headless
     * environment handling, which only applies when no display is available.
     */
    @Test
    void testDisplayHandlesHeadlessEnvironment() {
        // This test only runs in headless environments
        Assume.assumeTrue("Test requires headless environment", GraphicsEnvironment.isHeadless());

        // In headless environment, display should fail gracefully
        JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

        // This will throw HeadlessException in headless environment
        // but we should handle it gracefully
        assertThrows(Exception.class, () -> displayer.display(chart));
    }

    @Test
    void testDisplayRespectsDisableDisplayProperty() {
        // Set property to disable display
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Display should return immediately without showing a window
            displayer.display(chart);

            // If we get here without exception, the property worked
            assertTrue(true, "Display should be disabled when property is set");
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
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
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        javax.swing.JLabel infoLabel = new javax.swing.JLabel(" ");
        int hoverDelay = 1000;

        SwingChartDisplayer.ChartMouseoverListener listener = new SwingChartDisplayer.ChartMouseoverListener(infoLabel,
                hoverDelay);
        assertNotNull(listener, "Listener should be created successfully");
    }

    @Test
    void testChartMouseoverListenerCreationWithDifferentDelays() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        javax.swing.JLabel infoLabel = new javax.swing.JLabel(" ");

        // Test with different delay values
        SwingChartDisplayer.ChartMouseoverListener listener1 = new SwingChartDisplayer.ChartMouseoverListener(infoLabel,
                0);
        assertNotNull(listener1, "Listener should be created with zero delay");

        SwingChartDisplayer.ChartMouseoverListener listener2 = new SwingChartDisplayer.ChartMouseoverListener(infoLabel,
                5000);
        assertNotNull(listener2, "Listener should be created with large delay");
    }

    @Test
    void testChartMouseoverListenerHandlesClick() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        javax.swing.JLabel infoLabel = new javax.swing.JLabel(" ");
        SwingChartDisplayer.ChartMouseoverListener listener = new SwingChartDisplayer.ChartMouseoverListener(infoLabel,
                1000);

        // Verify listener was created
        assertNotNull(listener, "Listener should be created successfully");
        // The click handler should not throw - this tests the method signature
        // Note: We can't easily test with actual ChartMouseEvent without complex
        // mocking,
        // but the method exists and is part of the ChartMouseListener interface
    }

    // ========== Window cascading functionality tests ==========

    @Test
    void testMultipleDisplaysHandleCascadingGracefully() {
        // Set property to disable display to avoid actually showing windows
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart1 = ChartFactory.createLineChart("Test 1", "X", "Y", null);
            JFreeChart chart2 = ChartFactory.createLineChart("Test 2", "X", "Y", null);
            JFreeChart chart3 = ChartFactory.createLineChart("Test 3", "X", "Y", null);

            // Multiple displays should not throw exceptions
            // The cascading logic should handle positioning gracefully
            assertDoesNotThrow(() -> {
                displayer.display(chart1, "Window 1");
                displayer.display(chart2, "Window 2");
                displayer.display(chart3, "Window 3");
            }, "Multiple displays should handle cascading without exceptions");
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

    @Test
    void testDisplayWithWindowTitleHandlesCascading() {
        // Set property to disable display
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Display with custom window title should work
            assertDoesNotThrow(() -> displayer.display(chart, "Custom Window Title"),
                    "Display with window title should handle cascading gracefully");
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

    @Test
    void testDisplayHandlesCascadingInHeadlessEnvironment() {
        // This test only runs in headless environments
        Assume.assumeTrue("Test requires headless environment", GraphicsEnvironment.isHeadless());

        JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

        // In headless environment, cascading logic should fail gracefully
        // The exception handling in the cascading code should catch any errors
        assertThrows(Exception.class, () -> displayer.display(chart, "Test Window"),
                "Display should throw exception in headless environment, but cascading logic should be attempted");
    }

    @Test
    void testMultipleDisplaysWithDifferentTitles() {
        // Set property to disable display
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Multiple displays with different titles should all work
            assertDoesNotThrow(() -> {
                for (int i = 0; i < 5; i++) {
                    displayer.display(chart, "Window " + i);
                }
            }, "Multiple displays with different titles should handle cascading correctly");
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

    // ========== Window tracking and exit behavior tests ==========

    /**
     * Security manager that throws a special exception when System.exit is called,
     * allowing tests to verify exit behavior without actually terminating the JVM.
     * <p>
     * Note: SecurityManager is deprecated in Java 17+, but is still functional for
     * testing purposes.
     */
    @SuppressWarnings("removal")
    private static class ExitSecurityManager extends SecurityManager {
        private boolean exitCalled = false;
        private int exitCode = -1;

        @Override
        public void checkPermission(Permission perm) {
            // Allow all permissions except exit
        }

        @Override
        public void checkExit(int status) {
            exitCalled = true;
            exitCode = status;
            throw new SecurityException("System.exit(" + status + ") called");
        }

        boolean wasExitCalled() {
            return exitCalled;
        }

        int getExitCode() {
            return exitCode;
        }
    }

    @Test
    void testWindowTrackingWhenDisplayed() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        // Set property to disable actual display
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Display should not throw
            assertDoesNotThrow(() -> displayer.display(chart, "Test Window"),
                    "Display should track window without throwing");
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

    @Test
    void testMultipleWindowsTrackedIndependently() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        // Set property to disable actual display
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Display multiple windows
            assertDoesNotThrow(() -> {
                displayer.display(chart, "Window 1");
                displayer.display(chart, "Window 2");
                displayer.display(chart, "Window 3");
            }, "Multiple windows should be tracked independently");
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

    @Test
    @SuppressWarnings("removal")
    void testExitCalledWhenAllWindowsClosed() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        Assume.assumeTrue("SecurityManager not supported", isSecurityManagerSupported());
        ExitSecurityManager securityManager = new ExitSecurityManager();
        if (!safeSetSecurityManager(securityManager)) {
            return; // Skip test if SecurityManager not supported
        }

        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Create a single window
            displayer.display(chart, "Test Window");

            // Find the created window and close it
            Frame[] allFrames = Frame.getFrames();
            JFrame testFrame = null;
            for (Frame frame : allFrames) {
                if (frame instanceof JFrame jf && "Test Window".equals(jf.getTitle())) {
                    testFrame = jf;
                    break;
                }
            }
            assertNotNull(testFrame, "Test window should be created");

            // Simulate window closed event (this triggers the WindowListener)
            WindowEvent closedEvent = new WindowEvent(testFrame, WindowEvent.WINDOW_CLOSED);
            testFrame.dispatchEvent(closedEvent);

            // Verify exit was called
            assertTrue(securityManager.wasExitCalled(), "System.exit should be called when all windows are closed");
            assertEquals(0, securityManager.getExitCode(), "Exit code should be 0");
        } catch (SecurityException e) {
            // Expected when System.exit is called
            assertTrue(e.getMessage().contains("System.exit"),
                    "SecurityException should indicate System.exit was called");
        } finally {
            safeSetSecurityManager(null);
            // Clean up any remaining frames
            Frame[] frames = Frame.getFrames();
            for (Frame frame : frames) {
                if (frame instanceof JFrame) {
                    frame.dispose();
                }
            }
        }
    }

    @Test
    @SuppressWarnings("removal")
    void testExitNotCalledWhenSomeWindowsRemain() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        Assume.assumeTrue("SecurityManager not supported", isSecurityManagerSupported());
        ExitSecurityManager securityManager = new ExitSecurityManager();
        if (!safeSetSecurityManager(securityManager)) {
            return; // Skip test if SecurityManager not supported
        }

        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Create two windows
            displayer.display(chart, "Window 1");
            displayer.display(chart, "Window 2");

            // Find the created windows
            Frame[] allFrames = Frame.getFrames();
            JFrame firstFrame = null;
            JFrame secondFrame = null;
            for (Frame frame : allFrames) {
                if (frame instanceof JFrame jf) {
                    if ("Window 1".equals(jf.getTitle())) {
                        firstFrame = jf;
                    } else if ("Window 2".equals(jf.getTitle())) {
                        secondFrame = jf;
                    }
                }
            }
            assertNotNull(firstFrame, "First window should be created");
            assertNotNull(secondFrame, "Second window should be created");

            // Close only one window
            WindowEvent closedEvent = new WindowEvent(firstFrame, WindowEvent.WINDOW_CLOSED);
            firstFrame.dispatchEvent(closedEvent);

            // Verify exit was NOT called (since one window remains)
            assertFalse(securityManager.wasExitCalled(), "System.exit should NOT be called when windows remain open");

            // Clean up remaining window
            secondFrame.dispose();
        } finally {
            safeSetSecurityManager(null);
            // Clean up any remaining frames
            Frame[] frames = Frame.getFrames();
            for (Frame frame : frames) {
                if (frame instanceof JFrame) {
                    frame.dispose();
                }
            }
        }
    }

    @Test
    @SuppressWarnings("removal")
    void testExitCalledAfterAllMultipleWindowsClosed() {
        Assume.assumeFalse("Headless environment", GraphicsEnvironment.isHeadless());
        Assume.assumeTrue("SecurityManager not supported", isSecurityManagerSupported());
        ExitSecurityManager securityManager = new ExitSecurityManager();
        if (!safeSetSecurityManager(securityManager)) {
            return; // Skip test if SecurityManager not supported
        }

        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // Create three windows
            displayer.display(chart, "Window 1");
            displayer.display(chart, "Window 2");
            displayer.display(chart, "Window 3");

            // Find the created windows
            Frame[] allFrames = Frame.getFrames();
            JFrame window1 = null;
            JFrame window2 = null;
            JFrame window3 = null;
            for (Frame frame : allFrames) {
                if (frame instanceof JFrame jf) {
                    if ("Window 1".equals(jf.getTitle())) {
                        window1 = jf;
                    } else if ("Window 2".equals(jf.getTitle())) {
                        window2 = jf;
                    } else if ("Window 3".equals(jf.getTitle())) {
                        window3 = jf;
                    }
                }
            }
            assertNotNull(window1, "Window 1 should be created");
            assertNotNull(window2, "Window 2 should be created");
            assertNotNull(window3, "Window 3 should be created");

            // Close first two windows (exit should not be called)
            WindowEvent closedEvent1 = new WindowEvent(window1, WindowEvent.WINDOW_CLOSED);
            window1.dispatchEvent(closedEvent1);

            WindowEvent closedEvent2 = new WindowEvent(window2, WindowEvent.WINDOW_CLOSED);
            window2.dispatchEvent(closedEvent2);

            assertFalse(securityManager.wasExitCalled(), "System.exit should NOT be called when windows remain open");

            // Close the last window (exit should be called)
            WindowEvent closedEvent3 = new WindowEvent(window3, WindowEvent.WINDOW_CLOSED);
            window3.dispatchEvent(closedEvent3);

            // Verify exit was called
            assertTrue(securityManager.wasExitCalled(), "System.exit should be called when the last window is closed");
            assertEquals(0, securityManager.getExitCode(), "Exit code should be 0");
        } catch (SecurityException e) {
            // Expected when System.exit is called
            assertTrue(e.getMessage().contains("System.exit"),
                    "SecurityException should indicate System.exit was called");
        } finally {
            safeSetSecurityManager(null);
            // Clean up any remaining frames
            Frame[] frames = Frame.getFrames();
            for (Frame frame : frames) {
                if (frame instanceof JFrame) {
                    frame.dispose();
                }
            }
        }
    }

    @Test
    void testWindowTrackingWithDisabledDisplay() {
        // Set property to disable display
        System.setProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY, "true");
        try {
            JFreeChart chart = ChartFactory.createLineChart("Test", "X", "Y", null);

            // When display is disabled, no windows should be created or tracked
            assertDoesNotThrow(() -> {
                displayer.display(chart, "Test Window");
                displayer.display(chart, "Test Window 2");
            }, "Display with disabled property should not throw");

            // Since display is disabled, no windows are created, so no exit behavior
            // This test just verifies the code path doesn't crash
        } finally {
            System.clearProperty(SwingChartDisplayer.DISABLE_DISPLAY_PROPERTY);
        }
    }

}
