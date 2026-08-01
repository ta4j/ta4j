/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertThrows;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class MonteCarloPriceForecastSpecTest {

    @Test
    public void rejectsInvalidQuantileProbabilities() {
        assertThrows(IllegalArgumentException.class, () -> spec(List.of()));
        assertThrows(NullPointerException.class, () -> spec(Arrays.asList((Double) null)));
        assertThrows(IllegalArgumentException.class, () -> spec(List.of(Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> spec(List.of(-0.01d)));
        assertThrows(IllegalArgumentException.class, () -> spec(List.of(1.01d)));
    }

    private static MonteCarloPriceForecastSpec spec(List<Double> quantileProbabilities) {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 101, 102).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns);
        return new MonteCarloPriceForecastSpec(close, state, 1, 1, 1, 42L,
                MonteCarloReturnProjectionIndicator.ShockModel.STANDARDIZED_EMPIRICAL,
                MonteCarloReturnProjectionIndicator.VolatilityUpdateMode.CONSTANT, 0.94d, quantileProbabilities);
    }
}
