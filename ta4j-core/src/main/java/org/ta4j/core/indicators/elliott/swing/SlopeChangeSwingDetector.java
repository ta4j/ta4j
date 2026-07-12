/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Detects confirmed swing pivots from sustained changes in rolling regression
 * slope.
 *
 * <p>
 * Unlike reversal-distance ZigZag and fixed-window fractals, this detector is
 * designed for rounded turns. It compares adjacent least-squares slopes, waits
 * for the new direction to persist, then assigns the pivot to the most extreme
 * high or low in the transition interval. Detection is causal: evaluation at an
 * index never reads a later bar.
 *
 * @since 0.22.9
 */
public final class SlopeChangeSwingDetector implements SwingDetector {

    private final SlopeChangeConfig config;

    /**
     * Creates a slope-change detector with the minimum required scale parameter.
     * The detector uses {@link SlopeChangeConfig#defaults(int)} for balanced
     * persistence and magnitude filtering.
     *
     * @param window bars in each regression window
     * @since 0.22.9
     */
    public SlopeChangeSwingDetector(final int window) {
        this(SlopeChangeConfig.defaults(window));
    }

    /**
     * Creates a detector using the supplied configuration.
     *
     * @param config detector configuration
     * @since 0.22.9
     */
    public SlopeChangeSwingDetector(final SlopeChangeConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    @Override
    public SwingDetectorResult detect(final BarSeries series, final int index, final ElliottDegree degree) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(degree, "degree");
        if (series.isEmpty()) {
            return new SwingDetectorResult(List.of(), List.of());
        }
        final int endIndex = Math.max(series.getBeginIndex(), Math.min(index, series.getEndIndex()));
        final Indicator<Num> close = new ClosePriceIndicator(series);
        final Indicator<Num> high = new HighPriceIndicator(series);
        final Indicator<Num> low = new LowPriceIndicator(series);
        final Indicator<Num> atr = new ATRIndicator(series, config.atrPeriod());
        final List<SwingPivot> pivots = new ArrayList<>();
        final int firstCandidate = series.getBeginIndex() + config.window() - 1;
        final int lastCandidate = endIndex - config.window() - config.confirmationBars() + 1;
        final Num minSlopeChange = series.numFactory().numOf(config.minSlopeChange());
        final Num atrMultiplier = series.numFactory().numOf(config.minAtrReversal());

        for (int candidate = firstCandidate; candidate <= lastCandidate; candidate++) {
            final Num before = slope(close, candidate - config.window() + 1, candidate);
            final Num after = slope(close, candidate + 1, candidate + config.window());
            if (!Num.isFinite(before) || !Num.isFinite(after) || before.minus(after).abs().isLessThan(minSlopeChange)) {
                continue;
            }
            final SwingPivotType type;
            if (before.isPositive() && after.isNegative()) {
                type = SwingPivotType.HIGH;
            } else if (before.isNegative() && after.isPositive()) {
                type = SwingPivotType.LOW;
            } else {
                continue;
            }
            if (!persists(close, candidate + config.window(), type)) {
                continue;
            }
            final SwingPivot pivot = extremePivot(type == SwingPivotType.HIGH ? high : low, candidate,
                    candidate + config.window() - 1, type);
            if (pivot == null || !passesMagnitudeFilter(pivots, pivot, atr.getValue(pivot.index()), atrMultiplier)) {
                continue;
            }
            if (pivots.isEmpty()) {
                pivots.add(pivot);
                continue;
            }
            final int previousIndex = pivots.size() - 1;
            final SwingPivot previous = pivots.get(previousIndex);
            if (previous.type() != pivot.type()) {
                pivots.add(pivot);
            } else if (pivot.type() == SwingPivotType.HIGH && pivot.price().isGreaterThan(previous.price())
                    || pivot.type() == SwingPivotType.LOW && pivot.price().isLessThan(previous.price())) {
                pivots.set(previousIndex, pivot);
            }
        }
        return SwingDetectorResult.fromPivots(pivots, degree);
    }

    /**
     * @return detector configuration
     */
    public SlopeChangeConfig getConfig() {
        return config;
    }

    private boolean persists(final Indicator<Num> close, final int firstPostWindowEnd, final SwingPivotType type) {
        for (int offset = 0; offset < config.confirmationBars(); offset++) {
            final int end = firstPostWindowEnd + offset;
            final Num value = slope(close, end - config.window() + 1, end);
            if (!Num.isFinite(value) || type == SwingPivotType.HIGH && !value.isNegative()
                    || type == SwingPivotType.LOW && !value.isPositive()) {
                return false;
            }
        }
        return true;
    }

    private static Num slope(final Indicator<Num> values, final int start, final int end) {
        final Num zero = values.getBarSeries().numFactory().zero();
        Num sumX = zero;
        Num sumY = zero;
        Num sumXY = zero;
        Num sumXX = zero;
        final int count = end - start + 1;
        for (int index = start; index <= end; index++) {
            final Num y = values.getValue(index);
            if (!Num.isFinite(y)) {
                return NaN.NaN;
            }
            final Num x = values.getBarSeries().numFactory().numOf(index - start);
            sumX = sumX.plus(x);
            sumY = sumY.plus(y);
            sumXY = sumXY.plus(x.multipliedBy(y));
            sumXX = sumXX.plus(x.multipliedBy(x));
        }
        final Num n = values.getBarSeries().numFactory().numOf(count);
        final Num denominator = n.multipliedBy(sumXX).minus(sumX.multipliedBy(sumX));
        return denominator.isZero() ? zero
                : n.multipliedBy(sumXY).minus(sumX.multipliedBy(sumY)).dividedBy(denominator);
    }

    private static SwingPivot extremePivot(final Indicator<Num> values, final int start, final int end,
            final SwingPivotType type) {
        int extremeIndex = start;
        Num extreme = values.getValue(start);
        if (!Num.isFinite(extreme)) {
            return null;
        }
        for (int index = start + 1; index <= end; index++) {
            final Num value = values.getValue(index);
            if (!Num.isFinite(value)) {
                return null;
            }
            if (type == SwingPivotType.HIGH && value.isGreaterThan(extreme)
                    || type == SwingPivotType.LOW && value.isLessThan(extreme)) {
                extremeIndex = index;
                extreme = value;
            }
        }
        return new SwingPivot(extremeIndex, extreme, type);
    }

    private static boolean passesMagnitudeFilter(final List<SwingPivot> pivots, final SwingPivot candidate,
            final Num atr, final Num multiplier) {
        if (pivots.isEmpty() || multiplier.isZero()) {
            return true;
        }
        if (!Num.isFinite(atr)) {
            return false;
        }
        final SwingPivot previous = pivots.get(pivots.size() - 1);
        return candidate.price().minus(previous.price()).abs().isGreaterThanOrEqual(atr.multipliedBy(multiplier));
    }
}
