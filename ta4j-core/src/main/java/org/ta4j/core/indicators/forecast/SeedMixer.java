/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast;

final class SeedMixer {

    private SeedMixer() {
    }

    static long mix(long seed, int index, int horizon) {
        long mixed = seed;
        mixed ^= 0x9E3779B97F4A7C15L + ((long) index << 6) + ((long) index >> 2);
        mixed ^= 0xBF58476D1CE4E5B9L + ((long) horizon << 7) + ((long) horizon >> 3);
        return mix64(mixed);
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94D049BB133111EBL;
        return mixed ^ (mixed >>> 31);
    }
}
