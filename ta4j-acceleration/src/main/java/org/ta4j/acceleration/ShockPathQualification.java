/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.acceleration;

import java.util.Map;

import org.ta4j.core.acceleration.AccelerationRuntime.Backend;

/**
 * Predicted-total-time qualification for the versioned Monte Carlo shock-path
 * kernel. Each row is keyed by operation version, backend, and device family,
 * and predicts the full offload cost (context and library load when cold,
 * staging, launch, kernel, synchronization, return, and core-side decode) so
 * the runtime can compare providers against its scalar baseline.
 *
 * <p>
 * A row may only be added with recorded benchmark evidence: the workload shapes
 * (decisions, paths, horizon, lookback), cold and warm total timings of the
 * native lane and of the scalar lane on the same host, and the source revision
 * measured. Rows without that evidence stay absent and predict an unbounded
 * cost, which keeps the request scalar with a {@code NOT_QUALIFIED} diagnostic
 * instead of engaging an unqualified device. No shipped row is qualified yet,
 * so automatic selection currently keeps every workload scalar.
 *
 * @since 0.29.1
 */
final class ShockPathQualification {

    /** Qualification with no measured rows: every workload stays scalar. */
    static final ShockPathQualification QUALIFIED = new ShockPathQualification(Map.of());

    /**
     * Measured cost coefficients for one qualified region.
     *
     * @param coldBaseNanos fixed cost of a cold (first) execution
     * @param warmBaseNanos fixed cost of a resident execution
     * @param nanosPerStep  marginal cost per simulated path step
     * @param nanosPerByte  marginal cost per staged byte
     * @param minimumSteps  smallest qualified workload in path steps
     */
    record Coefficients(long coldBaseNanos, long warmBaseNanos, double nanosPerStep, double nanosPerByte,
            long minimumSteps) {
    }

    private final Map<String, Coefficients> rows;

    private ShockPathQualification(Map<String, Coefficients> rows) {
        this.rows = Map.copyOf(rows);
    }

    /**
     * Returns a qualification holding exactly one row; used to exercise routing
     * against measured coefficients.
     */
    static ShockPathQualification of(Backend backend, int operationVersion, String family, Coefficients coefficients) {
        return new ShockPathQualification(Map.of(key(operationVersion, backend, family), coefficients));
    }

    /**
     * Predicts the total offload cost for a request, or {@link Long#MAX_VALUE} when
     * the backend has no qualified row for the operation version and device family,
     * or when the workload is below the qualified floor.
     */
    long predictedTotalNanos(Backend backend, int operationVersion, String family, long steps, long stagedBytes,
            boolean resident) {
        Coefficients row = rows.get(key(operationVersion, backend, family));
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

    /**
     * Returns the qualified row's minimum step count, or {@link Long#MAX_VALUE}
     * when no row is qualified.
     */
    long minimumSteps(Backend backend, int operationVersion, String family) {
        Coefficients row = rows.get(key(operationVersion, backend, family));
        return row == null ? Long.MAX_VALUE : row.minimumSteps();
    }

    private static String key(int operationVersion, Backend backend, String family) {
        return operationVersion + "/" + backend.name() + "/" + family;
    }
}
