/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.ProminenceSwingConfig;
import org.ta4j.core.indicators.RecentProminenceSwingHighIndicator;
import org.ta4j.core.indicators.RecentProminenceSwingLowIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;

/**
 * Swing detector backed by bounded price-prominence highs and lows.
 *
 * <p>
 * Prominence provides a distinct middle ground between fixed-neighbor fractals
 * and reversal-distance ZigZag: candidates must be local extrema and must stand
 * materially above or below their surrounding baselines.
 *
 * @since 0.23.1
 */
public final class ProminenceSwingDetector implements SwingDetector {

    private final ProminenceSwingConfig config;

    /**
     * Creates a detector with {@link ProminenceSwingConfig#defaults()}.
     *
     * @since 0.23.1
     */
    public ProminenceSwingDetector() {
        this(ProminenceSwingConfig.defaults());
    }

    /**
     * Creates a detector with the supplied configuration.
     *
     * @param config prominence configuration
     * @since 0.23.1
     */
    public ProminenceSwingDetector(final ProminenceSwingConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public SwingDetectorResult detect(final BarSeries series, final int index, final ElliottDegree degree) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        if (series.isEmpty()) {
            return new SwingDetectorResult(List.of(), List.of());
        }
        final int clampedIndex = Math.max(series.getBeginIndex(), Math.min(index, series.getEndIndex()));
        final ElliottSwingIndicator indicator = new ElliottSwingIndicator(
                new RecentProminenceSwingHighIndicator(series, config),
                new RecentProminenceSwingLowIndicator(series, config), degree);
        return SwingDetectorResult.fromSwings(indicator.getValue(clampedIndex));
    }

    /**
     * @return prominence configuration
     * @since 0.23.1
     */
    public ProminenceSwingConfig getConfig() {
        return config;
    }
}
