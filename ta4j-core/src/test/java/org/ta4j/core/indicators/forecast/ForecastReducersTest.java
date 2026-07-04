/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ForecastReducersTest extends AbstractIndicatorTest<ForecastReducer, Num> {

    public ForecastReducersTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void reducesDefinedDistributionSummaries() {
        ForecastDistribution<Num> distribution = ForecastDistribution.ofSamples(2, 1, List.of(numOf(1), numOf(3)),
                List.of(0.5));

        assertNumEquals(2, ForecastReducers.mean().reduce(distribution));
        assertNumEquals(2, ForecastReducers.median().reduce(distribution));
        assertNumEquals(1, ForecastReducers.standardDeviation().reduce(distribution));
        assertNumEquals(2, ForecastReducers.quantile(0.5).reduce(distribution));
    }

    @Test
    public void returnsNaNForUndefinedOrMissingQuantiles() {
        ForecastDistribution<Num> undefined = ForecastDistribution.undefined(1, 1);
        ForecastDistribution<Num> distribution = ForecastDistribution.ofSamples(2, 1, List.of(numOf(1), numOf(3)),
                List.of(0.5));

        assertTrue(ForecastReducers.mean().reduce(undefined).isNaN());
        assertTrue(ForecastReducers.quantile(0.95).reduce(distribution).isNaN());
    }
}
