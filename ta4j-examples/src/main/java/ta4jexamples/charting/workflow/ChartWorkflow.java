/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.workflow;

import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

import java.awt.Dimension;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import ta4jexamples.charting.builder.ChartBuilder;
import ta4jexamples.charting.builder.ChartContext;
import ta4jexamples.charting.builder.TimeAxisMode;
import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.compose.TradingChartFactory;
import ta4jexamples.charting.display.ChartDisplayer;
import ta4jexamples.charting.display.SwingChartDisplayer;
import ta4jexamples.charting.storage.ChartStorage;
import ta4jexamples.charting.storage.FileSystemChartStorage;

/**
 * Facade for composing, displaying, and persisting TA4J charts.
 *
 * <p>
 * The refactored {@link ChartWorkflow} decomposes responsibilities into
 * dedicated collaborators:
 * </p>
 * <ul>
 * <li>{@link TradingChartFactory} builds {@link JFreeChart} instances.</li>
 * <li>{@link ChartDisplayer} presents charts to a user interface.</li>
 * <li>{@link ChartStorage} persists charts to the filesystem (optional).</li>
 * </ul>
 *
 * <p>
 * High-level convenience methods remain available while delegating to these
 * collaborators, enabling more focused unit testing and future extension.
 * </p>
 *
 * @since 0.19
 */
public class ChartWorkflow {

    /**
     * Default saved chart image width.
     *
     * <p>
     * Saved/exported charts default to 4K so persisted artifacts retain detail even
     * when the on-screen display uses a smaller, screen-aware size. The constant
     * itself has existed since 0.19; the default export size was raised from
     * 1920x1080 to 3840x2160 in 0.22.7.
     * </p>
     *
     * @since 0.19
     */
    static final int DEFAULT_CHART_IMAGE_WIDTH = 3840;

    /**
     * Default saved chart image height.
     *
     * <p>
     * Paired with {@link #DEFAULT_CHART_IMAGE_WIDTH}, the default export size was
     * raised to 3840x2160 in 0.22.7 while keeping the same constant in place.
     * </p>
     *
     * @since 0.19
     */
    static final int DEFAULT_CHART_IMAGE_HEIGHT = 2160;

    private static final Logger LOG = LogManager.getLogger(ChartWorkflow.class);

    private final TradingChartFactory chartFactory;
    private final ChartDisplayer chartDisplayer;
    private final ChartStorage chartStorage;

    /**
     * Creates a {@link ChartWorkflow} that supports chart composition and display
     * but omits chart persistence.
     *
     * @since 0.19
     */
    public ChartWorkflow() {
        this(new TradingChartFactory(), new SwingChartDisplayer(), ChartStorage.noOp());
    }

    /**
     * Creates a {@link ChartWorkflow} with filesystem persistence enabled.
     *
     * @param chartImageSaveDirectory the directory to store generated chart images
     * @since 0.19
     */
    public ChartWorkflow(String chartImageSaveDirectory) {
        this(new TradingChartFactory(), new SwingChartDisplayer(),
                new FileSystemChartStorage(resolveSaveDirectory(chartImageSaveDirectory)));
    }

    /**
     * Creates a {@link ChartWorkflow} with explicit collaborators.
     *
     * <p>
     * This constructor is the dependency-injection entry point for tests and
     * callers that want to swap rendering, display, or persistence behavior without
     * subclassing the workflow facade.
     * </p>
     *
     * @param chartFactory   factory responsible for composing charts
     * @param chartDisplayer display strategy used for on-screen rendering
     * @param chartStorage   persistence strategy used for saved chart artifacts
     * @since 0.22.7
     */
    public ChartWorkflow(TradingChartFactory chartFactory, ChartDisplayer chartDisplayer, ChartStorage chartStorage) {
        this.chartFactory = Objects.requireNonNull(chartFactory, "Chart factory cannot be null");
        this.chartDisplayer = Objects.requireNonNull(chartDisplayer, "Chart displayer cannot be null");
        this.chartStorage = Objects.requireNonNull(chartStorage, "Chart storage cannot be null");
    }

    /**
     * Creates a new chart builder for fluent chart construction.
     *
     * @return a new chart builder
     * @since 0.19
     */
    public ChartBuilder builder() {
        return new ChartBuilder(this, chartFactory);
    }

    /**
     * Renders a chart from the provided plan.
     *
     * @param plan the chart plan to render
     * @return the rendered chart
     */
    public JFreeChart render(ChartPlan plan) {
        Objects.requireNonNull(plan, "Chart plan cannot be null");
        return chartFactory.compose(plan.context());
    }

