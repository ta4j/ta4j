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
        long totalMemoryBytes, int driverVersion, int runtimeVersion, boolean gpuDevice, String detail) {
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
            return new OpenClProbeResult(false, "", 0, 0, 0L, 0L, 0, 0, false, "OpenCL probe returned no metadata");
        }
        String[] fields = payload.split("\\|", 10);
        if (fields.length != 10) {
            return new OpenClProbeResult(false, "", 0, 0, 0L, 0L, 0, 0, false, "Malformed OpenCL probe metadata");
        }
        if (!"OK".equals(fields[0])) {
            // Native failure payloads leave the numeric fields empty; the last
            // field carries the actionable detail and must be surfaced as-is.
            return new OpenClProbeResult(false, fields[1], 0, 0, 0L, 0L, 0, 0, false, fields[9]);
        }
        try {
            return new OpenClProbeResult(true, fields[1], Integer.parseInt(fields[2]), Integer.parseInt(fields[3]),
                    Long.parseLong(fields[4]), Long.parseLong(fields[5]), Integer.parseInt(fields[6]),
                    Integer.parseInt(fields[7]), "1".equals(fields[8]), fields[9]);
        } catch (NumberFormatException exception) {
            return new OpenClProbeResult(false, "", 0, 0, 0L, 0L, 0, 0, false,
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
