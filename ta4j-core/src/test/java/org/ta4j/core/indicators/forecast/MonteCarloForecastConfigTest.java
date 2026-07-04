/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.junit.Test;

public class MonteCarloForecastConfigTest {

    @Test
    public void builderSortsQuantilesAndStoresModes() {
        MonteCarloForecastConfig config = MonteCarloForecastConfig.builder()
                .horizon(3)
                .iterationCount(20)
                .lookbackBarCount(5)
                .seed(7L)
                .shockModel(ShockModel.NORMAL)
                .volatilityUpdateMode(VolatilityUpdateMode.EWMA)
                .volatilityDecayFactor(0.7)
                .quantiles(0.95, 0.05, 0.5)
                .build();

        assertEquals(3, config.horizon());
        assertEquals(20, config.iterationCount());
        assertEquals(5, config.lookbackBarCount());
        assertEquals(7L, config.seed());
        assertEquals(ShockModel.NORMAL, config.shockModel());
        assertEquals(VolatilityUpdateMode.EWMA, config.volatilityUpdateMode());
        assertEquals(0.7, config.volatilityDecayFactor(), 0.0);
        assertEquals(List.of(0.05, 0.5, 0.95), config.quantileProbabilities());
    }

    @Test
    public void validationRejectsInvalidConfig() {
        assertThrows(IllegalArgumentException.class, () -> MonteCarloForecastConfig.builder().horizon(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloForecastConfig.builder().iterationCount(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloForecastConfig.builder().lookbackBarCount(0).build());
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloForecastConfig.builder().quantiles(0.5, 1.1).build());
    }
}
