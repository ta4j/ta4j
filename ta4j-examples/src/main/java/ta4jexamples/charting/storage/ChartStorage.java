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
package ta4jexamples.charting.storage;

import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Strategy for persisting charts.
 *
 * <p>
 * Implementations are responsible for saving chart images to a storage system.
 * The default implementation {@link FileSystemChartStorage} saves charts as
 * JPEG files to the filesystem.
 * </p>
 *
 * @since 0.19
 */
public interface ChartStorage {

    /**
     * Persists the provided chart and returns the destination path if the operation
     * succeeds.
     *
     * @param chart      the chart to persist
     * @param series     the originating bar series
     * @param chartTitle the descriptive chart title
     * @param width      target image width
     * @param height     target image height
     * @return the optional path to the persisted image
     * @since 0.19
     */
    Optional<Path> save(JFreeChart chart, BarSeries series, String chartTitle, int width, int height);

    /**
     * Creates a storage strategy that performs no persistence.
     *
     * @return a no-op storage strategy
     * @since 0.19
     */
    static ChartStorage noOp() {
        return (chart, series, chartTitle, width, height) -> Optional.empty();
    }
}
