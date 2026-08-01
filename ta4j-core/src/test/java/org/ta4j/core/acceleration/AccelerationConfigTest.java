/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AccelerationConfigTest {

    @Test
    void parsesLaunchModes() {
        assertEquals(AccelerationMode.OFF, AccelerationMode.parse(null));
        assertEquals(AccelerationMode.OFF, AccelerationMode.parse("off"));
        assertEquals(AccelerationMode.CPU, AccelerationMode.parse("cpu"));
        assertEquals(AccelerationMode.AUTO, AccelerationMode.parse("auto"));
        assertEquals(AccelerationMode.METAL, AccelerationMode.parse("metal"));
        assertEquals(AccelerationMode.CUDA, AccelerationMode.parse("cuda"));
        assertEquals(AccelerationMode.HYBRID, AccelerationMode.parse("hybrid"));
    }

    @Test
    void rejectsInvalidLaunchMode() {
        assertThrows(IllegalArgumentException.class, () -> AccelerationMode.parse("mlx"));
    }

    @Test
    void validatesMinimumSpeedup() {
        assertThrows(IllegalArgumentException.class,
                () -> new AccelerationConfig(AccelerationMode.AUTO, false, -0.01d));
        assertThrows(IllegalArgumentException.class, () -> new AccelerationConfig(AccelerationMode.AUTO, false, 1.0d));
    }

    @Test
    void rejectsMalformedModeProperty() {
        IllegalArgumentException exception = parseWithProperty(AccelerationConfig.MODE_PROPERTY, "mlx");

        assertEquals("Invalid value for system property " + AccelerationConfig.MODE_PROPERTY + ": mlx",
                exception.getMessage());
    }

    @Test
    void rejectsMalformedMinimumSpeedupProperty() {
        IllegalArgumentException exception = parseWithProperty(AccelerationConfig.MINIMUM_SPEEDUP_PROPERTY, "fast");

        assertEquals("Invalid value for system property " + AccelerationConfig.MINIMUM_SPEEDUP_PROPERTY + ": fast",
                exception.getMessage());
    }

    private static IllegalArgumentException parseWithProperty(String propertyName, String value) {
        String previous = System.getProperty(propertyName);
        System.setProperty(propertyName, value);
        try {
            return assertThrows(IllegalArgumentException.class, AccelerationConfig::fromSystemProperties);
        } finally {
            if (previous == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previous);
            }
        }
    }
}
