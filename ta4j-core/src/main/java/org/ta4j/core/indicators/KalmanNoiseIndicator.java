/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Validates and scales a dynamic Kalman noise-variance input.
 *
 * <p>
 * Kalman process and measurement noise values must be finite and strictly
 * positive. This indicator preserves that boundary without silently applying an
 * absolute value or substituting an arbitrary epsilon. Callers that
 * intentionally need a magnitude, square, or floor should compose those
 * transformations before constructing this indicator.
 *
 * <p>
 * The wrapper validates numeric shape, not domain units. A positive price or
 * volume series is not automatically a meaningful variance estimate.
 *
 * @since 0.23.1
 */
public final class KalmanNoiseIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final Num scalingFactor;

    /**
     * Creates an unscaled dynamic noise indicator.
     *
     * @param indicator positive variance source
     * @since 0.23.1
     */
    public KalmanNoiseIndicator(Indicator<Num> indicator) {
        this(indicator, 1);
    }

    /**
     * Creates a scaled dynamic noise indicator.
     *
     * @param indicator     positive variance source
     * @param scalingFactor finite, strictly positive scale
     * @since 0.23.1
     */
    public KalmanNoiseIndicator(Indicator<Num> indicator, Number scalingFactor) {
        super(Objects.requireNonNull(indicator, "indicator must not be null"));
        this.indicator = indicator;
        this.scalingFactor = requirePositive(
                indicator.getBarSeries()
                        .numFactory()
                        .numOf(Objects.requireNonNull(scalingFactor, "scalingFactor must not be null")),
                "scalingFactor");
    }

    /**
     * Creates a constant noise indicator on a bar series.
     *
     * @param series positive-noise series
     * @param value  finite, strictly positive variance
     * @return constant validated noise indicator
     * @since 0.23.1
     */
    public static KalmanNoiseIndicator constant(BarSeries series, Number value) {
        BarSeries validatedSeries = Objects.requireNonNull(series, "series must not be null");
        NumFactory numFactory = validatedSeries.numFactory();
        Num noise = requirePositive(numFactory.numOf(Objects.requireNonNull(value, "value must not be null")), "value");
        return new KalmanNoiseIndicator(new ConstantIndicator<>(validatedSeries, noise));
    }

    @Override
    protected Num calculate(int index) {
        Num value = normalize(indicator.getValue(index));
        if (value == null || !value.isPositive()) {
            return NaN.NaN;
        }
        Num scaled = value.multipliedBy(scalingFactor);
        return Num.isFinite(scaled) && scaled.isPositive() ? scaled : NaN.NaN;
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars();
    }

    private Num normalize(Num value) {
        if (!Num.isFinite(value)) {
            return null;
        }
        Num normalized = getBarSeries().numFactory().numOf(value.bigDecimalValue());
        if (!Num.isFinite(normalized) || normalized.isZero() && !value.isZero()) {
            return null;
        }
        return normalized;
    }

    private static Num requirePositive(Num value, String fieldName) {
        if (!Num.isFinite(value) || !value.isPositive()) {
            throw new IllegalArgumentException(fieldName + " must be finite and > 0");
        }
        return value;
    }
}
