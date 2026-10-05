/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.swing;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import org.ta4j.core.BarSeries;
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
 * <p>
 * Series without bar-history revisions are revalidated by retained high, low
 * and close values before and after evaluation. A changed history rebuilds the
 * detector's dependent indicators; supported revisions keep constant-size
 * history checks. Indicator evaluation runs outside the detector's series read
 * scopes. Queries retry until the before/after observations agree; continuous
 * mutation can prevent a query from completing.
 *
 * @since 0.23.1
 */
public final class ProminenceSwingDetector implements SwingDetector {

    private final ProminenceSwingConfig config;
    private final ReentrantLock detectionLock = new ReentrantLock();
    private transient WeakReference<BarSeries> cachedSeries = new WeakReference<>(null);
    private transient ElliottDegree cachedDegree;
    private transient ElliottSwingIndicator cachedIndicator;
    private SwingHistorySnapshot observedHistory;

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
        // Never wait for an owner that may itself be waiting to read this series.
        // A caller can already hold the series lock; contention uses a fresh graph.
        if (!detectionLock.tryLock()) {
            return new ProminenceSwingDetector(config).detect(series, index, degree);
        }
        try {
            while (true) {
                final boolean changed = series.withReadLock(() -> {
                    final boolean historyChanged = cachedIndicator != null && cachedSeries.get() == series
                            && observedHistory.hasChangedIn(series, false);
                    observedHistory = SwingHistorySnapshot.capture(series);
                    return historyChanged;
                });
                if (observedHistory.endIndex() < observedHistory.beginIndex()) {
                    cachedIndicator = null;
                    return new SwingDetectorResult(List.of(), List.of());
                }
                final int clampedIndex = Math.max(observedHistory.beginIndex(),
                        Math.min(index, observedHistory.endIndex()));
                if (cachedIndicator == null || cachedSeries.get() != series || cachedDegree != degree || changed) {
                    cachedSeries = new WeakReference<>(series);
                    cachedDegree = degree;
                    cachedIndicator = new ElliottSwingIndicator(new RecentProminenceSwingHighIndicator(series, config),
                            new RecentProminenceSwingLowIndicator(series, config), degree);
                }
                SwingDetectorResult result = null;
                IndexOutOfBoundsException evaluationFailure = null;
                try {
                    // Indicator caches acquire their own locks before reading bars.
                    // The graph must run outside our short series read scopes.
                    result = SwingDetectorResult.fromSwings(cachedIndicator.getValue(clampedIndex));
                } catch (IndexOutOfBoundsException exception) {
                    evaluationFailure = exception;
                }
                final boolean changedDuringEvaluation = series
                        .withReadLock(() -> observedHistory.hasChangedIn(series, true));
                if (!changedDuringEvaluation && evaluationFailure == null) {
                    return result;
                }
                // Discard the complete dependent graph, including ATR and recursive
                // state, so a retry cannot inherit values from another history.
                cachedIndicator = null;
                observedHistory = null;
                if (!changedDuringEvaluation) {
                    throw evaluationFailure;
                }
            }
        } finally {
            detectionLock.unlock();
        }
    }

    /**
     * @return prominence configuration
     * @since 0.23.1
     */
    public ProminenceSwingConfig getConfig() {
        return config;
    }

}
