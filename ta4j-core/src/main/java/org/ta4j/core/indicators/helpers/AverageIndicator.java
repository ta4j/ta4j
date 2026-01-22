/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import java.util.Arrays;
import java.util.List;

/**
 * Indicator to calculate the average of a list of other indicators.
 */
public class AverageIndicator extends CachedIndicator<Num> {
    private final List<Indicator<Num>> indicators;
    private final int unstableBars;

    @SafeVarargs
    public AverageIndicator(Indicator<Num>... indicators) {
        this(Arrays.asList(indicators));
    }

    public AverageIndicator(List<Indicator<Num>> indicators) {
        super(validateAndGetFirst(indicators));

        this.indicators = indicators;
        this.unstableBars = indicators.stream().mapToInt(Indicator::getCountOfUnstableBars).max().orElse(0);
    }

    /**
     * Validates that the given list of indicators is not null or empty and returns
     * the first indicator.
     *
     * @param indicators the list of indicators to validate
     * @return the first indicator in the list
     * @throws IllegalArgumentException if the list is null or empty
     */
    private static Indicator<Num> validateAndGetFirst(List<Indicator<Num>> indicators) {
        if (indicators == null || indicators.isEmpty()) {
            throw new IllegalArgumentException("At least one indicator must be provided");
        }
        return indicators.getFirst();
    }

    @Override
    protected Num calculate(int index) {
        Num value = getBarSeries().numFactory().zero();

        for (Indicator<Num> indicator : indicators) {
            value = value.plus(indicator.getValue(index));
        }

        return value.dividedBy(getBarSeries().numFactory().numOf(indicators.size()));
    }

    @Override
    public int getCountOfUnstableBars() {
        return unstableBars;
    }
}
