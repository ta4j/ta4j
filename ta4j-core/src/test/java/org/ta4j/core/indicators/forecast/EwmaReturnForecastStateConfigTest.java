/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class EwmaReturnForecastStateConfigTest {

    @Test
    public void builderCreatesConfig() {
        EwmaReturnForecastStateConfig config = EwmaReturnForecastStateConfig.builder()
                .initializationBarCount(12)
                .decayFactor(0.8)
                .driftMode(DriftMode.ROLLING_MEAN)
                .build();

        assertEquals(12, config.initializationBarCount());
        assertEquals(0.8, config.decayFactor(), 0.0);
        assertEquals(DriftMode.ROLLING_MEAN, config.driftMode());
    }

    @Test
    public void validationRejectsInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> new EwmaReturnForecastStateConfig(0, 0.5, DriftMode.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new EwmaReturnForecastStateConfig(2, 1.0, DriftMode.ZERO));
        assertThrows(NullPointerException.class, () -> new EwmaReturnForecastStateConfig(2, 0.5, null));
    }
}
