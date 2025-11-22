/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
import org.ta4j.core.num.Num;

import static org.ta4j.core.num.NaN.NaN;

/**
 * Percent Rank indicator.
 *
 * <p>
 * Calculates the percentile rank of the current value within a rolling window
 * of the specified period. The percentile rank is the percentage of values in
 * the window that are less than the current value, expressed as a value between
 * 0 and 100.
 *
 * <p>
 * For example, if the current value is greater than 80% of the values in the
 * window, the percentile rank will be 80.
 *
 * <p>
 * NaN values in the window are ignored when calculating the percentile rank.
 *
 * <p>
 * <strong>Percentage Representation:</strong> This indicator uses percentage
 * scale representation (0-100). The calculation converts a ratio (0-1) to a
 * percentage (0-100) by multiplying by 100. For example, a ratio of 0.8 becomes
 * 80.0 (representing the 80th percentile). This is neither multiplicative total
 * returns nor additive rate of returns representation, but rather a standard
 * percentage scale conversion.
 *
 * <p>
 * TODO: <strong>Future compatibility:</strong> Once
 * {@code ReturnRepresentation} is available (see PR #1376), this indicator may
 * be reviewed to determine if it should support alternative percentage
 * representations or if the current percentage scale representation is
 * appropriate for percentile rank calculations.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/p/percentile-rank.asp">Investopedia:
 *      Percentile Rank</a>
 * @since 0.20
 */
public class PercentRankIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int period;

    /**
     * Constructor.
     *
     * @param indicator the {@link Indicator} to calculate the percentile rank for
     * @param period    the look-back period for the percentile rank calculation
     * @throws IllegalArgumentException if period is less than 1
     * @since 0.20
     */
    public PercentRankIndicator(Indicator<Num> indicator, int period) {
        super(indicator);
        if (period < 1) {
            throw new IllegalArgumentException("Period must be at least 1");
        }
        this.indicator = indicator;
        this.period = period;
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        Num current = indicator.getValue(index);
        if (Num.isNaNOrNull(current)) {
            return NaN;
        }

        int beginIndex = getBarSeries().getBeginIndex();
        // Account for underlying indicator's unstable period to avoid including NaN
        // values
        int adjustedBeginIndex = beginIndex + indicator.getCountOfUnstableBars();
        int startIndex = Math.max(adjustedBeginIndex, index - period);
        int valid = 0;
        int lessThanCount = 0;
        for (int i = startIndex; i < index; i++) {
            Num candidate = indicator.getValue(i);
            if (Num.isNaNOrNull(candidate)) {
                continue;
            }
            valid++;
            if (candidate.isLessThan(current)) {
                lessThanCount++;
            }
        }
        if (valid == 0) {
            return NaN;
        }
        Num ratio = getBarSeries().numFactory()
                .numOf(lessThanCount)
                .dividedBy(getBarSeries().numFactory().numOf(valid));
        return convertRatioToPercentage(ratio);
    }

    /**
     * Converts a ratio (0-1) to a percentage (0-100) using percentage scale
     * representation.
     * <p>
     * This method is structured to allow easy extension when
     * {@code ReturnRepresentation} support is added. The conversion can be switched
     * based on the representation type if needed.
     *
     * @param ratio the ratio value between 0 and 1
     * @return the percentage value between 0 and 100
     */
    protected Num convertRatioToPercentage(Num ratio) {
        // Percentage scale: ratio * 100
        // For example: 0.8 -> 80.0 (80th percentile)
        return ratio.multipliedBy(getBarSeries().numFactory().hundred());
    }

    @Override
    public int getCountOfUnstableBars() {
        return indicator.getCountOfUnstableBars() + period - 1;
    }
}
