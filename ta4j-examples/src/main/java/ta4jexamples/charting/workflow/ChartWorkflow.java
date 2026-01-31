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
package ta4jexamples.charting.workflow;

import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

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
     * Default chart image width.
     *
     * @since 0.19
     */
    static final int DEFAULT_CHART_IMAGE_WIDTH = 1920;

    /**
     * Default chart image height.
     *
     * @since 0.19
     */
    static final int DEFAULT_CHART_IMAGE_HEIGHT = 1080;

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
     * @since 0.23
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
     * @since 0.23
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
     * @since 0.23
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
     * @since 0.23
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
     * @since 0.23
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
     * @since 0.23
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
     * @since 0.23
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
     * @since 0.23
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
     * @since 0.23
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
     * @since 0.23
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
     * @since 0.23
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
     * @since 0.23
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
     * @since 0.23
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
     * @since 0.23
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
        displayChart(chart, null);
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
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null");
        }
        if (windowTitle != null && !windowTitle.trim().isEmpty()) {
            chartDisplayer.display(chart, windowTitle);
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
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null");
        }
        validateSeries(series);
        String effectiveFileName = (chartFileName != null && !chartFileName.trim().isEmpty()) ? chartFileName
                : (chart.getTitle() != null ? chart.getTitle().getText()
                        : chartFactory.buildChartTitle(series.getName(), ""));

        // Try using constructor storage first, if it returns empty (no-op), use current
        // directory
        Optional<Path> result = chartStorage.save(chart, series, effectiveFileName, DEFAULT_CHART_IMAGE_WIDTH,
                DEFAULT_CHART_IMAGE_HEIGHT);
        if (result.isEmpty()) {
            // Constructor storage is no-op, use current directory
            ChartStorage currentDirStorage = new FileSystemChartStorage(Paths.get("."));
            return currentDirStorage.save(chart, series, effectiveFileName, DEFAULT_CHART_IMAGE_WIDTH,
                    DEFAULT_CHART_IMAGE_HEIGHT);
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
     * Persists the supplied chart to the specified directory.
     *
     * @param chart                   the chart to persist
     * @param series                  the originating bar series
     * @param chartImageSaveDirectory the directory to save the chart image to
     * @return an optional path to the stored chart
     * @since 0.19
     */
    public Optional<Path> saveChartImage(JFreeChart chart, BarSeries series, Path chartImageSaveDirectory) {
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null");
        }
        if (chartImageSaveDirectory == null) {
            throw new IllegalArgumentException("Chart image save directory cannot be null");
        }
        return saveChartImage(chart, series, null, chartImageSaveDirectory.toString());
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
        if (chart == null) {
            throw new IllegalArgumentException("Chart cannot be null");
        }
        validateSeries(series);
        if (chartImageSaveDirectory == null || chartImageSaveDirectory.trim().isEmpty()) {
            throw new IllegalArgumentException("Chart image save directory cannot be null or empty");
        }
        String effectiveFileName = (chartFileName != null && !chartFileName.trim().isEmpty()) ? chartFileName
                : (chart.getTitle() != null ? chart.getTitle().getText()
                        : chartFactory.buildChartTitle(series.getName(), ""));
        ChartStorage customStorage = new FileSystemChartStorage(resolveSaveDirectory(chartImageSaveDirectory));
        return customStorage.save(chart, series, effectiveFileName, DEFAULT_CHART_IMAGE_WIDTH,
                DEFAULT_CHART_IMAGE_HEIGHT);
    }

    /**
     * Converts a chart into PNG bytes.
     *
     * @since 0.19
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