    /**
     * Renders a chart from the provided chart context.
     *
     * @param context the chart context to render
     * @return the rendered chart
     * @since 0.22.2
     */
    public JFreeChart render(ChartContext context) {
        Objects.requireNonNull(context, "Chart context cannot be null");
        return chartFactory.compose(context);
    }

    /**
     * Displays the chart described by the provided plan.
     *
     * @param plan the chart plan
     */
    public void display(ChartPlan plan) {
        String windowTitle = plan.metadata().title();
        if (windowTitle != null && !windowTitle.trim().isEmpty()) {
            displayChart(render(plan), windowTitle);
        } else {
            displayChart(render(plan));
        }
    }

    /**
     * Displays the chart described by the provided plan with a custom window title.
     *
     * @param plan        the chart plan
     * @param windowTitle custom window title
     */
    public void display(ChartPlan plan, String windowTitle) {
        displayChart(render(plan), windowTitle);
    }

    /**
     * Displays the chart described by the provided plan with a custom window title
     * and preferred display size.
     *
     * @param plan          the chart plan
     * @param windowTitle   custom window title
     * @param preferredSize preferred display size for the on-screen window
     * @since 0.22.7
     */
    public void display(ChartPlan plan, String windowTitle, Dimension preferredSize) {
        String effectiveWindowTitle = windowTitle;
        if (effectiveWindowTitle == null || effectiveWindowTitle.trim().isEmpty()) {
            effectiveWindowTitle = plan.metadata().title();
        }
        displayChart(render(plan), effectiveWindowTitle, preferredSize);
    }

    /**
     * Saves the chart described by the provided plan using the default storage
     * strategy.
     *
     * @param plan the chart plan
     * @return the optional path to the saved chart
     */
    public Optional<Path> save(ChartPlan plan) {
        return saveChartImage(render(plan), plan.primarySeries());
    }

    /**
     * Saves the chart described by the provided plan using an explicit export
     * resolution.
     *
     * @param plan        the chart plan
     * @param imageWidth  exported image width
     * @param imageHeight exported image height
     * @return the optional path to the saved chart
     * @since 0.22.7
     */
    public Optional<Path> save(ChartPlan plan, int imageWidth, int imageHeight) {
        return saveChartImage(render(plan), plan.primarySeries(), imageWidth, imageHeight);
    }

    /**
     * Saves the chart described by the provided plan with a custom filename.
     *
     * @param plan     the chart plan
     * @param filename desired filename
     * @return the optional path to the saved chart
     */
    public Optional<Path> save(ChartPlan plan, String filename) {
        return saveChartImage(render(plan), plan.primarySeries(), filename);
    }

    /**
     * Saves the chart described by the provided plan with a custom filename and an
     * explicit export resolution.
     *
     * @param plan        the chart plan
     * @param filename    desired filename
     * @param imageWidth  exported image width
     * @param imageHeight exported image height
     * @return the optional path to the saved chart
     * @since 0.22.7
     */
    public Optional<Path> save(ChartPlan plan, String filename, int imageWidth, int imageHeight) {
        return saveChartImage(render(plan), plan.primarySeries(), filename, imageWidth, imageHeight);
    }

    /**
     * Saves the chart described by the provided plan to the supplied directory.
     *
     * @param plan      the chart plan
     * @param directory target directory
     * @return optional saved path
     */
    public Optional<Path> save(ChartPlan plan, Path directory) {
        return saveChartImage(render(plan), plan.primarySeries(), directory);
    }

    /**
     * Saves the chart described by the provided plan to the supplied directory
     * using an explicit export resolution.
     *
     * @param plan        the chart plan
     * @param directory   target directory
     * @param imageWidth  exported image width
     * @param imageHeight exported image height
     * @return optional saved path
     * @since 0.22.7
     */
    public Optional<Path> save(ChartPlan plan, Path directory, int imageWidth, int imageHeight) {
        return saveChartImage(render(plan), plan.primarySeries(), directory, imageWidth, imageHeight);
    }

    /**
     * Saves the chart described by the provided plan using string directory and
     * filename inputs.
     *
     * @param plan      the chart plan
     * @param directory directory expressed as a string
     * @param filename  optional filename
     * @return optional saved path
     */
    public Optional<Path> save(ChartPlan plan, String directory, String filename) {
        return saveChartImage(render(plan), plan.primarySeries(), filename, directory);
    }

