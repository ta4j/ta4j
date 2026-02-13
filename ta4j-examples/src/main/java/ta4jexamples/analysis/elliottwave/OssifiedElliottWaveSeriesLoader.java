/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import java.io.InputStream;
import java.util.Objects;

import org.apache.logging.log4j.Logger;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;

import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Utility for loading ossified Elliott Wave demo series from classpath JSON
 * resources.
 *
 * @since 0.22.2
 */
final class OssifiedElliottWaveSeriesLoader {

    /**
     * Utility class.
     */
    private OssifiedElliottWaveSeriesLoader() {
    }

    /**
     * Loads an ossified classpath dataset into a detached {@link BarSeries} with a
     * caller-provided display name.
     *
     * @param resourceOwner class used to resolve classpath resources
     * @param resource      classpath resource path
     * @param seriesName    name assigned to the returned series
     * @param logger        logger used for diagnostics
     * @return loaded series, or {@code null} when loading fails
     */
    static BarSeries loadSeries(final Class<?> resourceOwner, final String resource, final String seriesName,
            final Logger logger) {
        Objects.requireNonNull(resourceOwner, "resourceOwner");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(seriesName, "seriesName");
        Objects.requireNonNull(logger, "logger");

        try (InputStream stream = resourceOwner.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                logger.error("Missing resource: {}", resource);
                return null;
            }
            BarSeries loaded = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(stream);
            if (loaded == null) {
                logger.error("Failed to load resource: {}", resource);
                return null;
            }

            BarSeries series = new BaseBarSeriesBuilder().withName(seriesName).build();
            for (int i = 0; i < loaded.getBarCount(); i++) {
                series.addBar(loaded.getBar(i));
            }
            return series;
        } catch (Exception ex) {
            logger.error("Failed to load dataset from {}: {}", resource, ex.getMessage(), ex);
            return null;
        }
    }
}
