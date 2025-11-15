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

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeriesCollection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Swing-based {@link ChartDisplayer} that renders charts in an
 * {@link ApplicationFrame}.
 *
 * <p>
 * This implementation displays charts in a Swing window with zoom and pan
 * capabilities. The display size can be configured via the
 * {@link #DISPLAY_SCALE_PROPERTY system property}.
 * </p>
 *
 * @since 0.19
 */
final class SwingChartDisplayer implements ChartDisplayer {

    /**
     * System property key for chart display scale configuration.
     *
     * @since 0.19
     */
    static final String DISPLAY_SCALE_PROPERTY = "ta4j.chart.displayScale";

    /**
     * System property key for mouseover hover delay in milliseconds.
     *
     * @since 0.19
     */
    static final String HOVER_DELAY_PROPERTY = "ta4j.chart.hoverDelay";

    /**
     * Default chart display scale.
     *
     * @since 0.19
     */
    static final double DEFAULT_DISPLAY_SCALE = 0.75;
    private static final int DEFAULT_DISPLAY_WIDTH = 1920;
    private static final int DEFAULT_DISPLAY_HEIGHT = 1200;
    private static final int MIN_DISPLAY_WIDTH = 800;
    private static final int MIN_DISPLAY_HEIGHT = 600;

    /**
     * Default mouseover hover delay in milliseconds.
     *
     * @since 0.19
     */
    static final int DEFAULT_HOVER_DELAY_MS = 100;

    private static final Logger LOG = LogManager.getLogger(SwingChartDisplayer.class);

    @Override
    public void display(JFreeChart chart) {
        display(chart, "Ta4j-examples");
    }

    @Override
    public void display(JFreeChart chart, String windowTitle) {
        // Serialize and deserialize the chart to create a deep copy that prevents
        // ChartPanel from modifying the original
        JFreeChart chartClone;
        try {
            chartClone = deepCopyChart(chart);
        } catch (Exception e) {
            LOG.debug("Failed to deep copy chart, falling back to shallow clone", e);
            try {
                chartClone = (JFreeChart) chart.clone();
            } catch (CloneNotSupportedException cloneEx) {
                LOG.debug("Failed to clone chart, using original chart for display", cloneEx);
                chartClone = chart;
            }
        }
        ChartPanel panel = new ChartPanel(chartClone);
        panel.setFillZoomRectangle(true);
        panel.setMouseWheelEnabled(true);
        panel.setDomainZoomable(true);
        panel.setDisplayToolTips(false);
        panel.setPreferredSize(determineDisplaySize());

        // Create info panel for mouseover data
        JLabel infoLabel = new JLabel(" ");
        infoLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        infoLabel.setForeground(Color.LIGHT_GRAY);
        infoLabel.setBackground(Color.BLACK);
        infoLabel.setOpaque(true);
        infoLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(4, 8, 4, 8));

        // Create container panel with chart and info label
        JPanel containerPanel = new JPanel(new BorderLayout());
        containerPanel.add(panel, BorderLayout.CENTER);
        containerPanel.add(infoLabel, BorderLayout.NORTH);

        // Add mouseover listener
        int hoverDelay = resolveHoverDelay();
        ChartMouseoverListener mouseoverListener = new ChartMouseoverListener(chartClone, panel, infoLabel, hoverDelay);
        panel.addChartMouseListener(mouseoverListener);

        String title = windowTitle != null && !windowTitle.trim().isEmpty() ? windowTitle : "Ta4j-examples";
        ApplicationFrame frame = new ApplicationFrame(title);
        frame.setContentPane(containerPanel);
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
                LOG.debug("Ignoring display scale property {} outside accepted range (0.1, 1.0]: {}",
                        DISPLAY_SCALE_PROPERTY, configuredScale);
            } catch (NumberFormatException numberFormatException) {
                LOG.debug("Unable to parse display scale property {} value: {}", DISPLAY_SCALE_PROPERTY,
                        configuredScale, numberFormatException);
            }
        }
        return DEFAULT_DISPLAY_SCALE;
    }

    int resolveHoverDelay() {
        String configuredDelay = System.getProperty(HOVER_DELAY_PROPERTY);
        if (configuredDelay != null) {
            try {
                int parsedValue = Integer.parseInt(configuredDelay);
                if (parsedValue >= 0) {
                    return parsedValue;
                }
                LOG.debug("Ignoring hover delay property {} with negative value: {}", HOVER_DELAY_PROPERTY,
                        configuredDelay);
            } catch (NumberFormatException numberFormatException) {
                LOG.debug("Unable to parse hover delay property {} value: {}", HOVER_DELAY_PROPERTY, configuredDelay,
                        numberFormatException);
            }
        }
        return DEFAULT_HOVER_DELAY_MS;
    }

    private JFreeChart deepCopyChart(JFreeChart chart) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(chart);
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            return (JFreeChart) ois.readObject();
        }
    }

    /**
     * Mouse listener that displays OHLC data for candles and indicator values on
     * mouseover.
     *
     * @since 0.19
     */
    static class ChartMouseoverListener implements ChartMouseListener {

        private final ChartPanel chartPanel;
        private final JLabel infoLabel;
        private final int hoverDelay;
        private Timer hoverTimer;
        private String lastDisplayedText;
        private ChartMouseEvent lastEvent;

        ChartMouseoverListener(JFreeChart chart, ChartPanel chartPanel, JLabel infoLabel, int hoverDelay) {
            this.chartPanel = chartPanel;
            this.infoLabel = infoLabel;
            this.hoverDelay = hoverDelay;
        }

        @Override
        public void chartMouseClicked(ChartMouseEvent event) {
            // No action on click
        }

        @Override
        public void chartMouseMoved(ChartMouseEvent event) {
            // Cancel any pending timer
            if (hoverTimer != null) {
                hoverTimer.stop();
                hoverTimer = null;
            }

            // Clear display immediately when mouse moves
            if (lastDisplayedText != null) {
                infoLabel.setText(" ");
                lastDisplayedText = null;
            }

            // Store the event for later use
            lastEvent = event;

            // Start new timer to show data after delay
            hoverTimer = new Timer(hoverDelay, new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    displayMouseoverData(lastEvent);
                }
            });
            hoverTimer.setRepeats(false);
            hoverTimer.start();
        }

        private void displayMouseoverData(ChartMouseEvent event) {
            try {
                if (event == null) {
                    return;
                }

                ChartEntity entity = event.getEntity();
                if (entity instanceof XYItemEntity xyItemEntity) {
                    XYDataset dataset = xyItemEntity.getDataset();
                    int seriesIndex = xyItemEntity.getSeriesIndex();
                    int itemIndex = xyItemEntity.getItem();

                    String displayText = extractDataText(dataset, seriesIndex, itemIndex);
                    if (displayText != null && !displayText.isEmpty()) {
                        infoLabel.setText(displayText);
                        lastDisplayedText = displayText;
                    }
                } else {
                    // Try to find data point from mouse coordinates
                    if (chartPanel.getChartRenderingInfo() != null) {
                        PlotRenderingInfo plotInfo = chartPanel.getChartRenderingInfo().getPlotInfo();
                        if (plotInfo != null) {
                            MouseEvent mouseEvent = event.getTrigger();
                            if (mouseEvent != null) {
                                String displayText = findDataFromCoordinates(mouseEvent.getX(), mouseEvent.getY(),
                                        plotInfo);
                                if (displayText != null && !displayText.isEmpty()) {
                                    infoLabel.setText(displayText);
                                    lastDisplayedText = displayText;
                                }
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                LOG.debug("Error displaying mouseover data", ex);
            }
        }

        private String extractDataText(XYDataset dataset, int seriesIndex, int itemIndex) {
            if (dataset instanceof DefaultOHLCDataset ohlcDataset) {
                try {
                    // DefaultOHLCDataset stores OHLC data - access OHLC values directly
                    double xValue = dataset.getXValue(seriesIndex, itemIndex);
                    double open = ohlcDataset.getOpenValue(seriesIndex, itemIndex);
                    double high = ohlcDataset.getHighValue(seriesIndex, itemIndex);
                    double low = ohlcDataset.getLowValue(seriesIndex, itemIndex);
                    double close = ohlcDataset.getCloseValue(seriesIndex, itemIndex);
                    double volume = ohlcDataset.getVolumeValue(seriesIndex, itemIndex);

                    DecimalFormat priceFormat = new DecimalFormat("#,##0.00###");
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    return String.format("Date: %s | O: %s | H: %s | L: %s | C: %s | V: %s",
                            dateFormat.format(new Date((long) xValue)), priceFormat.format(open),
                            priceFormat.format(high), priceFormat.format(low), priceFormat.format(close),
                            priceFormat.format(volume));
                } catch (Exception ex) {
                    LOG.debug("Error extracting OHLC data", ex);
                }
            } else if (dataset instanceof XYSeriesCollection xyCollection) {
                try {
                    org.jfree.data.xy.XYSeries series = xyCollection.getSeries(seriesIndex);
                    if (series != null && itemIndex < series.getItemCount()) {
                        double x = series.getX(itemIndex).doubleValue();
                        double y = series.getY(itemIndex).doubleValue();
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        DecimalFormat valueFormat = new DecimalFormat("#,##0.00###");
                        return String.format("Date: %s | Value: %s", dateFormat.format(new Date((long) x)),
                                valueFormat.format(y));
                    }
                } catch (Exception ex) {
                    LOG.debug("Error extracting indicator data", ex);
                }
            }
            return null;
        }

        private String findDataFromCoordinates(int x, int y, PlotRenderingInfo plotInfo) {
            // This is a fallback method - try to find nearest data point
            // For now, return null to use entity-based approach
            return null;
        }
    }
}
