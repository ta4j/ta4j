/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.swing;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.num.Num;

/**
 * Swing detector that adapts the ZigZag reversal threshold to volatility.
 *
 * <p>
 * Use this detector when a fixed reversal threshold is too rigid for changing
 * volatility regimes. It derives reversal thresholds from ATR, optionally
 * smoothed, and feeds the result into a ZigZag-based swing detector. Repeated
 * detection on the same series and degree reuses one bounded indicator pipeline
 * so live updates advance the recursive ZigZag state instead of rescanning the
 * full history. Historical queries that move backward rebuild the pipeline to
 * preserve causal pivot confirmation.
 *
 * <p>
 * Series without bar-history revisions are revalidated by retained high, low
 * and close values before and after evaluation. A changed history rebuilds the
 * detector's dependent indicators; supported revisions keep constant-size
 * history checks. Indicator evaluation runs outside the detector's series read
 * scopes.
 *
 * @since 0.22.2
 */
public final class AdaptiveZigZagSwingDetector implements SwingDetector {

    private final AdaptiveZigZagConfig config;
    private final ReentrantLock detectionLock = new ReentrantLock();
    private WeakReference<BarSeries> cachedSeries = new WeakReference<>(null);
    private ElliottDegree cachedDegree;
    private ElliottSwingIndicator cachedIndicator;
    private int cachedIndex = -1;
    private SwingHistorySnapshot observedHistory;

    /**
     * Creates a detector using the supplied configuration.
     *
     * @param config adaptive ZigZag configuration
     * @since 0.22.2
     */
    public AdaptiveZigZagSwingDetector(final AdaptiveZigZagConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public SwingDetectorResult detect(final BarSeries series, final int index, final ElliottDegree degree) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        // Never wait for an owner that may itself be waiting to read this series.
        // A caller can already hold the series lock; contention uses a fresh graph.
        if (!detectionLock.tryLock()) {
            return new AdaptiveZigZagSwingDetector(config).detect(series, index, degree);
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
                if (cachedIndicator == null || cachedSeries.get() != series || cachedDegree != degree || changed
                        || clampedIndex < cachedIndex) {
                    final Indicator<Num> highPrice = new HighPriceIndicator(series);
                    final Indicator<Num> lowPrice = new LowPriceIndicator(series);
                    final Indicator<Num> atr = new ATRIndicator(series, config.atrPeriod());
                    final Indicator<Num> smoothedAtr = config.smoothingPeriod() > 1
                            ? new SMAIndicator(atr, config.smoothingPeriod())
                            : atr;
                    final Indicator<Num> threshold = new AdaptiveZigZagThresholdIndicator(smoothedAtr, config);
                    final ZigZagStateIndicator state = new ZigZagStateIndicator(highPrice, lowPrice, threshold);
                    cachedSeries = new WeakReference<>(series);
                    cachedDegree = degree;
                    cachedIndicator = ElliottSwingIndicator.zigZag(state, highPrice, lowPrice, degree);
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
                    cachedIndex = clampedIndex;
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
     * @return adaptive configuration
     * @since 0.22.2
     */
    public AdaptiveZigZagConfig getConfig() {
        return config;
    }

    private static final class AdaptiveZigZagThresholdIndicator extends CachedIndicator<Num> {

        private final Indicator<Num> baseIndicator;
        private final AdaptiveZigZagConfig config;
        private final Num minThreshold;
        private final Num maxThreshold;
        private final Num multiplier;

        private AdaptiveZigZagThresholdIndicator(final Indicator<Num> baseIndicator,
                final AdaptiveZigZagConfig config) {
            super(baseIndicator.getBarSeries());
            this.baseIndicator = Objects.requireNonNull(baseIndicator, "baseIndicator");
            this.config = Objects.requireNonNull(config, "config");
            this.multiplier = getBarSeries().numFactory().numOf(config.atrMultiplier());
            this.minThreshold = getBarSeries().numFactory().numOf(config.minThreshold());
            this.maxThreshold = getBarSeries().numFactory().numOf(config.maxThreshold());
        }

        @Override
        protected Num calculate(final int index) {
            Num base = baseIndicator.getValue(index);
            if (Num.isNaNOrNull(base)) {
                return base;
            }
            Num value = base.multipliedBy(multiplier);
            if (config.hasMinClamp() && value.isLessThan(minThreshold)) {
                value = minThreshold;
            }
            if (config.hasMaxClamp() && value.isGreaterThan(maxThreshold)) {
                value = maxThreshold;
            }
            return value;
        }

        @Override
        public int getCountOfUnstableBars() {
            return baseIndicator.getCountOfUnstableBars();
        }
    }
}
