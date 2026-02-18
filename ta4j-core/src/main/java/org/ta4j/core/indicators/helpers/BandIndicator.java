/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Generic upper or lower band indicator computed around a middle value.
 *
 * <p>
 * The band is calculated as:
 * <ul>
 * <li>{@code UPPER}: {@code middle + multiplier * width}</li>
 * <li>{@code LOWER}: {@code middle - multiplier * width}</li>
 * </ul>
 *
 * <p>
 * This is useful for constructing indicator envelopes such as Bollinger-style,
 * VWAP, or other custom bands when the middle and width indicators are supplied
 * explicitly.
 *
 * @since 0.22.3
 */
public class BandIndicator extends CachedIndicator<Num> {

    /**
     * Band direction relative to the middle.
     *
     * @since 0.22.3
     */
    public enum BandType {
        UPPER, LOWER
    }

    private final Indicator<Num> middleIndicator;
    private final Indicator<Num> widthIndicator;
    private final Num multiplier;
    private final BandType bandType;

    /**
     * Constructor.
     *
     * @param middleIndicator the middle indicator (for example a moving average)
     * @param widthIndicator  the width indicator (for example a standard deviation)
     * @param multiplier      scaling factor applied to the width
     * @param bandType        whether to compute the upper or lower band
     * @since 0.22.3
     */
    public BandIndicator(Indicator<Num> middleIndicator, Indicator<Num> widthIndicator, Number multiplier,
            BandType bandType) {
        super(requireSameSeries(middleIndicator, widthIndicator));
        this.middleIndicator = Objects.requireNonNull(middleIndicator, "middleIndicator must not be null");
        this.widthIndicator = Objects.requireNonNull(widthIndicator, "widthIndicator must not be null");
        this.bandType = Objects.requireNonNull(bandType, "bandType must not be null");
        this.multiplier = getBarSeries().numFactory()
                .numOf(Objects.requireNonNull(multiplier, "multiplier must not be null"));
        if (Num.isNaNOrNull(this.multiplier)) {
            throw new IllegalArgumentException("multiplier must be a valid number");
        }
    }

    /**
     * Calculates the indicator value at the requested index.
     */
    @Override
    protected Num calculate(int index) {
        if (index < getBarSeries().getBeginIndex() + getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        Num middle = middleIndicator.getValue(index);
        Num width = widthIndicator.getValue(index);
        if (Num.isNaNOrNull(middle) || Num.isNaNOrNull(width)) {
            return NaN.NaN;
        }
        Num offset = width.multipliedBy(multiplier);
        return bandType == BandType.UPPER ? middle.plus(offset) : middle.minus(offset);
    }

    /**
     * Returns the number of unstable bars required before values become reliable.
     */
    @Override
    public int getCountOfUnstableBars() {
        return Math.max(middleIndicator.getCountOfUnstableBars(), widthIndicator.getCountOfUnstableBars());
    }

    /**
     * Returns a string representation of this component.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + " bandType: " + bandType;
    }

    /**
     * Validates and returns same series.
     */
    private static BarSeries requireSameSeries(Indicator<?> middleIndicator, Indicator<?> widthIndicator) {
        Objects.requireNonNull(middleIndicator, "middleIndicator must not be null");
        Objects.requireNonNull(widthIndicator, "widthIndicator must not be null");
        BarSeries series = Objects.requireNonNull(middleIndicator.getBarSeries(),
                "middleIndicator must reference a bar series");
        if (!Objects.equals(series, widthIndicator.getBarSeries())) {
            throw new IllegalArgumentException("Indicators must share the same bar series");
        }
        return series;
    }
}
