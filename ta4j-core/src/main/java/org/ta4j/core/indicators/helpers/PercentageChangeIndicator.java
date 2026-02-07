/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Calculates the percentage change between the current and the previous value
 * of an indicator.
 *
 * <p>
 * This indicator uses <strong>rate of return</strong> representation
 * (additive). The formula calculates the percentage change as:
 *
 * <pre>
 * PercentageChange = ((currentValue - previousValue) / previousValue) * 100
 * </pre>
 *
 * <p>
 * This is equivalent to:
 *
 * <pre>
 * PercentageChange = (currentValue / previousValue - 1) * 100
 * </pre>
 *
 * <p>
 * <strong>Representation:</strong> This indicator uses rate of return
 * representation. For example, if a price moves from 100 to 110, this indicator
 * returns 10.0 (representing a 10% gain). This differs from total return
 * representation, which would return 1.10 (representing 110% of the original
 * value).
 *
 * <p>
 * <strong>Optional features:</strong>
 * <ul>
 * <li><strong>Previous indicator:</strong> When {@link #previousIndicator} is
 * specified, the percentage change is calculated between the current value of
 * {@link #indicator} and the previous value of {@link #previousIndicator} (at
 * index - 1). This allows comparing different indicators, such as comparing the
 * current open price with the previous close price.</li>
 * <li><strong>Percentage threshold:</strong> When {@link #percentageThreshold}
 * is greater than zero, the indicator tracks the last time the threshold was
 * exceeded and calculates the percentage change from that point instead of from
 * the previous bar. This is useful for detecting significant moves and
 * measuring cumulative changes since the last significant move.</li>
 * </ul>
 *
 * <p>
 * TODO: <strong>Future compatibility:</strong> Once
 * {@code ReturnRepresentation} is available (see PR #1376), this indicator may
 * be extended to support both representations via an optional constructor
 * parameter.
 *
 * @since 0.20
 */
public class PercentageChangeIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final Indicator<Num> previousIndicator;
    private final Num percentageThreshold;
    private Num lastNotification;

    /**
     * Constructor to calculate percentage change from the previous value.
     *
     * @param indicator the indicator to calculate the percentage change for
     * @since 0.20
     */
    public PercentageChangeIndicator(Indicator<Num> indicator) {
        this(indicator, null, indicator.getBarSeries().numFactory().zero());
    }

    /**
     * Constructor with percentage threshold.
     * <p>
     * When {@code percentageThreshold} is greater than zero, the indicator tracks
     * the last time the threshold was exceeded and calculates the percentage change
     * from that point.
     *
     * @param indicator           the indicator to calculate the percentage change
     *                            for
     * @param percentageThreshold the threshold percentage. If greater than zero,
     *                            tracks changes from the last time this threshold
     *                            was exceeded. If zero, calculates from the
     *                            previous bar.
     * @since 0.20
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Num percentageThreshold) {
        this(indicator, null, percentageThreshold);
    }

    /**
     * Constructor with percentage threshold (Number overload).
     *
     * @param indicator           the indicator to calculate the percentage change
     *                            for
     * @param percentageThreshold the threshold percentage. If greater than zero,
     *                            tracks changes from the last time this threshold
     *                            was exceeded. If zero, calculates from the
     *                            previous bar.
     * @since 0.20
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Number percentageThreshold) {
        this(indicator, null, indicator.getBarSeries().numFactory().numOf(percentageThreshold));
    }

    /**
     * Constructor with optional previous indicator.
     * <p>
     * Calculates the percentage change between the current value of
     * {@code indicator} and the previous value of {@code previousIndicator} (at
     * index - 1).
     *
     * @param indicator         the indicator to calculate the percentage change for
     * @param previousIndicator the indicator to use for the previous value (at
     *                          index - 1). If {@code null}, uses the previous value
     *                          of {@code indicator}.
     * @since 0.20
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Indicator<Num> previousIndicator) {
        this(indicator, previousIndicator, indicator.getBarSeries().numFactory().zero());
    }

    /**
     * Constructor with optional previous indicator and percentage threshold.
     * <p>
     * When {@code percentageThreshold} is greater than zero, the indicator tracks
     * the last time the threshold was exceeded and calculates the percentage change
     * from that point.
     *
     * @param indicator           the indicator to calculate the percentage change
     *                            for
     * @param previousIndicator   the indicator to use for the previous value (at
     *                            index - 1). If {@code null}, uses the previous
     *                            value of {@code indicator}.
     * @param percentageThreshold the threshold percentage. If greater than zero,
     *                            tracks changes from the last time this threshold
     *                            was exceeded. If zero, calculates from the
     *                            previous bar.
     * @since 0.20
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Indicator<Num> previousIndicator,
            Num percentageThreshold) {
        super(indicator);
        this.indicator = indicator;
        this.previousIndicator = previousIndicator;
        this.percentageThreshold = percentageThreshold;
    }

    /**
     * Constructor with optional previous indicator and percentage threshold (Number
     * overload).
     *
     * @param indicator           the indicator to calculate the percentage change
     *                            for
     * @param previousIndicator   the indicator to use for the previous value (at
     *                            index - 1). If {@code null}, uses the previous
     *                            value of {@code indicator}.
     * @param percentageThreshold the threshold percentage. If greater than zero,
     *                            tracks changes from the last time this threshold
     *                            was exceeded. If zero, calculates from the
     *                            previous bar.
     * @since 0.20
     */
    public PercentageChangeIndicator(Indicator<Num> indicator, Indicator<Num> previousIndicator,
            Number percentageThreshold) {
        this(indicator, previousIndicator, indicator.getBarSeries().numFactory().numOf(percentageThreshold));
    }

    /**
     * Calculates the percentage change between the current value and the previous
     * value of the indicator using rate of return representation.
     *
     * @param index the index of the current bar
     * @return the percentage change between the current and previous values (rate
     *         of return representation), or NaN if index is within the unstable bar
     *         period or if previous value is zero
     */
    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (beginIndex > index) {
            return NaN;
        }

        // Return NaN during unstable period
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }

        Num currentValue = indicator.getValue(index);
        if (Num.isNaNOrNull(currentValue) || currentValue.isZero()) {
            return NaN;
        }

        // If threshold tracking is enabled, calculate all previous values to get
        // the correct last notification value for this index
        if (!percentageThreshold.isZero()) {
            for (int i = beginIndex; i < index; i++) {
                setLastNotification(i);
            }

            if (lastNotification == null) {
                return NaN;
            }

            Num changeFraction = currentValue.dividedBy(lastNotification);
            return fractionToPercentage(changeFraction);
        }

        // Simple case: compare with previous bar

        // Determine which indicator to use for previous value
        Indicator<Num> sourceForPrevious = (previousIndicator != null) ? previousIndicator : indicator;
        Num previousValue = sourceForPrevious.getValue(index - 1);

        // Check for NaN or zero previous value (division by zero)
        if (Num.isNaNOrNull(previousValue) || previousValue.isZero()) {
            return NaN;
        }

        return calculateRateOfReturnPercentage(currentValue, previousValue);
    }

    /**
     * Updates the last notification value when threshold tracking is enabled. This
     * method is called during calculation to track when the threshold was last
     * exceeded.
     *
     * @param index the index to check
     */
    private void setLastNotification(int index) {
        Num value = indicator.getValue(index);
        if (Num.isNaNOrNull(value) || value.isZero()) {
            return;
        }
        if (lastNotification == null) {
            lastNotification = value;
            return;
        }

        Num changeFraction = value.dividedBy(lastNotification);
        Num changePercentage = fractionToPercentage(changeFraction);

        if (changePercentage.abs().isGreaterThanOrEqual(percentageThreshold)) {
            lastNotification = value;
        }
    }

    /**
     * Converts a change fraction to a percentage change.
     *
     * @param changeFraction the fraction representing the change (e.g., 1.1 for 10%
     *                       increase)
     * @return the percentage change (e.g., 10.0 for 10% increase)
     */
    private Num fractionToPercentage(Num changeFraction) {
        final var hundred = getBarSeries().numFactory().hundred();
        return changeFraction.multipliedBy(hundred).minus(hundred);
    }

    /**
     * Calculates the rate of return as a percentage.
     * <p>
     * This method is structured to allow easy extension when
     * {@code ReturnRepresentation} support is added. The calculation can be
     * switched based on the representation type.
     *
     * @param currentValue  the current indicator value
     * @param previousValue the previous indicator value
     * @return the rate of return as a percentage (e.g., 10.0 for a 10% gain)
     */
    protected Num calculateRateOfReturnPercentage(Num currentValue, Num previousValue) {
        // Rate of return: ((current - previous) / previous) * 100
        // Equivalent to: (current / previous - 1) * 100
        Num change = currentValue.minus(previousValue);
        Num changeFraction = change.dividedBy(previousValue);
        return changeFraction.multipliedBy(getBarSeries().numFactory().hundred());
    }

    /** @return {@code 1} */
    @Override
    public int getCountOfUnstableBars() {
        int indicatorUnstableBars = indicator.getCountOfUnstableBars();
        int previousUnstableBars = previousIndicator == null ? indicatorUnstableBars
                : previousIndicator.getCountOfUnstableBars();
        return Math.max(indicatorUnstableBars, previousUnstableBars + 1);
    }
}
