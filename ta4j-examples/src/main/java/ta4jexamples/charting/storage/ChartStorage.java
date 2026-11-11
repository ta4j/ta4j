/*
 * SPDX-License-Identifier: MIT
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
