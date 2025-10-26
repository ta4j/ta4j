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

import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ui.ApplicationFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Rectangle;

/**
 * Swing-based {@link ChartDisplayer} that renders charts in an
 * {@link ApplicationFrame}.
 */
final class SwingChartDisplayer implements ChartDisplayer {

    static final String DISPLAY_SCALE_PROPERTY = "ta4j.chart.displayScale";
    static final double DEFAULT_DISPLAY_SCALE = 0.75;
    private static final int DEFAULT_DISPLAY_WIDTH = 1920;
    private static final int DEFAULT_DISPLAY_HEIGHT = 1200;
    private static final int MIN_DISPLAY_WIDTH = 800;
    private static final int MIN_DISPLAY_HEIGHT = 600;

    private static final Logger LOG = LoggerFactory.getLogger(SwingChartDisplayer.class);

    @Override
    public void display(JFreeChart chart) {
        ChartPanel panel = new ChartPanel(chart);
        panel.setFillZoomRectangle(true);
        panel.setMouseWheelEnabled(true);
        panel.setDomainZoomable(true);
        panel.setPreferredSize(determineDisplaySize());

        ApplicationFrame frame = new ApplicationFrame("Ta4j-examples");
        frame.setContentPane(panel);
        frame.pack();
        frame.setVisible(true);
    }

    Dimension determineDisplaySize() {
        double displayScale = resolveDisplayScale();

        try {
            Rectangle bounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            if (bounds != null && bounds.getWidth() > 0 && bounds.getHeight() > 0) {
                int width = (int) Math.round(bounds.getWidth() * displayScale);
                int height = (int) Math.round(bounds.getHeight() * displayScale);
                width = Math.max(MIN_DISPLAY_WIDTH, width);
                height = Math.max(MIN_DISPLAY_HEIGHT, height);
                return new Dimension(width, height);
            }
        } catch (HeadlessException headlessEx) {
            LOG.debug("Headless environment detected while determining chart display size", headlessEx);
        } catch (Exception ex) {
            LOG.warn("Unable to determine screen bounds for chart display size", ex);
        }

        int fallbackWidth = (int) Math.round(DEFAULT_DISPLAY_WIDTH * displayScale);
        int fallbackHeight = (int) Math.round(DEFAULT_DISPLAY_HEIGHT * displayScale);
        fallbackWidth = Math.max(MIN_DISPLAY_WIDTH, fallbackWidth);
        fallbackHeight = Math.max(MIN_DISPLAY_HEIGHT, fallbackHeight);
        return new Dimension(fallbackWidth, fallbackHeight);
    }

    double resolveDisplayScale() {
        String configuredScale = System.getProperty(DISPLAY_SCALE_PROPERTY);
        if (configuredScale != null) {
            try {
                double parsedValue = Double.parseDouble(configuredScale);
                if (parsedValue > 0.1 && parsedValue <= 1.0) {
                    return parsedValue;
                }
                LOG.warn("Ignoring display scale property {} outside accepted range (0.1, 1.0]: {}",
                        DISPLAY_SCALE_PROPERTY, configuredScale);
            } catch (NumberFormatException numberFormatException) {
                LOG.warn("Unable to parse display scale property {} value: {}", DISPLAY_SCALE_PROPERTY, configuredScale,
                        numberFormatException);
            }
        }
        return DEFAULT_DISPLAY_SCALE;
    }
}
