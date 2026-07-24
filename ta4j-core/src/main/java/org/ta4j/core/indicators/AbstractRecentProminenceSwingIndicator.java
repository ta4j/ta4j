/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Shared causal prominence detection for recent swing highs and lows.
 */
abstract class AbstractRecentProminenceSwingIndicator extends AbstractRecentSwingIndicator {

    private final transient Indicator<Num> priceIndicator;
    private final Indicator<Num> minimumProminence;
    private final transient ProminenceSwingConfig config;
    private final transient FractalDetectionHelper.Direction direction;
    private final int lookbackBars;
    private final int confirmationBars;
    private final int allowedEqualBars;
    private final int atrPeriod;
    private final double minAtrProminence;

    protected AbstractRecentProminenceSwingIndicator(final Indicator<Num> priceIndicator,
            final Indicator<Num> minimumProminence, final ProminenceSwingConfig config,
            final FractalDetectionHelper.Direction direction) {
        super(requirePrice(priceIndicator), unstableBars(priceIndicator, minimumProminence, config));
        this.priceIndicator = priceIndicator;
        this.minimumProminence = Objects.requireNonNull(minimumProminence, "minimumProminence");
        IndicatorUtils.requireSameSeries(priceIndicator, minimumProminence);
        this.config = Objects.requireNonNull(config, "config");
        this.direction = Objects.requireNonNull(direction, "direction");
        this.lookbackBars = config.lookbackBars();
        this.confirmationBars = config.confirmationBars();
        this.allowedEqualBars = config.allowedEqualBars();
        this.atrPeriod = config.atrPeriod();
        this.minAtrProminence = config.minAtrProminence();
    }

    protected static Indicator<Num> atrProminence(final BarSeries series, final ProminenceSwingConfig config) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(config, "config");
        return NumericIndicator.of(new ATRIndicator(series, config.atrPeriod()))
                .multipliedBy(config.minAtrProminence());
    }

    protected final ProminenceSwingConfig config() {
        return config;
    }

    @Override
    protected final int detectLatestSwingIndex(final int index) {
        final BarSeries series = getBarSeries();
        final int beginIndex = series.getBeginIndex();
        if (index < beginIndex || index > series.getEndIndex()) {
            return -1;
        }
        final int latestCandidate = index - config.confirmationBars();
        for (int candidate = latestCandidate; candidate > beginIndex; candidate--) {
            if (FractalDetectionHelper.isConfirmedFractal(priceIndicator, series, candidate, 1,
                    config.confirmationBars(), index, config.allowedEqualBars(), direction)
                    && hasRequiredProminence(candidate, index)) {
                return candidate;
            }
        }
        return -1;
    }

    private boolean hasRequiredProminence(final int candidateIndex, final int evaluationIndex) {
        final int beginIndex = getBarSeries().getBeginIndex();
        final int leftStart = Math.max(beginIndex, candidateIndex - config.lookbackBars());
        final int rightEnd = Math.min(evaluationIndex, candidateIndex + config.lookbackBars());
        if (leftStart >= candidateIndex || rightEnd <= candidateIndex) {
            return false;
        }
        final Num candidate = priceIndicator.getValue(candidateIndex);
        final Num threshold = minimumProminence.getValue(candidateIndex);
        if (!Num.isFinite(candidate) || !Num.isFinite(threshold) || threshold.isNegative()) {
            return false;
        }

        final Num leftBase = sideBase(candidate, candidateIndex - 1, leftStart, -1);
        final Num rightBase = sideBase(candidate, candidateIndex + 1, rightEnd, 1);
        if (!Num.isFinite(leftBase) || !Num.isFinite(rightBase)) {
            return false;
        }

        final Num contour;
        final Num prominence;
        if (direction == FractalDetectionHelper.Direction.HIGH) {
            contour = leftBase.isGreaterThan(rightBase) ? leftBase : rightBase;
            prominence = candidate.minus(contour);
        } else {
            contour = leftBase.isLessThan(rightBase) ? leftBase : rightBase;
            prominence = contour.minus(candidate);
        }
        return Num.isFinite(prominence) && prominence.isGreaterThanOrEqual(threshold);
    }

    private Num sideBase(final Num candidate, final int start, final int boundary, final int step) {
        Num base = null;
        for (int index = start; step < 0 ? index >= boundary : index <= boundary; index += step) {
            final Num value = priceIndicator.getValue(index);
            if (!Num.isFinite(value)) {
                return null;
            }
            if (direction == FractalDetectionHelper.Direction.HIGH && value.isGreaterThan(candidate)
                    || direction == FractalDetectionHelper.Direction.LOW && value.isLessThan(candidate)) {
                break;
            }
            if (base == null || direction == FractalDetectionHelper.Direction.HIGH && value.isLessThan(base)
                    || direction == FractalDetectionHelper.Direction.LOW && value.isGreaterThan(base)) {
                base = value;
            }
        }
        return base;
    }

    private static Indicator<Num> requirePrice(final Indicator<Num> priceIndicator) {
        return IndicatorUtils.requireIndicator(priceIndicator, "priceIndicator");
    }

    private static int unstableBars(final Indicator<Num> priceIndicator, final Indicator<Num> minimumProminence,
            final ProminenceSwingConfig config) {
        requirePrice(priceIndicator);
        Objects.requireNonNull(minimumProminence, "minimumProminence");
        Objects.requireNonNull(config, "config");
        return Math.max(priceIndicator.getCountOfUnstableBars(), minimumProminence.getCountOfUnstableBars())
                + config.lookbackBars() + config.confirmationBars();
    }
}
