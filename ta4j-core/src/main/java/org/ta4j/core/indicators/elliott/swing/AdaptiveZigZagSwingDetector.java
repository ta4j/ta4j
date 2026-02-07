/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.zigzag.ZigZagStateIndicator;
import org.ta4j.core.num.Num;

/**
 * Swing detector that adapts the ZigZag reversal threshold to volatility.
 *
 * <p>
 * Use this detector when a fixed reversal threshold is too rigid for changing
 * volatility regimes. It derives reversal thresholds from ATR, optionally
 * smoothed, and feeds the result into a ZigZag-based swing detector.
 *
 * @since 0.22.2
 */
public final class AdaptiveZigZagSwingDetector implements SwingDetector {

    private final AdaptiveZigZagConfig config;

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
        if (series.isEmpty()) {
            return new SwingDetectorResult(List.of(), List.of());
        }
        final int clampedIndex = Math.max(series.getBeginIndex(), Math.min(index, series.getEndIndex()));
        final Indicator<Num> price = new ClosePriceIndicator(series);
        final Indicator<Num> atr = new ATRIndicator(series, config.atrPeriod());
        final Indicator<Num> smoothedAtr = config.smoothingPeriod() > 1
                ? new SMAIndicator(atr, config.smoothingPeriod())
                : atr;
        final Indicator<Num> threshold = new AdaptiveZigZagThresholdIndicator(smoothedAtr, config);
        final ZigZagStateIndicator state = new ZigZagStateIndicator(price, threshold);
        final ElliottSwingIndicator indicator = ElliottSwingIndicator.zigZag(state, price, degree);
        return SwingDetectorResult.fromSwings(indicator.getValue(clampedIndex));
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
