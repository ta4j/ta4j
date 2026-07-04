/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ForecastDistributionTest extends AbstractIndicatorTest<Object, Object> {

    public ForecastDistributionTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void summarizesSamplesAndDefaultQuantiles() {
        ForecastDistribution<Num> distribution = ForecastDistribution.ofSamples(4, 2,
                List.of(numOf(1), numOf(2), numOf(3), numOf(4)));

        assertTrue(distribution.defined());
        assertEquals(4, distribution.index());
        assertEquals(2, distribution.horizon());
        assertEquals(4, distribution.sampleCount());
        assertNumEquals(2.5, distribution.mean());
        assertNumEquals(2.5, distribution.median());
        assertNumEquals(Math.sqrt(1.25), distribution.standardDeviation());
        assertNumEquals(1.15, distribution.quantile(0.05));
        assertNumEquals(3.85, distribution.quantile(0.95));
    }

    @Test
    public void filtersInvalidSamplesAndKeepsQuantilesImmutable() {
        ForecastDistribution<Num> distribution = ForecastDistribution.ofSamples(2, 1,
                List.of(numOf(1), org.ta4j.core.num.NaN.NaN, numOf(3)), List.of(0.5));

        assertEquals(2, distribution.sampleCount());
        assertNumEquals(2, distribution.quantile(0.5));
        assertThrows(UnsupportedOperationException.class, () -> distribution.quantiles().put(0.9, numOf(3)));
    }

    @Test
    public void returnsUndefinedWhenNoValidSamplesExist() {
        ForecastDistribution<Num> distribution = ForecastDistribution.ofSamples(1, 1,
                List.of(org.ta4j.core.num.NaN.NaN));

        assertFalse(distribution.defined());
        assertEquals(0, distribution.sampleCount());
        assertTrue(distribution.mean().isNaN());
        assertTrue(distribution.quantiles().isEmpty());
    }

    @Test
    public void mapsSummaryValuesAndQuantiles() {
        ForecastDistribution<Num> distribution = ForecastDistribution.ofSamples(2, 1, List.of(numOf(1), numOf(3)),
                List.of(0.5));

        ForecastDistribution<Num> doubled = distribution.map(value -> value.multipliedBy(numOf(2)));

        assertTrue(doubled.defined());
        assertEquals(distribution.index(), doubled.index());
        assertEquals(distribution.horizon(), doubled.horizon());
        assertNumEquals(4, doubled.mean());
        assertNumEquals(4, doubled.median());
        assertNumEquals(4, doubled.quantile(0.5));
    }

    @Test
    public void validatesDistributionShape() {
        assertThrows(IllegalArgumentException.class, () -> ForecastDistribution.undefined(0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ForecastDistribution<>(0, 1, 0, true, numOf(0), numOf(0), numOf(0), java.util.Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> ForecastDistribution.ofSamples(0, 1, List.of(numOf(1)), List.of(-0.1)));
    }
}
