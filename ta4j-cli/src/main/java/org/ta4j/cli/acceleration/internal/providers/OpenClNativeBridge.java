/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.Arrays;

interface OpenClNativeBridge {

    int ABI_VERSION = 1;

    OpenClProbeResult probe();

    OpenClEvaluationResult evaluate(NativeForecastRequest request);
}

record OpenClProbeResult(boolean available, String deviceName, int computeMajor, int computeMinor, long freeMemoryBytes,
        long totalMemoryBytes, int driverVersion, int runtimeVersion, int momentThreads, boolean gpuDevice,
        String detail) {
}

record OpenClEvaluationResult(double totalMicros, double transferMicros, double kernelMicros, double reductionMicros,
        double[] rows) {

    OpenClEvaluationResult {
        rows = Arrays.copyOf(rows, rows.length);
    }

    @Override
    public double[] rows() {
        return Arrays.copyOf(rows, rows.length);
    }
}

final class JniOpenClNativeBridge implements OpenClNativeBridge {

    private static final int HEADER_LENGTH = 4;

    @Override
    public OpenClProbeResult probe() {
        String payload = nativeProbe(ABI_VERSION);
        if (payload == null || payload.isBlank()) {
            return new OpenClProbeResult(false, "", 0, 0, 0L, 0L, 0, 0, 0, false, "OpenCL probe returned no metadata");
        }
        // Fixed trailing layout:
        // major|minor|free|total|driver|runtime|threads|gpu|detail.
        // The device name (fields[1..]) may itself contain '|' (the native OK
        // payload embeds it verbatim), so the trailing fields must be anchored
        // from the end instead of assuming the name occupies exactly one field.
        String[] fields = payload.split("\\|", -1);
        if (fields.length < 11) {
            return new OpenClProbeResult(false, "", 0, 0, 0L, 0L, 0, 0, 0, false, "Malformed OpenCL probe metadata");
        }
        int tail = fields.length - 9;
        if (!"OK".equals(fields[0])) {
            // Native failure payloads leave the numeric fields empty; the last
            // field carries the actionable detail and must be surfaced as-is.
            return new OpenClProbeResult(false, String.join("|", Arrays.copyOfRange(fields, 1, tail)), 0, 0, 0L, 0L, 0,
                    0, 0, false, fields[fields.length - 1]);
        }
        try {
            return new OpenClProbeResult(true, String.join("|", Arrays.copyOfRange(fields, 1, tail)),
                    Integer.parseInt(fields[tail]), Integer.parseInt(fields[tail + 1]),
                    Long.parseLong(fields[tail + 2]), Long.parseLong(fields[tail + 3]),
                    Integer.parseInt(fields[tail + 4]), Integer.parseInt(fields[tail + 5]),
                    Integer.parseInt(fields[tail + 6]), "1".equals(fields[tail + 7]), fields[tail + 8]);
        } catch (NumberFormatException exception) {
            return new OpenClProbeResult(false, "", 0, 0, 0L, 0L, 0, 0, 0, false,
                    "Malformed OpenCL probe number: " + exception.getMessage());
        }
    }

    @Override
    public OpenClEvaluationResult evaluate(NativeForecastRequest request) {
        double[] payload = nativeEvaluate(ABI_VERSION, request.fromInclusive(), request.decisionCount(),
                request.horizon(), request.iterationCount(), request.lookbackBarCount(), request.seed(),
                request.shockModel(), request.volatilityMode(), request.volatilityDecayFactor(), request.quantiles(),
                request.stable(), request.prices(), request.means(), request.drifts(), request.variances(),
                request.historicalReturns());
        if (payload == null || payload.length < HEADER_LENGTH) {
            throw new IllegalStateException("OpenCL evaluation returned no result payload");
        }
        return new OpenClEvaluationResult(payload[0], payload[1], payload[2], payload[3],
                Arrays.copyOfRange(payload, HEADER_LENGTH, payload.length));
    }

    private static native String nativeProbe(int abiVersion);

    private static native double[] nativeEvaluate(int abiVersion, int fromInclusive, int decisionCount, int horizon,
            int iterationCount, int lookbackBarCount, long seed, int shockModel, int volatilityMode,
            double volatilityDecayFactor, double[] quantiles, int[] stable, double[] prices, double[] means,
            double[] drifts, double[] variances, double[] historicalReturns);
}
