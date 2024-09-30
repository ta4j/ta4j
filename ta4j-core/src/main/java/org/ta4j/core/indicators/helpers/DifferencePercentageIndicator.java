/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
 */
public class DifferencePercentageIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final Num percentageThreshold;
    private Num lastNotification;

    /**
     * Constructor to get the percentage difference from the previous value.
     *
     * @param indicator the {@link Indicator}
     */
    public DifferencePercentageIndicator(Indicator<Num> indicator) {
        this(indicator, indicator.getBarSeries().numFactory().zero());
    }

    /**
     * Constructor.
     *
     * @param indicator           the {@link Indicator}
     * @param percentageThreshold the threshold percentage
     */
    public DifferencePercentageIndicator(Indicator<Num> indicator, Number percentageThreshold) {
        this(indicator, indicator.getBarSeries().numFactory().numOf(percentageThreshold));
    }

    /**
     * Constructor.
     *
     * @param indicator           the {@link Indicator}
     * @param percentageThreshold the threshold percentage
     */
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
    public int getUnstableBars() {
        return 1;
    }

    private Num fractionToPercentage(Num changeFraction) {
        final var hundred = getBarSeries().numFactory().hundred();
        return changeFraction.multipliedBy(hundred).minus(hundred);
    }
}
