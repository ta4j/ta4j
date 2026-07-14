/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Bar;
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
 * @since 0.22.2
 */
public final class AdaptiveZigZagSwingDetector implements SwingDetector {

    private final AdaptiveZigZagConfig config;
    private WeakReference<BarSeries> cachedSeries = new WeakReference<>(null);
    private ElliottDegree cachedDegree;
    private ElliottSwingIndicator cachedIndicator;
    private int cachedIndex = -1;
    private Bar cachedFirstBar;
    private Bar cachedLastBar;
    private int cachedBeginIndex = -1;
    private int cachedEndIndex = -1;

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
    public synchronized SwingDetectorResult detect(final BarSeries series, final int index,
            final ElliottDegree degree) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        if (series.isEmpty()) {
            return new SwingDetectorResult(List.of(), List.of());
        }
        final int currentEndIndex = series.getEndIndex();
        final Bar currentLastBar = series.getBar(currentEndIndex);
        final int clampedIndex = Math.max(series.getBeginIndex(), Math.min(index, currentEndIndex));
        final boolean historyReplaced = cachedIndicator != null && cachedSeries.get() == series
                && (currentEndIndex < cachedEndIndex
                        || (series.getBeginIndex() <= cachedBeginIndex
                                && series.getBar(series.getBeginIndex()) != cachedFirstBar)
                        || (currentEndIndex == cachedEndIndex && currentLastBar != cachedLastBar));
        if (cachedIndicator == null || cachedSeries.get() != series || cachedDegree != degree || historyReplaced
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
            cachedFirstBar = series.getBar(series.getBeginIndex());
            cachedBeginIndex = series.getBeginIndex();
        }
        final SwingDetectorResult result = SwingDetectorResult.fromSwings(cachedIndicator.getValue(clampedIndex));
        cachedIndex = clampedIndex;
        cachedEndIndex = currentEndIndex;
        cachedLastBar = currentLastBar;
        return result;
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
