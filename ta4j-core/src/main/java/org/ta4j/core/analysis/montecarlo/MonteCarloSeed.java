/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

/**
 * Provides the deterministic seed derivation shared by Monte Carlo forecast
 * engines and reproducibility examples.
 *
 * @since 0.25.1
 */
public final class MonteCarloSeed {

    private MonteCarloSeed() {
    }

    /**
     * Mixes a simulation seed with its decision index and forecast horizon.
     *
     * @param seed    base simulation seed
     * @param index   decision bar index
     * @param horizon forecast horizon in bars
     * @return the deterministic mixed seed
     * @since 0.25.1
     */
    public static long mix(long seed, int index, int horizon) {
        long value = seed;
        value ^= 0x9E3779B97F4A7C15L + ((long) index << 32) + index;
        value = Long.rotateLeft(value, 27) * 0x3C79AC492BA7B653L;
        value ^= 0x1C69B3F74AC4AE35L + horizon;
        value = Long.rotateLeft(value, 31) * 0x1C69B3F74AC4AE35L;
        return value ^ value >>> 33;
    }
}
