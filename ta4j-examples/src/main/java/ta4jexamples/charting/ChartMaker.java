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
import java.util.Objects;
import java.util.Optional;

/**
 * Facade for composing, displaying, and persisting TA4J charts.
 *
 * <p>
 * The refactored {@link ChartMaker} decomposes responsibilities into dedicated
 * collaborators:
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
public class ChartMaker {

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

    private static final Logger LOG = LogManager.getLogger(ChartMaker.class);

    private final TradingChartFactory chartFactory;
    private final ChartDisplayer chartDisplayer;
    private final ChartStorage chartStorage;

    /**
     * Creates a {@link ChartMaker} that supports chart composition and display but
     * omits chart persistence.
     *
     * @since 0.19
     */
    public ChartMaker() {
        this(new TradingChartFactory(), new SwingChartDisplayer(), ChartStorage.noOp());
    }

    /**
     * Creates a {@link ChartMaker} with filesystem persistence enabled.
     *
     * @param chartImageSaveDirectory the directory to store generated chart images
     * @since 0.19
     */
    public ChartMaker(String chartImageSaveDirectory) {
        this(new TradingChartFactory(), new SwingChartDisplayer(),
                new FileSystemChartStorage(resolveSaveDirectory(chartImageSaveDirectory)));
    }

    ChartMaker(TradingChartFactory chartFactory, ChartDisplayer chartDisplayer, ChartStorage chartStorage) {
        this.chartFactory = Objects.requireNonNull(chartFactory, "Chart factory cannot be null");
        this.chartDisplayer = Objects.requireNonNull(chartDisplayer, "Chart displayer cannot be null");
        this.chartStorage = Objects.requireNonNull(chartStorage, "Chart storage cannot be null");
    }

    /**
     * Builds a chart that overlays a trading record on top of OHLC data.
     *
     * @since 0.19
     */
    public JFreeChart createTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        validateTradingInputs(series, strategyName, tradingRecord);
        return chartFactory.createTradingRecordChart(series, strategyName, tradingRecord);
    }

    /**
     * Persists a trading record chart if persistence is configured.
     *
     * @return an optional path to the stored chart
     * @since 0.19
     */
    public Optional<Path> saveTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        validateTradingInputs(series, strategyName, tradingRecord);
        JFreeChart chart = chartFactory.createTradingRecordChart(series, strategyName, tradingRecord);
        String chartTitle = chart.getTitle() != null ? chart.getTitle().getText()
                : chartFactory.buildChartTitle(series.getName(), strategyName);
        return chartStorage.save(chart, series, chartTitle, DEFAULT_CHART_IMAGE_WIDTH, DEFAULT_CHART_IMAGE_HEIGHT);
    }

    /**
     * Displays a trading record chart, logging any presentation exceptions.
     *
     * @since 0.19
     */
    public void displayTradingRecordChart(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        validateTradingInputs(series, strategyName, tradingRecord);
        try {
            JFreeChart chart = chartFactory.createTradingRecordChart(series, strategyName, tradingRecord);
            displayChart(chart);
        } catch (Exception ex) {
            LOG.error("Failed to display trading record chart for {}@{}", strategyName, safeSeriesName(series), ex);
        }
    }

    /**
     * Produces a PNG representation of a trading record chart.
     *
     * @since 0.19
     */
    public byte[] createTradingRecordChartBytes(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        validateTradingInputs(series, strategyName, tradingRecord);
        JFreeChart chart = chartFactory.createTradingRecordChart(series, strategyName, tradingRecord);
        return getChartAsByteArray(chart);
    }

    /**
     * Builds an indicator overlay chart.
     *
     * @since 0.19
     */
    @SafeVarargs
    public final JFreeChart createIndicatorChart(BarSeries series, Indicator<Num>... indicators) {
        validateSeries(series);
        if (indicators == null) {
            throw new IllegalArgumentException("Indicators cannot be null");
        }
        for (Indicator<Num> indicator : indicators) {
            if (indicator == null) {
                throw new IllegalArgumentException("Indicators cannot contain null values");
            }
        }
        return chartFactory.createIndicatorChart(series, indicators);
    }

    /**
     * Displays an indicator overlay chart.
     *
     * @since 0.19
     */
    @SafeVarargs
    public final void displayIndicatorChart(BarSeries series, Indicator<Num>... indicators) {
        try {
            JFreeChart chart = createIndicatorChart(series, indicators);
            displayChart(chart);
        } catch (Exception ex) {
            LOG.error("Failed to display indicator chart for {}", safeSeriesName(series), ex);
        }
    }

    /**
     * Builds an analysis overlay chart.
     *
     * @since 0.19
     */
    public JFreeChart createAnalysisChart(BarSeries series, AnalysisType... analysisTypes) {
        validateSeries(series);
        if (analysisTypes == null) {
            throw new IllegalArgumentException("Analysis types cannot be null");
        }
        for (AnalysisType analysisType : analysisTypes) {
            if (analysisType == null) {
                throw new IllegalArgumentException("Analysis types cannot contain null values");
            }
        }
        return chartFactory.createAnalysisChart(series, analysisTypes);
    }

    /**
     * Displays an analysis overlay chart.
     *
     * @since 0.19
     */
    public void displayAnalysisChart(BarSeries series, AnalysisType... analysisTypes) {
        try {
            JFreeChart chart = createAnalysisChart(series, analysisTypes);
            displayChart(chart);
        } catch (Exception ex) {
            LOG.error("Failed to display analysis chart for {}", safeSeriesName(series), ex);
        }
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
        return createDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator, secondaryLabel, null);
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
        return chartFactory.createDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator,
                secondaryLabel, chartTitle);
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
                safeSeriesName(series), null);
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
        try {
            JFreeChart chart = createDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator,
                    secondaryLabel, chartTitle);
            if (windowTitle != null && !windowTitle.trim().isEmpty()) {
                chartDisplayer.display(chart, windowTitle);
            } else {
                chartDisplayer.display(chart);
            }
        } catch (Exception ex) {
            LOG.error("Failed to display dual-axis chart for {}", safeSeriesName(series), ex);
        }
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
     * Persists the supplied chart.
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
        return chartStorage.save(chart, series, effectiveFileName, DEFAULT_CHART_IMAGE_WIDTH, DEFAULT_CHART_IMAGE_HEIGHT);
    }

    /**
     * Saves a chart image to a file path.
     * @param chart the JFreeChart object to be saved as an image
     * @param series the BarSeries object containing chart data
     * @return an Optional containing the Path where the chart image was saved, or empty if saving failed
     */
    public Optional<Path> saveChartImage(JFreeChart chart, BarSeries series) {
        return saveChartImage(chart, series, null);
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

    /**
     * Legacy convenience method retained for backwards compatibility.
     *
     * @deprecated use
     *             {@link #saveTradingRecordChart(BarSeries, String, TradingRecord)}
     */
    @Deprecated
    public String generateAndSaveChartImage(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        return saveTradingRecordChart(series, strategyName, tradingRecord).map(Path::toString).orElse(null);
    }

    /**
     * Legacy convenience method retained for backwards compatibility.
     *
     * @deprecated use
     *             {@link #displayTradingRecordChart(BarSeries, String, TradingRecord)}
     */
    @Deprecated
    public void generateAndDisplayTradingRecordChart(BarSeries series, String strategyName,
            TradingRecord tradingRecord) {
        displayTradingRecordChart(series, strategyName, tradingRecord);
    }

    /**
     * Legacy convenience method retained for backwards compatibility.
     *
     * @deprecated use {@link #displayAnalysisChart(BarSeries, AnalysisType...)}
     */
    @Deprecated
    public void generateAndDisplayChart(BarSeries series, AnalysisType... analysisTypes) {
        displayAnalysisChart(series, analysisTypes);
    }

    /**
     * Legacy convenience method retained for backwards compatibility.
     *
     * @deprecated use {@link #displayIndicatorChart(BarSeries, Indicator[])}
     */
    @Deprecated
    @SafeVarargs
    public final void generateAndDisplayChart(BarSeries series, Indicator<Num>... indicators) {
        displayIndicatorChart(series, indicators);
    }

    /**
     * Legacy convenience method retained for backwards compatibility.
     *
     * @deprecated use
     *             {@link #createTradingRecordChart(BarSeries, String, TradingRecord)}
     */
    @Deprecated
    public JFreeChart generateChart(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        return createTradingRecordChart(series, strategyName, tradingRecord);
    }

    /**
     * Legacy convenience method retained for backwards compatibility.
     *
     * @deprecated use
     *             {@link #createTradingRecordChartBytes(BarSeries, String, TradingRecord)}
     */
    @Deprecated
    public byte[] generateChartAsBytes(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        return createTradingRecordChartBytes(series, strategyName, tradingRecord);
    }

    /**
     * Legacy convenience method retained for backwards compatibility.
     *
     * @deprecated use {@link #createIndicatorChart(BarSeries, Indicator[])}
     */
    @Deprecated
    @SafeVarargs
    public final JFreeChart generateChart(BarSeries series, Indicator<Num>... indicators) {
        return createIndicatorChart(series, indicators);
    }

    /**
     * Legacy convenience method retained for backwards compatibility.
     *
     * @deprecated use {@link #createAnalysisChart(BarSeries, AnalysisType...)}
     */
    @Deprecated
    public JFreeChart generateChart(BarSeries series, AnalysisType... analysisTypes) {
        return createAnalysisChart(series, analysisTypes);
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
