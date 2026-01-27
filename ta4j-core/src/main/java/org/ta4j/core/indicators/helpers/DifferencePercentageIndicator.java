/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Difference Percentage Indicator.
 *
 * <p>
 * Returns the percentage difference from the last time the
 * {@link #percentageThreshold threshold} was reached. If the threshold is
 * {@code 0} or not specified, only the percentage difference from the previous
 * value is returned.
 *
 * @deprecated Use {@link PercentageChangeIndicator} instead. This class has
 *             been consolidated into {@code PercentageChangeIndicator} which
 *             provides the same functionality with additional features.
 *             <p>
 *             Migration examples:
 *             <ul>
 *             <li>{@code new DifferencePercentageIndicator(indicator)} →
 *             {@code new PercentageChangeIndicator(indicator)}</li>
 *             <li>{@code new DifferencePercentageIndicator(indicator, threshold)}
 *             → {@code new PercentageChangeIndicator(indicator, null,
 *             threshold)}</li>
 *             <li>{@code new DifferencePercentageIndicator(indicator,
 *             previousIndicator)} → {@code new
 *             PercentageChangeIndicator(indicator, previousIndicator)}</li>
 *             <li>{@code new DifferencePercentageIndicator(indicator,
 *             previousIndicator, threshold)} → {@code new
 *             PercentageChangeIndicator(indicator, previousIndicator,
 *             threshold)}</li>
 *             </ul>
 *
 * @since 0.18
 */
@Deprecated
public class DifferencePercentageIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final Num percentageThreshold;
    private Num lastNotification;

    /**
     * Constructor to get the percentage difference from the previous value.
     *
     * @param indicator the {@link Indicator}
     */
    @Deprecated
    public DifferencePercentageIndicator(Indicator<Num> indicator) {
        this(indicator, indicator.getBarSeries().numFactory().zero());
    }

    /**
     * Constructor.
     *
     * @param indicator           the {@link Indicator}
     * @param percentageThreshold the threshold percentage
     */
    @Deprecated
    public DifferencePercentageIndicator(Indicator<Num> indicator, Number percentageThreshold) {
        this(indicator, indicator.getBarSeries().numFactory().numOf(percentageThreshold));
    }

    /**
     * Constructor.
     *
     * @param indicator           the {@link Indicator}
     * @param percentageThreshold the threshold percentage
     */
    @Deprecated
    public DifferencePercentageIndicator(Indicator<Num> indicator, Num percentageThreshold) {
        super(indicator);
        this.indicator = indicator;
        this.percentageThreshold = percentageThreshold;
    }

    @Override
    protected Num calculate(int index) {
        int beginIndex = getBarSeries().getBeginIndex();
        if (beginIndex > index) {
            return NaN.NaN;
        }

        Num value = indicator.getValue(index);
        if (value.isNaN() || value.isZero()) {
            return NaN.NaN;
        }

        // calculate all the previous values to get the correct
        // last notification value for this index
        for (int i = getBarSeries().getBeginIndex(); i < index; i++) {
            setLastNotification(i);
        }

        if (lastNotification == null) {
            return NaN.NaN;
        }

        Num changeFraction = value.dividedBy(lastNotification);
        return fractionToPercentage(changeFraction);
    }

    public void setLastNotification(int index) {
        Num value = indicator.getValue((index));
        if (value.isNaN() || value.isZero()) {
            return;
        }
        if (lastNotification == null) {
            lastNotification = value;
        }

        Num changeFraction = value.dividedBy(lastNotification);
        Num changePercentage = fractionToPercentage(changeFraction);

        if (changePercentage.abs().isGreaterThanOrEqual(percentageThreshold)) {
            lastNotification = value;
        }
    }

    @Override
    @Deprecated
    public int getCountOfUnstableBars() {
        return 1;
    }

    private Num fractionToPercentage(Num changeFraction) {
        final var hundred = getBarSeries().numFactory().hundred();
        return changeFraction.multipliedBy(hundred).minus(hundred);
    }
}
