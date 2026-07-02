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
        this(validatedConfig(indicators));
    }

    public AverageIndicator(List<Indicator<Num>> indicators) {
        this(validatedConfig(indicators));
    }

    private AverageIndicator(Config config) {
        super(config.firstIndicator());
        this.indicators = config.indicators();
        this.unstableBars = config.unstableBars();
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

    private static Config validatedConfig(Indicator<Num>[] indicators) {
        return validatedConfig(Arrays.asList(indicators));
    }

    private static Config validatedConfig(List<Indicator<Num>> indicators) {
        Indicator<Num> firstIndicator = validateAndGetFirst(indicators);
        List<Indicator<Num>> indicatorSnapshot = List.copyOf(indicators);
        int unstableBars = indicatorSnapshot.stream().mapToInt(Indicator::getCountOfUnstableBars).max().orElse(0);
        return new Config(firstIndicator, indicatorSnapshot, unstableBars);
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

    private record Config(Indicator<Num> firstIndicator, List<Indicator<Num>> indicators, int unstableBars) {
    }
}