    /**
     * Saves the chart described by the provided plan using string directory and
     * filename inputs plus an explicit export resolution.
     *
     * @param plan        the chart plan
     * @param directory   directory expressed as a string
     * @param filename    optional filename
     * @param imageWidth  exported image width
     * @param imageHeight exported image height
     * @return optional saved path
     * @since 0.22.7
     */
    public Optional<Path> save(ChartPlan plan, String directory, String filename, int imageWidth, int imageHeight) {
        return saveChartImage(render(plan), plan.primarySeries(), filename, directory, imageWidth, imageHeight);
    }

    /**
     * Saves the chart described by the provided plan to the supplied directory with
     * a custom filename.
     *
     * @param plan      the chart plan
     * @param directory target directory
     * @param filename  desired filename
     * @return optional saved path
     */
    public Optional<Path> save(ChartPlan plan, Path directory, String filename) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        return save(plan, directory.toString(), filename);
    }

    /**
     * Saves the chart described by the provided plan to the supplied directory with
     * a custom filename and export resolution.
     *
     * @param plan        the chart plan
     * @param directory   target directory
     * @param filename    desired filename
     * @param imageWidth  exported image width
     * @param imageHeight exported image height
     * @return optional saved path
     * @since 0.22.7
     */
    public Optional<Path> save(ChartPlan plan, Path directory, String filename, int imageWidth, int imageHeight) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        return save(plan, directory.toString(), filename, imageWidth, imageHeight);
    }

    /**
     * Builds a chart that overlays a trading record on top of OHLC data.
     *
     * @since 0.19
     */
    public JFreeChart createTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        return createTradingRecordChart(series, strategyName, tradingRecord, TimeAxisMode.REAL_TIME);
    }

    /**
     * Builds a chart that overlays a trading record on top of OHLC data using the
     * supplied time axis mode.
     *
     * @since 0.22.2
     */
    public JFreeChart createTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord,
            TimeAxisMode timeAxisMode) {
        return buildTradingRecordChart(series, strategyName, tradingRecord, timeAxisMode, null, false);
    }

    /**
     * Builds a chart that overlays a trading record on top of OHLC data and appends
     * indicator subplots.
     *
     * @since 0.19
     */
    @SafeVarargs
    public final JFreeChart createTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord,
            Indicator<Num>... indicators) {
        return createTradingRecordChart(series, strategyName, tradingRecord, TimeAxisMode.REAL_TIME, indicators);
    }

    /**
     * Builds a chart that overlays a trading record on top of OHLC data and appends
     * indicator subplots using the supplied time axis mode.
     *
     * @since 0.22.2
     */
    @SafeVarargs
    public final JFreeChart createTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord,
            TimeAxisMode timeAxisMode, Indicator<Num>... indicators) {
        return buildTradingRecordChart(series, strategyName, tradingRecord, timeAxisMode, indicators, true);
    }

    /**
     * Persists a trading record chart if persistence is configured.
     *
     * @return an optional path to the stored chart
     * @since 0.19
     */
    public Optional<Path> saveTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        return saveTradingRecordChart(series, strategyName, tradingRecord, TimeAxisMode.REAL_TIME);
    }

    /**
     * Persists a trading record chart if persistence is configured, using the
     * supplied time axis mode.
     *
     * @return an optional path to the stored chart
     * @since 0.22.2
     */
    public Optional<Path> saveTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord,
            TimeAxisMode timeAxisMode) {
        JFreeChart chart = buildTradingRecordChart(series, strategyName, tradingRecord, timeAxisMode, null, false);
        String chartTitle = resolveChartTitle(chart, series, strategyName);
        return chartStorage.save(chart, series, chartTitle, DEFAULT_CHART_IMAGE_WIDTH, DEFAULT_CHART_IMAGE_HEIGHT);
    }

    /**
     * Persists a trading record chart with indicator subplots if persistence is
     * configured.
     *
     * @return an optional path to the stored chart
     * @since 0.19
     */
    @SafeVarargs
    public final Optional<Path> saveTradingRecordChart(BarSeries series, String strategyName,
            TradingRecord tradingRecord, Indicator<Num>... indicators) {
        return saveTradingRecordChart(series, strategyName, tradingRecord, TimeAxisMode.REAL_TIME, indicators);
    }

    /**
     * Persists a trading record chart with indicator subplots if persistence is
     * configured, using the supplied time axis mode.
     *
     * @return an optional path to the stored chart
     * @since 0.22.2
     */
    @SafeVarargs
    public final Optional<Path> saveTradingRecordChart(BarSeries series, String strategyName,
            TradingRecord tradingRecord, TimeAxisMode timeAxisMode, Indicator<Num>... indicators) {
        JFreeChart chart = buildTradingRecordChart(series, strategyName, tradingRecord, timeAxisMode, indicators, true);
        String chartTitle = resolveChartTitle(chart, series, strategyName);
        return chartStorage.save(chart, series, chartTitle, DEFAULT_CHART_IMAGE_WIDTH, DEFAULT_CHART_IMAGE_HEIGHT);
    }

    /**
     * Displays a trading record chart, logging any presentation exceptions.
     *
     * @since 0.19
     */
    public void displayTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        displayTradingRecordChart(series, strategyName, tradingRecord, TimeAxisMode.REAL_TIME);
    }

    /**
     * Displays a trading record chart, logging any presentation exceptions, using
     * the supplied time axis mode.
     *
     * @since 0.22.2
     */
    public void displayTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord,
            TimeAxisMode timeAxisMode) {
        validateTradingInputs(series, strategyName, tradingRecord);
        validateTimeAxisMode(timeAxisMode);
        displayChartSafely(
                () -> chartFactory.createTradingRecordChart(series, strategyName, tradingRecord, timeAxisMode), null,
                "Failed to display trading record chart for {}@{}", strategyName, safeSeriesName(series));
    }

    /**
     * Displays a trading record chart with indicator subplots, logging any
     * presentation exceptions.
     *
     * @since 0.19
     */
    @SafeVarargs
    public final void displayTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord,
            Indicator<Num>... indicators) {
        displayTradingRecordChart(series, strategyName, tradingRecord, TimeAxisMode.REAL_TIME, indicators);
    }

    /**
     * Displays a trading record chart with indicator subplots, logging any
     * presentation exceptions, using the supplied time axis mode.
     *
     * @since 0.22.2
     */
    @SafeVarargs
    public final void displayTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord,
            TimeAxisMode timeAxisMode, Indicator<Num>... indicators) {
        validateTradingInputs(series, strategyName, tradingRecord);
        validateTimeAxisMode(timeAxisMode);
        validateIndicators(indicators);
        displayChartSafely(
                () -> chartFactory.createTradingRecordChart(series, strategyName, tradingRecord, timeAxisMode,
                        indicators),
                null, "Failed to display trading record chart for {}@{}", strategyName, safeSeriesName(series));
    }

    /**
     * Produces a PNG representation of a trading record chart.
     *
     * @since 0.19
     */
    public byte[] createTradingRecordChartBytes(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        return createTradingRecordChartBytes(series, strategyName, tradingRecord, TimeAxisMode.REAL_TIME);
    }

    /**
     * Produces a PNG representation of a trading record chart using the supplied
     * time axis mode.
     *
     * @since 0.22.2
     */
    public byte[] createTradingRecordChartBytes(BarSeries series, String strategyName, TradingRecord tradingRecord,
            TimeAxisMode timeAxisMode) {
        JFreeChart chart = buildTradingRecordChart(series, strategyName, tradingRecord, timeAxisMode, null, false);
        return getChartAsByteArray(chart);
    }

    /**
     * Produces a PNG representation of a trading record chart with indicator
     * subplots.
     *
     * @since 0.19
     */
    @SafeVarargs
    public final byte[] createTradingRecordChartBytes(BarSeries series, String strategyName,
            TradingRecord tradingRecord, Indicator<Num>... indicators) {
        return createTradingRecordChartBytes(series, strategyName, tradingRecord, TimeAxisMode.REAL_TIME, indicators);
    }

    /**
     * Produces a PNG representation of a trading record chart with indicator
     * subplots using the supplied time axis mode.
     *
     * @since 0.22.2
     */
    @SafeVarargs
    public final byte[] createTradingRecordChartBytes(BarSeries series, String strategyName,
            TradingRecord tradingRecord, TimeAxisMode timeAxisMode, Indicator<Num>... indicators) {
        JFreeChart chart = buildTradingRecordChart(series, strategyName, tradingRecord, timeAxisMode, indicators, true);
        return getChartAsByteArray(chart);
    }

    /**
     * Builds an indicator chart with the bar series in the main section and each
     * indicator in its own section below, each with its own Y-axis.
     *
     * @since 0.19
     */
    @SafeVarargs
    public final JFreeChart createIndicatorChart(BarSeries series, Indicator<Num>... indicators) {
        return createIndicatorChart(series, TimeAxisMode.REAL_TIME, indicators);
    }

    /**
     * Builds an indicator chart with the bar series in the main section and each
     * indicator in its own section below, each with its own Y-axis, using the
     * supplied time axis mode.
     *
     * @since 0.22.2
     */
    @SafeVarargs
    public final JFreeChart createIndicatorChart(BarSeries series, TimeAxisMode timeAxisMode,
            Indicator<Num>... indicators) {
        return buildIndicatorChart(series, timeAxisMode, indicators);
    }

    /**
     * Displays an indicator chart with the bar series in the main section and each
     * indicator in its own section below, each with its own Y-axis.
     *
     * @since 0.19
     */
    @SafeVarargs
    public final void displayIndicatorChart(BarSeries series, Indicator<Num>... indicators) {
        displayIndicatorChart(series, TimeAxisMode.REAL_TIME, indicators);
    }

    /**
     * Displays an indicator chart with the bar series in the main section and each
     * indicator in its own section below, each with its own Y-axis, using the
     * supplied time axis mode.
     *
     * @since 0.22.2
     */
    @SafeVarargs
    public final void displayIndicatorChart(BarSeries series, TimeAxisMode timeAxisMode, Indicator<Num>... indicators) {
        validateTimeAxisMode(timeAxisMode);
        displayChartSafely(() -> createIndicatorChart(series, timeAxisMode, indicators), null,
                "Failed to display indicator chart for {}", safeSeriesName(series));
    }

    /**
     * Builds a dual-axis chart with two indicators.
     *
     * @param series             the bar series
     * @param primaryIndicator   the primary indicator (left axis)
     * @param primaryLabel       the label for the primary axis
     * @param secondaryIndicator the secondary indicator (right axis)
     * @param secondaryLabel     the label for the secondary axis
     * @return the dual-axis chart
     * @since 0.19
     */
    public JFreeChart createDualAxisChart(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel) {
        return createDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator, secondaryLabel, null,
                TimeAxisMode.REAL_TIME);
    }

    /**
     * Builds a dual-axis chart with two indicators and a custom chart title.
     *
     * @param series             the bar series
     * @param primaryIndicator   the primary indicator (left axis)
     * @param primaryLabel       the label for the primary axis
     * @param secondaryIndicator the secondary indicator (right axis)
     * @param secondaryLabel     the label for the secondary axis
     * @param chartTitle         the title for the chart (optional, uses series name
     *                           if null)
     * @return the dual-axis chart
     * @since 0.19
     */
    public JFreeChart createDualAxisChart(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel, String chartTitle) {
        return createDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator, secondaryLabel,
                chartTitle, TimeAxisMode.REAL_TIME);
    }

    /**
     * Builds a dual-axis chart with two indicators, a custom chart title, and a
     * custom time axis mode.
     *
     * @param series             the bar series
     * @param primaryIndicator   the primary indicator (left axis)
     * @param primaryLabel       the label for the primary axis
     * @param secondaryIndicator the secondary indicator (right axis)
     * @param secondaryLabel     the label for the secondary axis
     * @param chartTitle         the title for the chart (optional, uses series name
     *                           if null)
     * @param timeAxisMode       the time axis mode to use
     * @return the dual-axis chart
     * @since 0.22.2
     */
    public JFreeChart createDualAxisChart(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel, String chartTitle, TimeAxisMode timeAxisMode) {
        validateSeries(series);
        if (primaryIndicator == null) {
            throw new IllegalArgumentException("Primary indicator cannot be null");
        }
        if (secondaryIndicator == null) {
            throw new IllegalArgumentException("Secondary indicator cannot be null");
        }
        if (primaryLabel == null || primaryLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("Primary label cannot be null or empty");
        }
        if (secondaryLabel == null || secondaryLabel.trim().isEmpty()) {
            throw new IllegalArgumentException("Secondary label cannot be null or empty");
        }
        validateTimeAxisMode(timeAxisMode);
        return chartFactory.createDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator,
                secondaryLabel, chartTitle, timeAxisMode);
    }

    /**
     * Displays a dual-axis chart with two indicators.
     *
     * @param series             the bar series
     * @param primaryIndicator   the primary indicator (left axis)
     * @param primaryLabel       the label for the primary axis
     * @param secondaryIndicator the secondary indicator (right axis)
     * @param secondaryLabel     the label for the secondary axis
     * @since 0.19
     */
    public void displayDualAxisChart(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel) {
        displayDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator, secondaryLabel,
                safeSeriesName(series), null, TimeAxisMode.REAL_TIME);
    }

    /**
     * Displays a dual-axis chart with two indicators and a custom time axis mode.
     *
     * @param series             the bar series
     * @param primaryIndicator   the primary indicator (left axis)
     * @param primaryLabel       the label for the primary axis
     * @param secondaryIndicator the secondary indicator (right axis)
     * @param secondaryLabel     the label for the secondary axis
     * @param timeAxisMode       the time axis mode to use
     * @since 0.22.2
     */
    public void displayDualAxisChart(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel, TimeAxisMode timeAxisMode) {
        displayDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator, secondaryLabel,
                safeSeriesName(series), null, timeAxisMode);
    }

    /**
     * Displays a dual-axis chart with two indicators, optional chart title, and
     * optional window title.
     *
     * @param series             the bar series
     * @param primaryIndicator   the primary indicator (left axis)
     * @param primaryLabel       the label for the primary axis
     * @param secondaryIndicator the secondary indicator (right axis)
     * @param secondaryLabel     the label for the secondary axis
     * @param chartTitle         the title for the chart (optional, uses series name
     *                           if null)
     * @param windowTitle        the title for the window/frame (optional, uses
     *                           default if null)
     * @since 0.19
     */
    public void displayDualAxisChart(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel, String chartTitle, String windowTitle) {
        displayDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator, secondaryLabel, chartTitle,
                windowTitle, TimeAxisMode.REAL_TIME);
    }

    /**
     * Displays a dual-axis chart with two indicators, optional chart title,
     * optional window title, and a custom time axis mode.
     *
     * @param series             the bar series
     * @param primaryIndicator   the primary indicator (left axis)
     * @param primaryLabel       the label for the primary axis
     * @param secondaryIndicator the secondary indicator (right axis)
     * @param secondaryLabel     the label for the secondary axis
     * @param chartTitle         the title for the chart (optional, uses series name
     *                           if null)
     * @param windowTitle        the title for the window/frame (optional, uses
     *                           default if null)
     * @param timeAxisMode       the time axis mode to use
     * @since 0.22.2
     */
    public void displayDualAxisChart(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel, String chartTitle, String windowTitle,
            TimeAxisMode timeAxisMode) {
        displayChartSafely(
                () -> createDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator, secondaryLabel,
                        chartTitle, timeAxisMode),
                windowTitle, "Failed to display dual-axis chart for {}", safeSeriesName(series));
    }

    /**
     * Displays a caller-provided chart using the configured displayer.
     *
     * @since 0.19
     */
    public void displayChart(JFreeChart chart) {
        displayChart(chart, null, null);
    }

    /**
     * Displays a caller-provided chart using the configured displayer with a custom
     * window title.
     *
     * @param chart       the chart to display
     * @param windowTitle the title for the window/frame (optional, uses default if
     *                    null)
     * @since 0.19
     */
    public void displayChart(JFreeChart chart, String windowTitle) {
        displayChart(chart, windowTitle, null);
    }

    /**
     * Displays a caller-provided chart using the configured displayer with a custom
     * window title and preferred display size.
     *
     * @param chart         the chart to display
     * @param windowTitle   the title for the window/frame (optional, uses default
     *                      if null)
     * @param preferredSize preferred display size for the on-screen window
     * @since 0.22.7
     */
    public void displayChart(JFreeChart chart, String windowTitle, Dimension preferredSize) {
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null");
        }
        if (windowTitle != null && !windowTitle.trim().isEmpty()) {
            chartDisplayer.display(chart, windowTitle, preferredSize);
        } else if (preferredSize != null) {
            chartDisplayer.display(chart, null, preferredSize);
        } else {
            chartDisplayer.display(chart);
        }
    }

    /**
     * Persists the supplied chart. Uses the default save directory if configured
     * via constructor, otherwise saves to the current directory.
     *
     * @return an optional path to the stored chart
     * @since 0.19
     */
    public Optional<Path> saveChartImage(JFreeChart chart, BarSeries series, String chartFileName) {
        return saveChartImage(chart, series, chartFileName, DEFAULT_CHART_IMAGE_WIDTH, DEFAULT_CHART_IMAGE_HEIGHT);
    }

    /**
     * Persists the supplied chart. Uses the default save directory if configured
     * via constructor, otherwise saves to the current directory.
     *
     * @param chart         the chart to persist
     * @param series        the originating bar series
     * @param chartFileName the filename for the chart (optional)
     * @param imageWidth    exported image width
     * @param imageHeight   exported image height
     * @return an optional path to the stored chart
     * @since 0.22.7
     */
    public Optional<Path> saveChartImage(JFreeChart chart, BarSeries series, String chartFileName, int imageWidth,
            int imageHeight) {
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null");
        }
        validateSeries(series);
        validateImageSize(imageWidth, imageHeight);
        String effectiveFileName = (chartFileName != null && !chartFileName.trim().isEmpty()) ? chartFileName
                : (chart.getTitle() != null ? chart.getTitle().getText()
                        : chartFactory.buildChartTitle(series.getName(), ""));

        // Try using constructor storage first, if it returns empty (no-op), use current
        // directory
        Optional<Path> result = chartStorage.save(chart, series, effectiveFileName, imageWidth, imageHeight);
        if (result.isEmpty()) {
            // Constructor storage is no-op, use current directory
            ChartStorage currentDirStorage = new FileSystemChartStorage(Paths.get("."));
            return currentDirStorage.save(chart, series, effectiveFileName, imageWidth, imageHeight);
        }
        return result;
    }

    /**
     * Saves a chart image to a file path. Uses the default save directory if
     * configured via constructor, otherwise saves to the current directory.
     *
     * @param chart  the JFreeChart object to be saved as an image
     * @param series the BarSeries object containing chart data
     * @return an Optional containing the Path where the chart image was saved, or
     *         empty if saving failed
     * @since 0.19
     */
    public Optional<Path> saveChartImage(JFreeChart chart, BarSeries series) {
        return saveChartImage(chart, series, (String) null);
    }

    /**
     * Saves a chart image to a file path using an explicit export resolution. Uses
     * the default save directory if configured via constructor, otherwise saves to
     * the current directory.
     *
     * @param chart       the JFreeChart object to be saved as an image
     * @param series      the BarSeries object containing chart data
     * @param imageWidth  exported image width
     * @param imageHeight exported image height
     * @return an Optional containing the Path where the chart image was saved, or
     *         empty if saving failed
     * @since 0.22.7
     */
    public Optional<Path> saveChartImage(JFreeChart chart, BarSeries series, int imageWidth, int imageHeight) {
        return saveChartImage(chart, series, (String) null, imageWidth, imageHeight);
    }

    /**
     * Persists the supplied chart to the specified directory.
     *
     * @param chart                   the chart to persist
     * @param series                  the originating bar series
     * @param chartImageSaveDirectory the directory to save the chart image to
     * @return an optional path to the stored chart
     * @since 0.19
     */
    public Optional<Path> saveChartImage(JFreeChart chart, BarSeries series, Path chartImageSaveDirectory) {
        return saveChartImage(chart, series, chartImageSaveDirectory, DEFAULT_CHART_IMAGE_WIDTH,
                DEFAULT_CHART_IMAGE_HEIGHT);
    }

    /**
     * Persists the supplied chart to the specified directory using an explicit
     * export resolution.
     *
     * @param chart                   the chart to persist
     * @param series                  the originating bar series
     * @param chartImageSaveDirectory the directory to save the chart image to
     * @param imageWidth              exported image width
     * @param imageHeight             exported image height
     * @return an optional path to the stored chart
     * @since 0.22.7
     */
    public Optional<Path> saveChartImage(JFreeChart chart, BarSeries series, Path chartImageSaveDirectory,
            int imageWidth, int imageHeight) {
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null");
        }
        if (chartImageSaveDirectory == null) {
            throw new IllegalArgumentException("Chart image save directory cannot be null");
        }
        return saveChartImage(chart, series, null, chartImageSaveDirectory.toString(), imageWidth, imageHeight);
    }

    /**
     * Persists the supplied chart to the specified directory.
     *
     * @param chart                   the chart to persist
     * @param series                  the originating bar series
     * @param chartFileName           the filename for the chart (optional, can be
     *                                null)
     * @param chartImageSaveDirectory the directory to save the chart image to
     * @return an optional path to the stored chart
     * @since 0.19
     */
    public Optional<Path> saveChartImage(JFreeChart chart, BarSeries series, String chartFileName,
            String chartImageSaveDirectory) {
        return saveChartImage(chart, series, chartFileName, chartImageSaveDirectory, DEFAULT_CHART_IMAGE_WIDTH,
                DEFAULT_CHART_IMAGE_HEIGHT);
    }

    /**
     * Persists the supplied chart to the specified directory using an explicit
     * export resolution.
     *
     * @param chart                   the chart to persist
     * @param series                  the originating bar series
     * @param chartFileName           the filename for the chart (optional, can be
     *                                null)
     * @param chartImageSaveDirectory the directory to save the chart image to
     * @param imageWidth              exported image width
     * @param imageHeight             exported image height
     * @return an optional path to the stored chart
     * @since 0.22.7
     */
    public Optional<Path> saveChartImage(JFreeChart chart, BarSeries series, String chartFileName,
            String chartImageSaveDirectory, int imageWidth, int imageHeight) {
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null");
        }
        validateSeries(series);
        if (chartImageSaveDirectory == null || chartImageSaveDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("Chart image save directory cannot be null or empty");
        }
        validateImageSize(imageWidth, imageHeight);
        String effectiveFileName = (chartFileName != null && !chartFileName.trim().isEmpty()) ? chartFileName
                : (chart.getTitle() != null ? chart.getTitle().getText()
                        : chartFactory.buildChartTitle(series.getName(), ""));
        ChartStorage customStorage = new FileSystemChartStorage(resolveSaveDirectory(chartImageSaveDirectory));
        return customStorage.save(chart, series, effectiveFileName, imageWidth, imageHeight);
    }

    /**
     * Converts a chart into PNG bytes.
     *
     * @since 0.19
     */
    public byte[] getChartAsByteArray(JFreeChart chart) {
        return getChartAsByteArray(chart, DEFAULT_CHART_IMAGE_WIDTH, DEFAULT_CHART_IMAGE_HEIGHT);
    }

    /**
     * Converts a chart into PNG bytes using an explicit export resolution.
     *
     * @param chart       the chart to encode
     * @param imageWidth  exported image width
     * @param imageHeight exported image height
     * @return the encoded PNG bytes
     * @since 0.22.7
     */
    public byte[] getChartAsByteArray(JFreeChart chart, int imageWidth, int imageHeight) {
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null");
        }
        validateImageSize(imageWidth, imageHeight);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ChartUtils.writeChartAsPNG(out, chart, imageWidth, imageHeight);
        } catch (IOException ex) {
            LOG.error("Failed to write chart to byte array", ex);
        }
        return out.toByteArray();
    }

    private JFreeChart buildTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord,
            TimeAxisMode timeAxisMode, Indicator<Num>[] indicators, boolean validateIndicators) {
        validateTradingInputs(series, strategyName, tradingRecord);
        validateTimeAxisMode(timeAxisMode);
        if (validateIndicators) {
            validateIndicators(indicators);
        }
        return chartFactory.createTradingRecordChart(series, strategyName, tradingRecord, timeAxisMode, indicators);
    }

    @SafeVarargs
    private final JFreeChart buildIndicatorChart(BarSeries series, TimeAxisMode timeAxisMode,
            Indicator<Num>... indicators) {
        validateSeries(series);
        validateTimeAxisMode(timeAxisMode);
        validateIndicators(indicators);
        return chartFactory.createIndicatorChart(series, timeAxisMode, indicators);
    }

    private String resolveChartTitle(JFreeChart chart, BarSeries series, String strategyName) {
        return chart.getTitle() != null ? chart.getTitle().getText()
                : chartFactory.buildChartTitle(series.getName(), strategyName);
    }

    private void displayChartSafely(Supplier<JFreeChart> chartSupplier, String windowTitle, String message,
            Object... args) {
        try {
            displayChart(chartSupplier.get(), windowTitle);
        } catch (Exception ex) {
            LOG.error(message, appendArgs(args, ex));
        }
    }

    private static Object[] appendArgs(Object[] args, Object extra) {
        if (args == null || args.length == 0) {
            return new Object[] { extra };
        }
        Object[] extended = Arrays.copyOf(args, args.length + 1);
        extended[extended.length - 1] = extra;
        return extended;
    }

    private void validateTradingInputs(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        validateSeries(series);
        if (strategyName == null || strategyName.trim().isEmpty()) {
            throw new IllegalArgumentException("Strategy name cannot be null or empty");
        }
        if (tradingRecord == null) {
            throw new IllegalArgumentException("Trading record cannot be null");
        }
    }

    private void validateIndicators(Indicator<Num>[] indicators) {
        if (indicators == null) {
            throw new IllegalArgumentException("Indicators cannot be null");
        }
        for (Indicator<Num> indicator : indicators) {
            if (indicator == null) {
                throw new IllegalArgumentException("Indicators cannot contain null values");
            }
        }
    }

    private void validateTimeAxisMode(TimeAxisMode timeAxisMode) {
        Objects.requireNonNull(timeAxisMode, "Time axis mode cannot be null");
    }

    private void validateImageSize(int imageWidth, int imageHeight) {
        if (imageWidth <= 0) {
            throw new IllegalArgumentException("Image width must be positive");
        }
        if (imageHeight <= 0) {
            throw new IllegalArgumentException("Image height must be positive");
        }
    }

    private void validateSeries(BarSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null");
        }
        if (series.getBarCount() == 0) {
            throw new IllegalArgumentException("Series must contain at least one bar");
        }
    }

    private String safeSeriesName(BarSeries series) {
        if (series == null) {
            return "unknown-series";
        }
        String name = series.getName();
        return name != null ? name : "unnamed-series";
    }

    private static Path resolveSaveDirectory(String directory) {
        if (directory == null || directory.trim().isEmpty()) {
            throw new IllegalArgumentException("Chart image save directory cannot be null or empty");
        }
        return Paths.get(directory);
    }
}
