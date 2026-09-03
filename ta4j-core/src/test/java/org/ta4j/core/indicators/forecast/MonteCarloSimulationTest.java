/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.acceleration.AccelerationRuntime;

public class MonteCarloSimulationTest {

    @Test
    public void boundedSelectionMatchesVersionOneGoldenVectors() {
        assertVector(1, 0, 0, 0, 0, 0, 0, 0, 0);
        assertVector(2, 0, 1, 0, 0, 1, 0, 0, 1);
        assertVector(7, 2, 3, 2, 5, 1, 5, 1, 5);
        assertVector(252, 170, 241, 114, 96, 57, 152, 162, 47);
        assertVector(256, 158, 13, 142, 100, 217, 36, 98, 167);
        assertVector(1_000, 990, 181, 814, 188, 65, 908, 682, 759);
    }

    @Test
    public void gaussianSelectionMatchesVersionOneGoldenVector() {
        MonteCarloSimulation.DeterministicRandom random = stream();

        double[] actual = new double[6];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = random.nextGaussian();
        }

        assertArrayEquals(new double[] { -1.3318445490451813, 0.5448539398879264, -0.5868281460745287,
                0.2513242949628345, -0.6323990089329744, -1.3505535293708895 }, actual, 0d);
    }

    @Test
    public void pathCoordinatesAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloSimulation.DeterministicRandom.forPath(1L, -1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloSimulation.DeterministicRandom.forPath(1L, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MonteCarloSimulation.DeterministicRandom.forPath(1L, 0, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> stream().nextInt(0));
        assertEquals(1, AccelerationRuntime.Operation.MONTE_CARLO_SHOCK_PATHS_V1.version());
    }

    private static void assertVector(int bound, int... expected) {
        MonteCarloSimulation.DeterministicRandom random = stream();
        int[] actual = new int[expected.length];
        for (int i = 0; i < actual.length; i++) {
            actual[i] = random.nextInt(bound);
        }
        assertArrayEquals(expected, actual);
    }

    private static MonteCarloSimulation.DeterministicRandom stream() {
        return MonteCarloSimulation.DeterministicRandom.forPath(42L, 317, 12, 5);
    }
}
