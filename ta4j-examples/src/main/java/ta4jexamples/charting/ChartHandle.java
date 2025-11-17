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

import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Handle for a built chart that provides fluent action methods for display and
 * persistence.
 *
 * <p>
 * This class wraps a {@link JFreeChart} instance along with its associated
 * {@link BarSeries} and provides methods to display and save the chart. All
 * action methods return {@code this} to enable method chaining.
 * </p>
 *
 * @since 0.19
 */
public final class ChartHandle {

    private final JFreeChart chart;
    private final BarSeries series;
    private final ChartMaker chartMaker;

    ChartHandle(JFreeChart chart, BarSeries series, ChartMaker chartMaker) {
        this.chart = Objects.requireNonNull(chart, "Chart cannot be null");
        this.series = Objects.requireNonNull(series, "Series cannot be null");
        this.chartMaker = Objects.requireNonNull(chartMaker, "Chart maker cannot be null");
    }

    /**
     * Returns the underlying {@link JFreeChart} instance.
     *
     * @return the chart
     * @since 0.19
     */
    public JFreeChart getChart() {
        return chart;
    }

    /**
     * Returns the associated {@link BarSeries}.
     *
     * @return the bar series
     * @since 0.19
     */
    public BarSeries getSeries() {
        return series;
    }

    /**
     * Displays the chart using the configured displayer.
     *
     * @return this handle for method chaining
     * @since 0.19
     */
    public ChartHandle display() {
        chartMaker.displayChart(chart);
        return this;
    }

    /**
     * Displays the chart with a custom window title.
     *
     * @param windowTitle the title for the window/frame
     * @return this handle for method chaining
     * @since 0.19
     */
    public ChartHandle display(String windowTitle) {
        chartMaker.displayChart(chart, windowTitle);
        return this;
    }

    /**
     * Saves the chart to the specified directory with the specified filename.
     *
     * @param directory the directory to save the chart to
     * @param filename  the filename for the chart (without extension)
     * @return this handle for method chaining
     * @since 0.19
     */
    public ChartHandle save(String directory, String filename) {
        chartMaker.saveChartImage(chart, series, filename, directory);
        return this;
    }

    /**
     * Saves the chart to the specified directory with the specified filename.
     *
     * @param directory the directory to save the chart to
     * @param filename  the filename for the chart (without extension)
     * @return this handle for method chaining
     * @since 0.19
     */
    public ChartHandle save(Path directory, String filename) {
        return save(directory.toString(), filename);
    }

    /**
     * Saves the chart with the specified filename. Uses the default save directory
     * if configured via constructor, otherwise saves to the current directory.
     *
     * @param filename the filename for the chart (without extension)
     * @return this handle for method chaining
     * @since 0.19
     */
    public ChartHandle save(String filename) {
        chartMaker.saveChartImage(chart, series, filename);
        return this;
    }

    /**
     * Saves the chart to the specified directory with an auto-generated filename.
     *
     * @param directory the directory to save the chart to
     * @return this handle for method chaining
     * @since 0.19
     */
    public ChartHandle saveToDirectory(String directory) {
        chartMaker.saveChartImage(chart, series, null, directory);
        return this;
    }

    /**
     * Saves the chart to the specified directory with an auto-generated filename.
     *
     * @param directory the directory to save the chart to
     * @return this handle for method chaining
     * @since 0.19
     */
    public ChartHandle saveToDirectory(Path directory) {
        return saveToDirectory(directory.toString());
    }
}
