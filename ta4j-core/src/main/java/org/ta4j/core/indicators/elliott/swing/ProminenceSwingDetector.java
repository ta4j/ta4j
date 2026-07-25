/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.Bar;
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
    private transient BarSeries cachedSeries;
    private transient ElliottDegree cachedDegree;
    private transient ElliottSwingIndicator cachedIndicator;
    private transient long observedRevision;
    private transient int observedEndIndex;
    private transient Bar observedLastBar;

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
    public synchronized SwingDetectorResult detect(final BarSeries series, final int index,
            final ElliottDegree degree) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        if (series.isEmpty()) {
            return new SwingDetectorResult(List.of(), List.of());
        }
        final int clampedIndex = Math.max(series.getBeginIndex(), Math.min(index, series.getEndIndex()));
        final ElliottSwingIndicator indicator = indicatorFor(series, degree);
        return SwingDetectorResult.fromSwings(indicator.getValue(clampedIndex));
    }

    /**
     * @return prominence configuration
     * @since 0.23.1
     */
    public ProminenceSwingConfig getConfig() {
        return config;
    }

    private ElliottSwingIndicator indicatorFor(final BarSeries series, final ElliottDegree degree) {
        if (cachedIndicator == null || cachedSeries != series || cachedDegree != degree || historyChanged(series)) {
            cachedSeries = series;
            cachedDegree = degree;
            cachedIndicator = new ElliottSwingIndicator(new RecentProminenceSwingHighIndicator(series, config),
                    new RecentProminenceSwingLowIndicator(series, config), degree);
        }
        observedRevision = series.getBarHistoryRevision();
        observedEndIndex = series.getEndIndex();
        observedLastBar = observedRevision < 0L && !series.isEmpty() ? series.getLastBar() : null;
        return cachedIndicator;
    }

    private boolean historyChanged(final BarSeries series) {
        final long currentRevision = series.getBarHistoryRevision();
        final int currentEndIndex = series.getEndIndex();
        final Bar currentLastBar = currentRevision < 0L && !series.isEmpty() ? series.getLastBar() : null;
        return currentRevision >= 0L && observedRevision >= 0L && currentRevision != observedRevision
                || currentRevision < 0L && (currentEndIndex < observedEndIndex
                        || currentEndIndex == observedEndIndex && currentLastBar != observedLastBar);
    }
}
