/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Calculates the sum of all indicator values.
 *
 * <pre>
 * Sum = summand0 + summand1 + ... + summandN
 * </pre>
 */
public class SumIndicator extends CachedIndicator<Num> {

    private final Indicator<Num>[] summands;

    /**
     * Constructor.
     *
     * @param summands the indicators ​​to be summed
     */
    @SafeVarargs
    public SumIndicator(Indicator<Num>... summands) {
        // TODO: check if first series is equal to the other ones
        super(summands[0]);
        this.summands = summands;
    }

    @Override
    protected Num calculate(int index) {
        Num sum = getBarSeries().numFactory().zero();
        for (Indicator<Num> summand : summands) {
            sum = sum.plus(summand.getValue(index));
        }
        return sum;
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }
}
