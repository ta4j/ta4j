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
}
