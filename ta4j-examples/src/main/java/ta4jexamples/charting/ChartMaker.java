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
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYPointerAnnotation;
import org.jfree.chart.axis.DateAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.ValueMarker;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.StandardXYItemRenderer;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.ui.Layer;
import org.jfree.data.xy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.*;
import org.ta4j.core.num.Num;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * A comprehensive chart maker for creating financial charts with trading data
 * and technical analysis.
 *
 * <p>
 * This class provides functionality to create various types of financial charts
 * including:
 * <ul>
 * <li>Candlestick charts with OHLC data</li>
 * <li>Charts with technical indicators</li>
 * <li>Charts with trading signals (buy/sell markers)</li>
 * <li>Charts with analysis overlays (moving averages, etc.)</li>
 * </ul>
 *
 * <p>
 * The ChartMaker supports both display and file export functionality, with
 * configurable chart dimensions and styling options.
 * </p>
 *
 * @see BarSeries
 * @see TradingRecord
 * @see Indicator
 * @see AnalysisType
 */
public class ChartMaker {

    private static final Logger LOG = LoggerFactory.getLogger(ChartMaker.class);

    // Chart configuration constants
    private static final int DEFAULT_CHART_IMAGE_WIDTH = 1920;
    private static final int DEFAULT_CHART_IMAGE_HEIGHT = 1080;
    private static final int DEFAULT_DISPLAY_WIDTH = 1920;
    private static final int DEFAULT_DISPLAY_HEIGHT = 1200;
    private static final int MIN_DISPLAY_WIDTH = 800;
    private static final int MIN_DISPLAY_HEIGHT = 600;
    private static final double DEFAULT_DISPLAY_SCALE = 0.75;
    private static final String DISPLAY_SCALE_PROPERTY = "ta4j.chart.displayScale";

    // Chart styling constants
    private static final Color CHART_BACKGROUND_COLOR = Color.BLACK;
    private static final float CHART_BACKGROUND_ALPHA = 0.85f;
    private static final Color GRIDLINE_COLOR = new Color(0x232323);
    private static final Color BUY_ANNOTATION_COLOR = Color.LIGHT_GRAY;
    private static final Color SELL_ANNOTATION_COLOR = Color.LIGHT_GRAY;

    // Date formatting constants
    private static final String DATE_FORMAT_DAILY = "yyyy-MM-dd";
    private static final String DATE_FORMAT_INTRADAY = "yyyy-MM-dd HH:mm:ss";

    /** Directory for saving chart images. Null if not configured. */
    private final Path chartImageSaveDirectory;

    /**
     * Constructs a ChartMaker without image saving capability.
     */
    public ChartMaker() {
        this.chartImageSaveDirectory = null;
    }

    /**
     * Constructs a ChartMaker with the specified image save directory.
     *
     * @param chartImageSaveDirectory the directory path for saving chart images
     * @throws IllegalArgumentException if chartImageSaveDirectory is null or empty
     */
    public ChartMaker(String chartImageSaveDirectory) {
        if (chartImageSaveDirectory == null || chartImageSaveDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("Chart image save directory cannot be null or empty");
        }
        this.chartImageSaveDirectory = Paths.get(chartImageSaveDirectory);
    }

    /**
     * Generates and saves a chart image for the given trading data.
     *
     * @param series        the bar series data
     * @param strategyName  the name of the strategy
     * @param tradingRecord the trading record containing buy/sell signals
     * @return the file path where the chart was saved, or null if saving is not
     *         configured
     * @throws IllegalArgumentException if any parameter is null
     */
    public String generateAndSaveChartImage(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        validateParameters(series, strategyName, tradingRecord);

        if (this.chartImageSaveDirectory == null) {
            LOG.debug("Chart image saving not configured");
            return null;
        }

        try {
            JFreeChart chart = generateChart(series, strategyName, tradingRecord);
            Path savePath = buildSavePath(series, chart.getTitle().getText());
            return saveChartAsImageFile(chart, savePath, false);
        } catch (Exception ex) {
            LOG.error("Failed to generate and save chart image for {}@{}", strategyName, series.getName(), ex);
            return null;
        }
    }

