/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.display;

import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.XYItemEntity;
import org.jfree.data.xy.XYDataset;
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
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

/**
 * Swing-based {@link ChartDisplayer} that renders charts in a {@link JFrame}.
 *
 * <p>
 * This implementation displays charts in a Swing window with zoom and pan
 * capabilities. The display size can be configured via the
 * {@link #DISPLAY_SCALE_PROPERTY system property}. Each window closes
 * independently using {@link JFrame#DISPOSE_ON_CLOSE} to prevent closing one
 * window from affecting others. When all chart windows are closed, the program
 * automatically exits.
 * </p>
 *
 * @since 0.19
 */
public final class SwingChartDisplayer implements ChartDisplayer {

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
     * System property key to disable chart display (useful for automated tests).
     * When set to "true", charts will not be displayed and the display method will
     * return immediately without creating any windows.
     *
     * @since 0.19
     */
    public static final String DISABLE_DISPLAY_PROPERTY = "ta4j.chart.disableDisplay";

    /**
     * Default chart display scale.
     *
     * @since 0.19
     */
    static final double DEFAULT_DISPLAY_SCALE = 0.85;
    private static final int DEFAULT_DISPLAY_WIDTH = 1920;
    private static final int DEFAULT_DISPLAY_HEIGHT = 1200;
    private static final int MIN_DISPLAY_WIDTH = 800;
    private static final int MIN_DISPLAY_HEIGHT = 600;

    /**
     * Default mouseover hover delay in milliseconds.
     * <p>
     * Set to 500ms to align with UX best practices. Nielsen Norman Group recommends
     * 300-500ms, and Microsoft uses 500ms for tooltips. This prevents accidental
     * tooltip activations when users move their cursor across the chart.
     *
     * @since 0.19
     */
    static final int DEFAULT_HOVER_DELAY_MS = 500;

    private static final Logger LOG = LogManager.getLogger(SwingChartDisplayer.class);

    /**
     * Static counter to track window positions for cascading multiple chart
     * windows.
     */
    private static int windowCounter = 0;
    private static final int CASCADE_OFFSET_X = 30;
    private static final int CASCADE_OFFSET_Y = 30;

    /**
     * Set of all open chart windows. Used to track when all windows are closed so
     * the program can exit.
     */
    private static final Set<JFrame> openWindows = ConcurrentHashMap.newKeySet();

    @Override
    public void display(JFreeChart chart) {
        display(chart, "Ta4j-examples");
    }

    @Override
    public void display(JFreeChart chart, String windowTitle) {
        // Validate input parameter
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null");
        }

        // Check if display is disabled via system property (useful for automated tests)
        if (isDisplayDisabled()) {
            LOG.debug("Chart display is disabled via system property {}", DISABLE_DISPLAY_PROPERTY);
            return;
        }

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
        ChartMouseoverListener mouseoverListener = new ChartMouseoverListener(infoLabel, hoverDelay);
        panel.addChartMouseListener(mouseoverListener);

        // Add ancestor listener to cleanup timer when component is removed
        panel.addAncestorListener(new AncestorListener() {
            @Override
            public void ancestorAdded(AncestorEvent event) {
                // No action needed
            }

            @Override
            public void ancestorRemoved(AncestorEvent event) {
                mouseoverListener.disposeHoverTimer();
            }

            @Override
            public void ancestorMoved(AncestorEvent event) {
                // No action needed
            }
        });

        String title = windowTitle != null && !windowTitle.trim().isEmpty() ? windowTitle : "Ta4j-examples";
        // Use JFrame instead of ApplicationFrame to avoid EXIT_ON_CLOSE behavior
        // ApplicationFrame sets EXIT_ON_CLOSE which closes all windows
        JFrame frame = new JFrame(title);
        frame.setContentPane(containerPanel);
        frame.pack();
        frame.setAlwaysOnTop(false);
        frame.setAutoRequestFocus(false);
        // Set to DISPOSE_ON_CLOSE so closing one window doesn't close all windows
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Track this window and add listener to exit when all windows are closed
        openWindows.add(frame);
        frame.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {
                // No action needed
            }

            @Override
            public void windowClosing(WindowEvent e) {
                // No action needed - DISPOSE_ON_CLOSE handles the closing
            }

            @Override
            public void windowClosed(WindowEvent e) {
                openWindows.remove(frame);
                // If all windows are closed, exit the program
                if (openWindows.isEmpty()) {
                    LOG.debug("All chart windows closed, exiting program");
                    System.exit(0);
                }
            }

            @Override
            public void windowIconified(WindowEvent e) {
                // No action needed
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
                // No action needed
            }

            @Override
            public void windowActivated(WindowEvent e) {
                // No action needed
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
                // No action needed
            }
        });

        // Cascade windows by offsetting each new window
        int windowIndex = windowCounter++;
        try {
            Rectangle screenBounds = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            if (screenBounds != null) {
                int x = screenBounds.x + (windowIndex * CASCADE_OFFSET_X);
                int y = screenBounds.y + (windowIndex * CASCADE_OFFSET_Y);
                // Ensure window stays within screen bounds
                Dimension frameSize = frame.getSize();
                if (x + frameSize.width > screenBounds.width) {
                    x = screenBounds.x + ((windowIndex % 10) * CASCADE_OFFSET_X);
                }
                if (y + frameSize.height > screenBounds.height) {
                    y = screenBounds.y + ((windowIndex % 10) * CASCADE_OFFSET_Y);
                }
                frame.setLocation(x, y);
            }
        } catch (Exception ex) {
            LOG.debug("Unable to set window position for cascading, using default", ex);
        }

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

    /**
     * Checks if chart display is disabled via system property.
     *
     * @return true if display is disabled, false otherwise
     */
    boolean isDisplayDisabled() {
        String disableDisplay = System.getProperty(DISABLE_DISPLAY_PROPERTY);
        return "true".equalsIgnoreCase(disableDisplay);
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

        private final JLabel infoLabel;
        private final int hoverDelay;
        private final ChartDataExtractor dataExtractor;
        private Timer hoverTimer;
        private String lastDisplayedText;
        private ChartMouseEvent lastEvent;

        ChartMouseoverListener(JLabel infoLabel, int hoverDelay) {
            this(infoLabel, hoverDelay, new ChartDataExtractor());
        }

        ChartMouseoverListener(JLabel infoLabel, int hoverDelay, ChartDataExtractor dataExtractor) {
            this.infoLabel = infoLabel;
            this.hoverDelay = hoverDelay;
            this.dataExtractor = dataExtractor;
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

        /**
         * Stops the hover timer and clears all references to prevent memory leaks when
         * the component is disposed or the listener is removed.
         */
        void disposeHoverTimer() {
            if (hoverTimer != null) {
                hoverTimer.stop();
                hoverTimer = null;
            }
            lastEvent = null;
            lastDisplayedText = null;
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

                    String displayText = dataExtractor.extractDataText(dataset, seriesIndex, itemIndex);
                    if (displayText != null && !displayText.isEmpty()) {
                        infoLabel.setText(displayText);
                        lastDisplayedText = displayText;
                    }
                }
            } catch (Exception ex) {
                LOG.debug("Error displaying mouseover data", ex);
            }
        }
    }
}
