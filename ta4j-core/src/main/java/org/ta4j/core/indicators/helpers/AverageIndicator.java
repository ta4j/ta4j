/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorUtils;
import org.ta4j.core.num.Num;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
        super(config.firstIndicator(),
                config.indicators().subList(1, config.indicators().size()).toArray(Indicator<?>[]::new));
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
        if (indicators == null) {
            throw new IllegalArgumentException("At least one indicator must be provided");
        }
        return validatedConfig(Arrays.asList(indicators));
    }

    private static Config validatedConfig(List<Indicator<Num>> indicators) {
        Indicator<Num> firstIndicator = validateAndGetFirst(indicators);
        if (indicators.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("indicator must not be null");
        }
        List<Indicator<Num>> indicatorSnapshot = List.copyOf(indicators);
        for (int i = 1; i < indicatorSnapshot.size(); i++) {
            IndicatorUtils.requireSameSeries(indicatorSnapshot.get(0), indicatorSnapshot.get(i));
        }
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
