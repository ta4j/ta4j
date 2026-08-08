/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.Arrays;

interface CudaNativeBridge {

    int ABI_VERSION = 1;

    CudaProbeResult probe();

    CudaEvaluationResult evaluate(NativeForecastRequest request);
}

record CudaProbeResult(boolean available, String deviceName, int computeMajor, int computeMinor, long freeMemoryBytes,
        long totalMemoryBytes, int driverVersion, int runtimeVersion, String detail) {
}

record CudaEvaluationResult(double totalMicros, double transferMicros, double kernelMicros, double reductionMicros,
        double[] rows) {

    CudaEvaluationResult {
        rows = Arrays.copyOf(rows, rows.length);
    }

    @Override
    public double[] rows() {
        return Arrays.copyOf(rows, rows.length);
    }
}

final class JniCudaNativeBridge implements CudaNativeBridge {

    private static final int HEADER_LENGTH = 4;

    @Override
    public CudaProbeResult probe() {
        String payload = nativeProbe(ABI_VERSION);
        if (payload == null || payload.isBlank()) {
            return new CudaProbeResult(false, "", 0, 0, 0L, 0L, 0, 0, "CUDA probe returned no metadata");
        }
        // Fixed trailing layout: major|minor|free|total|driver|runtime|detail.
        // Anchor the trailing fields from the end so a '|' inside the device
        // name cannot shift the numeric fields.
        String[] fields = payload.split("\\|", -1);
        if (fields.length < 9) {
            return new CudaProbeResult(false, "", 0, 0, 0L, 0L, 0, 0, "Malformed CUDA probe metadata");
        }
        int tail = fields.length - 7;
        if (!"OK".equals(fields[0])) {
            // Native failure payloads leave the numeric fields empty; the last
            // field carries the actionable detail and must be surfaced as-is.
            return new CudaProbeResult(false, String.join("|", Arrays.copyOfRange(fields, 1, tail)), 0, 0, 0L, 0L, 0,
                    0, fields[fields.length - 1]);
        }
        try {
            return new CudaProbeResult(true, String.join("|", Arrays.copyOfRange(fields, 1, tail)),
                    Integer.parseInt(fields[tail]), Integer.parseInt(fields[tail + 1]),
                    Long.parseLong(fields[tail + 2]), Long.parseLong(fields[tail + 3]),
                    Integer.parseInt(fields[tail + 4]), Integer.parseInt(fields[tail + 5]), fields[tail + 6]);
        } catch (NumberFormatException exception) {
            return new CudaProbeResult(false, "", 0, 0, 0L, 0L, 0, 0,
                    "Malformed CUDA probe number: " + exception.getMessage());
        }
    }

    @Override
    public CudaEvaluationResult evaluate(NativeForecastRequest request) {
        double[] payload = nativeEvaluate(ABI_VERSION, request.fromInclusive(), request.decisionCount(),
                request.horizon(), request.iterationCount(), request.lookbackBarCount(), request.seed(),
                request.shockModel(), request.volatilityMode(), request.volatilityDecayFactor(), request.quantiles(),
                request.stable(), request.prices(), request.means(), request.drifts(), request.variances(),
                request.historicalReturns());
        if (payload == null || payload.length < HEADER_LENGTH) {
            throw new IllegalStateException("CUDA evaluation returned no result payload");
        }
        return new CudaEvaluationResult(payload[0], payload[1], payload[2], payload[3],
                Arrays.copyOfRange(payload, HEADER_LENGTH, payload.length));
    }

    private static native String nativeProbe(int abiVersion);

    private static native double[] nativeEvaluate(int abiVersion, int fromInclusive, int decisionCount, int horizon,
            int iterationCount, int lookbackBarCount, long seed, int shockModel, int volatilityMode,
            double volatilityDecayFactor, double[] quantiles, int[] stable, double[] prices, double[] means,
            double[] drifts, double[] variances, double[] historicalReturns);
}
