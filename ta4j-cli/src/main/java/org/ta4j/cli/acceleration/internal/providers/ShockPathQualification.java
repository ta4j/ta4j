/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.Map;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;

/**
 * Predicted-total-time qualification for the versioned Monte Carlo shock-path
 * kernel. Each row is keyed by operation version, backend, device family, and
 * resident state, and predicts the full offload cost (context and library load
 * when cold, staging, launch, kernel, synchronization, return, and core-side
 * decode) so the runtime can rank providers against its scalar baseline.
 *
 * <p>
 * Coefficient rows are carried from the pre-ABI crossover models (a fixed 25%
 * total-time advantage above a minimum step count on qualified families) and
 * restated in total-time form. Rows without hardware qualification stay absent
 * and predict an unbounded cost, which routes the request to scalar with a
 * {@code CPU_FASTER} diagnostic instead of engaging an unqualified device.
 *
 * @since 0.24.2
 */
final class ShockPathQualification {

    /** Workload steps below which no offload can amortize launch costs. */
    static final String MIN_STEPS_PROPERTY_SUFFIX = ".minSteps";

    /** Device family override, for example {@code m5max}. */
    static final String FAMILY_PROPERTY_SUFFIX = ".family";

    private record Coefficients(long coldBaseNanos, long warmBaseNanos, double nanosPerStep, double nanosPerByte,
            long minimumSteps) {
    }

    private static final Map<String, Coefficients> ROWS = Map.of(
            // Metal on Apple M5 Max: the pre-ABI model engaged above 2^24
            // path steps with a 25% total-time advantage over scalar.
            key(1, Backend.METAL, "m5max"), new Coefficients(500_000_000L, 200_000L, 37.5d, 0.1d, 16_777_216L));

    private ShockPathQualification() {
    }

    /**
     * Predicts the total offload cost for a request, or {@link Long#MAX_VALUE} when
     * the backend has no qualified row for the operation version and device family,
     * or when the workload is below the crossover floor.
     */
    static long predictedTotalNanos(Backend backend, int operationVersion, String family, long steps, long stagedBytes,
            boolean resident) {
        Coefficients row = ROWS.get(key(operationVersion, backend, family));
        if (row == null || steps < minimumSteps(backend, operationVersion, family)) {
            return Long.MAX_VALUE;
        }
        double predicted = (resident ? row.warmBaseNanos() : row.coldBaseNanos()) + steps * row.nanosPerStep()
                + stagedBytes * row.nanosPerByte();
        if (!Double.isFinite(predicted) || predicted >= (double) Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, (long) predicted);
    }

    static String familyProperty(Backend backend) {
        return "ta4j.acceleration." + backend.name().toLowerCase(java.util.Locale.ROOT) + FAMILY_PROPERTY_SUFFIX;
    }

    static String minStepsProperty(Backend backend) {
        return "ta4j.acceleration." + backend.name().toLowerCase(java.util.Locale.ROOT) + MIN_STEPS_PROPERTY_SUFFIX;
    }

    /**
     * Returns the configured minimum step count, defaulting to the qualified row's
     * floor. Tests and operators use this to move crossover without touching native
     * code.
     */
    static long minimumSteps(Backend backend, int operationVersion, String family) {
        Coefficients row = ROWS.get(key(operationVersion, backend, family));
        long fallback = row == null ? Long.MAX_VALUE : row.minimumSteps();
        long configured = Long.getLong(minStepsProperty(backend), fallback);
        return configured < 0L ? fallback : configured;
    }

    private static String key(int operationVersion, Backend backend, String family) {
        return operationVersion + "/" + backend.name() + "/" + family;
    }
}