    /**
     * Generates and displays a chart with trading record data.
     *
     * @param series        the bar series data
     * @param strategyName  the name of the strategy
     * @param tradingRecord the trading record containing buy/sell signals
     * @throws IllegalArgumentException if any parameter is null
     */
    public void generateAndDisplayTradingRecordChart(BarSeries series, String strategyName,
            TradingRecord tradingRecord) {
        validateParameters(series, strategyName, tradingRecord);

        try {
            JFreeChart chart = generateChart(series, strategyName, tradingRecord);
            displayChart(chart);
        } catch (Exception ex) {
            LOG.error("Failed to generate and display trading record chart for {}@{}", strategyName, series.getName(),
                    ex);
        }
    }

    /**
     * Generates and displays a chart with analysis overlays.
     *
     * @param series        the bar series data
     * @param analysisTypes the analysis types to overlay on the chart
     * @throws IllegalArgumentException if series is null or analysisTypes is null
     */
    public void generateAndDisplayChart(BarSeries series, AnalysisType... analysisTypes) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        if (analysisTypes == null) {
            throw new IllegalArgumentException("Analysis types cannot be null");
        }

        try {
            JFreeChart chart = generateChart(series, analysisTypes);
            displayChart(chart);
        } catch (Exception ex) {
            LOG.error("Failed to generate and display analysis chart for {}", series.getName(), ex);
        }
    }

    /**
     * Generates and displays a chart with technical indicators.
     *
     * @param series     the bar series data
     * @param indicators the technical indicators to display
     * @throws IllegalArgumentException if series is null or indicators is null
     */
    @SafeVarargs
    public final void generateAndDisplayChart(BarSeries series, Indicator<Num>... indicators) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        if (indicators == null) {
            throw new IllegalArgumentException("Indicators cannot be null");
        }

        try {
            JFreeChart chart = generateChart(series, indicators);
            displayChart(chart);
        } catch (Exception ex) {
            LOG.error("Failed to generate and display indicators chart for {}", series.getName(), ex);
        }
    }

    /**
     * Generates a chart with trading record data.
     *
     * @param series        the bar series data
     * @param strategyName  the name of the strategy
     * @param tradingRecord the trading record containing buy/sell signals
     * @return the generated JFreeChart
     * @throws IllegalArgumentException if any parameter is null
     */
    public JFreeChart generateChart(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        validateParameters(series, strategyName, tradingRecord);

        try {
            DefaultOHLCDataset data = createChartDataset(series);
            String chartTitle = buildChartTitle(series.getName(), strategyName);
            JFreeChart chart = buildChart(chartTitle, series.getFirstBar().getTimePeriod(), data);
            addTradingRecordToChart((XYPlot) chart.getPlot(), series, tradingRecord);
            return chart;
        } catch (Exception ex) {
            LOG.error("Failed to generate chart for {}@{}", strategyName, series.getName(), ex);
            return new JFreeChart(new XYPlot()); // Return empty chart on error
        }
    }

    /**
     * Generates a chart as a byte array for the given trading data.
     *
     * @param series        the bar series data
     * @param strategyName  the name of the strategy
     * @param tradingRecord the trading record containing buy/sell signals
     * @return the chart as a byte array (PNG format)
     * @throws IllegalArgumentException if any parameter is null
     */
    public byte[] generateChartAsBytes(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        validateParameters(series, strategyName, tradingRecord);

        JFreeChart chart = generateChart(series, strategyName, tradingRecord);
        return getChartAsByteArray(chart);
    }

    /**
     * Generates a chart with technical indicators.
     *
     * @param series     the bar series data
     * @param indicators the technical indicators to display
     * @return the generated JFreeChart
     * @throws IllegalArgumentException if series is null or indicators is null
     */
    @SafeVarargs
    public final JFreeChart generateChart(BarSeries series, Indicator<Num>... indicators) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        if (indicators == null) {
            throw new IllegalArgumentException("Indicators cannot be null");
        }

        try {
            DefaultOHLCDataset data = createChartDataset(series);
            JFreeChart chart = buildChart(series.toString(), series.getFirstBar().getTimePeriod(), data);
            addIndicatorsToChart((XYPlot) chart.getPlot(), indicators);
            return chart;
        } catch (Exception ex) {
            LOG.error("Failed to generate indicators chart for {}", series.getName(), ex);
            return new JFreeChart(new XYPlot()); // Return empty chart on error
        }
    }

    /**
     * Generates a chart with analysis overlays.
     *
     * @param series        the bar series data
     * @param analysisTypes the analysis types to overlay on the chart
     * @return the generated JFreeChart
     * @throws IllegalArgumentException if series is null or analysisTypes is null
     */
    public JFreeChart generateChart(BarSeries series, AnalysisType... analysisTypes) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        if (analysisTypes == null) {
            throw new IllegalArgumentException("Analysis types cannot be null");
        }

        try {
            DefaultOHLCDataset data = createChartDataset(series);
            String chartTitle = buildChartTitle(series.getName(), "");
            JFreeChart chart = buildChart(chartTitle, series.getFirstBar().getTimePeriod(), data);

            if (analysisTypes.length > 0) {
                addAnalysisLinesToChart((XYPlot) chart.getPlot(), data, analysisTypes);
            }
            return chart;
        } catch (Exception ex) {
            LOG.error("Failed to generate analysis chart for {}", series.getName(), ex);
            return new JFreeChart(new XYPlot()); // Return empty chart on error
        }
    }

    /**
     * Converts a JFreeChart to a byte array in PNG format.
     *
     * @param chart the chart to convert
     * @return the chart as a byte array
     * @throws IllegalArgumentException if chart is null
     */
    public byte[] getChartAsByteArray(JFreeChart chart) {
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ChartUtils.writeChartAsPNG(out, chart, DEFAULT_CHART_IMAGE_WIDTH, DEFAULT_CHART_IMAGE_HEIGHT);
        } catch (IOException ex) {
            LOG.error("Failed to write chart to byte array", ex);
        }
        return out.toByteArray();
    }

    // Private helper methods

    /**
     * Validates the required parameters for trading record methods.
     */
    private void validateParameters(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        if (strategyName == null || strategyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Strategy name cannot be null or empty");
        }
        if (tradingRecord == null) {
            throw new IllegalArgumentException("Trading record cannot be null");
        }
    }

    /**
     * Builds a chart title from the series name and strategy name.
     */
    private String buildChartTitle(String barSeriesName, String strategyName) {
        if (barSeriesName == null || barSeriesName.trim().isEmpty()) {
            return strategyName;
        }
        String[] shortenedBarSeriesName = barSeriesName.split(" ");
        return strategyName + "@" + shortenedBarSeriesName[0];
    }

    /**
     * Builds the save path for chart images.
     *
     * <p>
     * This method sanitizes all path components to ensure they are valid for the
     * file system. Invalid characters like colons (:) in timestamps are replaced
     * with safe alternatives.
     * </p>
     */
    private Path buildSavePath(BarSeries series, String chartTitle) {
        String sanitizedSeriesName = sanitizePathComponent(series.getName());
        String sanitizedPeriodDescription = sanitizePathComponent(series.getSeriesPeriodDescription());
        String sanitizedChartTitle = sanitizePathComponent(chartTitle);

        return Paths.get(this.chartImageSaveDirectory.toString(), sanitizedSeriesName, sanitizedPeriodDescription,
                sanitizedChartTitle + ".jpg");
    }

    /**
     * Sanitizes a string to be safe for use as a file or directory name.
     *
     * <p>
     * This method replaces invalid filename characters with safe alternatives:
     * <ul>
     * <li>Colons (:) are replaced with hyphens (-)</li>
     * <li>Forward slashes (/) are replaced with underscores (_)</li>
     * <li>Backslashes (\) are replaced with underscores (_)</li>
     * <li>Question marks (?) are replaced with underscores (_)</li>
     * <li>Asterisks (*) are replaced with underscores (_)</li>
     * <li>Less than (<) and greater than (>) are replaced with parentheses</li>
     * <li>Pipe characters (|) are replaced with underscores (_)</li>
     * <li>Double quotes (") are removed</li>
     * <li>Leading/trailing whitespace and dots are trimmed</li>
     * <li>Empty strings are replaced with "unknown"</li>
     * </ul>
     *
     * @param component the path component to sanitize
     * @return a sanitized string safe for use in file paths
     */
    private String sanitizePathComponent(String component) {
        if (component == null || component.trim().isEmpty()) {
            return "unknown";
        }

        return component.replace(":", "-") // Replace colons with hyphens
                .replace("/", "_") // Replace forward slashes with underscores
                .replace("\\", "_") // Replace backslashes with underscores
                .replace("?", "_") // Replace question marks with underscores
                .replace("*", "_") // Replace asterisks with underscores
                .replace("<", "(") // Replace less than with opening parenthesis
                .replace(">", ")") // Replace greater than with closing parenthesis
                .replace("|", "_") // Replace pipe characters with underscores
                .replace("\"", "") // Remove double quotes
                .trim() // Remove leading/trailing whitespace
                .replaceAll("^\\.+|\\.+$", "") // Remove leading/trailing dots
                .replaceAll("\\s+", "_"); // Replace multiple whitespace with single underscore
    }

    /**
     * Saves a chart as an image file.
     */
    private String saveChartAsImageFile(JFreeChart chart, Path savePath, boolean clearSavePath) {
        try {
            if (clearSavePath) {
                Path deletePath = savePath.subpath(0, savePath.getNameCount() - 1);
                LOG.debug("Removing file(s)/dir(s) at {}", deletePath.toAbsolutePath().toString());
                Files.deleteIfExists(deletePath);
            }

            Files.createDirectories(savePath.subpath(0, savePath.getNameCount() - 1));
            ChartUtils.saveChartAsJPEG(savePath.toFile(), chart, DEFAULT_CHART_IMAGE_WIDTH, DEFAULT_CHART_IMAGE_HEIGHT);
            LOG.debug("Saved chart to {}", savePath.toAbsolutePath().toString());
            return savePath.toAbsolutePath().toString();
        } catch (IOException ex) {
            LOG.error("Failed to save chart {} to {}", chart.getTitle().getText(), savePath, ex);
            return null;
        }
    }

    /**
     * Adds trading record data to the chart plot.
     */
    private void addTradingRecordToChart(XYPlot plot, BarSeries series, TradingRecord tradingRecord) {
        try {
            int positionIndex = 1;

            // Add completed positions
            for (Position position : tradingRecord.getPositions()) {
                addTradeToChart(position.getEntry(), positionIndex, plot, series);
                addTradeToChart(position.getExit(), positionIndex, plot, series);
                positionIndex++;
            }

            // Add current position if open
            if (tradingRecord.getCurrentPosition().isOpened()) {
                Trade lastTrade = tradingRecord.getLastTrade();
                if (lastTrade != null) {
                    addTradeToChart(lastTrade, positionIndex, plot, series);
                }
            }
        } catch (Exception ex) {
            LOG.error("Failed to add trading record to chart", ex);
        }
    }

    /**
     * Adds a single trade to the chart plot.
     */
    private void addTradeToChart(Trade trade, int tradeIndex, XYPlot plot, BarSeries series) {
        if (trade == null) {
            return;
        }

        Instant seriesStartTime = series.getFirstBar().getEndTime();
        Instant seriesEndTime = series.getLastBar().getEndTime();
        Instant tradeTime = series.getBar(trade.getIndex()).getEndTime();

        if (isTradeTimeInRange(tradeTime, seriesStartTime, seriesEndTime)) {
            double orderDateTime = tradeTime.toEpochMilli();
            String label = (trade.isBuy() ? "buy" : "sell") + "#" + tradeIndex;
            double angle = trade.isBuy() ? 90 : 180;
            Color color = trade.isBuy() ? BUY_ANNOTATION_COLOR : SELL_ANNOTATION_COLOR;

            XYPointerAnnotation annotation = new XYPointerAnnotation(label, orderDateTime,
                    trade.getPricePerAsset().doubleValue(), angle);
            annotation.setPaint(color);
            annotation.setArrowPaint(color);
            plot.addAnnotation(annotation);
        } else {
            LOG.debug("Trade at {} not added to chart - outside range [{}, {}]", tradeTime, seriesStartTime,
                    seriesEndTime);
        }
    }

    /**
     * Checks if a trade time is within the series time range.
     */
    private boolean isTradeTimeInRange(Instant tradeTime, Instant startTime, Instant endTime) {
        return (tradeTime.equals(startTime) || tradeTime.isAfter(startTime))
                && (tradeTime.equals(endTime) || tradeTime.isBefore(endTime));
    }

    /**
     * Creates a data series for an indicator.
     */
    private XYSeries createDataSeriesForIndicator(Indicator<Num> indicator) {
        XYSeries series = new XYSeries(indicator.toString());

        for (int i = indicator.getBarSeries().getBeginIndex(); i < indicator.getBarSeries().getEndIndex(); i++) {
            Num value = indicator.getValue(i);
            if (value != null && !value.isNaN()) {
                double orderDateTime = indicator.getBarSeries().getBar(i).getEndTime().toEpochMilli();
                series.add(orderDateTime, value.doubleValue());
            }
        }

        return series;
    }

    /**
     * Creates datasets for multiple indicators.
     */
    @SafeVarargs
    private final List<XYSeriesCollection> createDataSetsForIndicators(Indicator<Num>... indicators) {
        List<XYSeriesCollection> dataSets = new ArrayList<>();

        for (Indicator<Num> indicator : indicators) {
            XYSeriesCollection dataset = new XYSeriesCollection();
            XYSeries series = createDataSeriesForIndicator(indicator);
            dataset.addSeries(series);
            dataSets.add(dataset);
        }

        return dataSets;
    }

    /**
     * Adds indicators to the chart plot.
     */
    @SafeVarargs
    private final void addIndicatorsToChart(XYPlot plot, Indicator<Num>... indicators) {
        List<XYSeriesCollection> dataSets = createDataSetsForIndicators(indicators);

        int index = 1;
        for (XYSeriesCollection dataSet : dataSets) {
            plot.setDataset(index, dataSet);
            plot.setRenderer(index, new StandardXYItemRenderer());
            index++;
        }
    }

    /**
     * Adds analysis lines to the chart plot.
     */
    private void addAnalysisLinesToChart(XYPlot plot, DefaultOHLCDataset data, AnalysisType... analysisTypes) {
        LOG.debug("Range Upper Bound: {}", plot.getDataRange(plot.getRangeAxis()).getUpperBound());
        LOG.debug("Domain Upper Bound: {}",
                LocalDateTime.ofInstant(
                        Instant.ofEpochMilli((long) plot.getDataRange(plot.getDomainAxis()).getUpperBound()),
                        ZoneId.systemDefault()));

        try {
            // Add a sample range marker (this could be made configurable)
            plot.addRangeMarker(new ValueMarker(300d, Color.RED, new BasicStroke(0.1f)), Layer.FOREGROUND);

            int index = 1;
            for (AnalysisType analysisType : analysisTypes) {
                XYDataset analysisDataSet = analysisType.dataSet.getAnalysis(data);
                plot.setDataset(index, analysisDataSet);
                plot.setRenderer(index, new StandardXYItemRenderer());
                index++;
            }
        } catch (Exception ex) {
            LOG.error("Failed to add analysis lines to chart", ex);
        }
    }

    /**
     * Builds the main chart with OHLC data.
     */
    private JFreeChart buildChart(String chartTitle, Duration duration, DefaultOHLCDataset dataSet) {
        JFreeChart chart = ChartFactory.createCandlestickChart(chartTitle, // title
                "Date", // x-axis label
                "Price (USD)", // y-axis label
                dataSet, // data
                true // create legend?
        );

        // Configure chart appearance
        chart.setAntiAlias(true);
        chart.setTextAntiAlias(true);
        chart.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        chart.setBackgroundImageAlpha(CHART_BACKGROUND_ALPHA);

        XYPlot plot = (XYPlot) chart.getPlot();
        configureDomainAxis(plot, duration);
        configureRangeAxis(plot);
        configureCandlestickRenderer(plot);
        configurePlotAppearance(plot);

        return chart;
    }

    /**
     * Configures the domain (time) axis.
     */
    private void configureDomainAxis(XYPlot plot, Duration duration) {
        DateAxis domainAxis = (DateAxis) plot.getDomainAxis();

        if (duration.toDays() >= 1) {
            domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_DAILY));
        } else {
            domainAxis.setDateFormatOverride(new SimpleDateFormat(DATE_FORMAT_INTRADAY));
        }
        domainAxis.setAutoRange(true);
    }

    /**
     * Configures the range (price) axis.
     */
    private void configureRangeAxis(XYPlot plot) {
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setAutoRangeIncludesZero(false);
    }

    /**
     * Configures the candlestick renderer.
     */
    private void configureCandlestickRenderer(XYPlot plot) {
        BaseCandleStickRenderer candleStickRenderer = new BaseCandleStickRenderer();
        candleStickRenderer.setDownPaint(new Color(0x9C0004));
        candleStickRenderer.setUpPaint(new Color(0x00B909));
        plot.setRenderer(candleStickRenderer);
    }

    /**
     * Configures the plot appearance.
     */
    private void configurePlotAppearance(XYPlot plot) {
        plot.setBackgroundPaint(CHART_BACKGROUND_COLOR);
        plot.setBackgroundAlpha(CHART_BACKGROUND_ALPHA);
        plot.setDomainGridlinePaint(GRIDLINE_COLOR);
        plot.setRangeGridlinePaint(GRIDLINE_COLOR);
    }

    /**
     * Creates a chart dataset from a bar series.
     */
    private DefaultOHLCDataset createChartDataset(BarSeries series) {
        List<OHLCDataItem> dataItems = new ArrayList<>();

        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Bar tick = series.getBar(i);
            OHLCDataItem item = new OHLCDataItem(Date.from(tick.getEndTime()), tick.getOpenPrice().doubleValue(),
                    tick.getHighPrice().doubleValue(), tick.getLowPrice().doubleValue(),
                    tick.getClosePrice().doubleValue(), tick.getVolume().doubleValue());
            dataItems.add(item);
        }

        String seriesName = series.getName() != null ? series.getName().split(" ")[0] : "Unknown";
        return new DefaultOHLCDataset(seriesName, dataItems.toArray(new OHLCDataItem[0]));
    }

    /**
     * Displays a chart in a frame.
     */
    private void displayChart(JFreeChart chart) {
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
                LOG.warn("Ignoring display scale property {} outside accepted range (0.1, 1.0]: {}", DISPLAY_SCALE_PROPERTY,
                        configuredScale);
            } catch (NumberFormatException numberFormatException) {
                LOG.warn("Unable to parse display scale property {} value: {}", DISPLAY_SCALE_PROPERTY, configuredScale,
                        numberFormatException);
            }
        }
        return DEFAULT_DISPLAY_SCALE;
    }
}
