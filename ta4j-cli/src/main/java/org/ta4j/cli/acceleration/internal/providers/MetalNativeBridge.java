/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import java.util.Arrays;

interface MetalNativeBridge {

    int ABI_VERSION = 1;

    MetalProbeResult probe();

    MetalEvaluationResult evaluate(NativeForecastRequest request);
}

record MetalProbeResult(boolean available, String deviceName, long recommendedMaxWorkingSetBytes, String detail) {
}

record MetalEvaluationResult(double totalMicros, double transferMicros, double kernelMicros, float[] terminalPrices) {

    MetalEvaluationResult {
        terminalPrices = Arrays.copyOf(terminalPrices, terminalPrices.length);
    }

    @Override
    public float[] terminalPrices() {
        return Arrays.copyOf(terminalPrices, terminalPrices.length);
    }
}

final class JniMetalNativeBridge implements MetalNativeBridge {

    private static final int HEADER_LENGTH = 3;

    @Override
    public MetalProbeResult probe() {
        String payload = nativeProbe(ABI_VERSION);
        if (payload == null || payload.isBlank()) {
            return new MetalProbeResult(false, "", 0L, "Metal probe returned no metadata");
        }
        String[] fields = payload.split("\\|", 4);
        if (fields.length != 4) {
            return new MetalProbeResult(false, "", 0L, "Malformed Metal probe metadata");
        }
        if (!"OK".equals(fields[0])) {
            // Native failure payloads leave the numeric fields empty; the
            // last field carries the actionable detail (for example
            // ERROR|||metal_device_unavailable) and must be surfaced as-is
            // instead of being replaced by a number-parse error.
            return new MetalProbeResult(false, "", 0L, fields[3]);
        }
        try {
            return new MetalProbeResult(true, fields[1], Long.parseLong(fields[2]), fields[3]);
        } catch (NumberFormatException exception) {
            return new MetalProbeResult(false, "", 0L, "Malformed Metal probe number: " + exception.getMessage());
        }
    }

    @Override
    public MetalEvaluationResult evaluate(NativeForecastRequest request) {
        long[] timings = new long[HEADER_LENGTH];
        float[] terminalPrices = nativeEvaluate(ABI_VERSION, request.fromInclusive(), request.decisionCount(),
                request.horizon(), request.iterationCount(), request.lookbackBarCount(), request.seed(),
                request.shockModel(), request.volatilityMode(), request.volatilityDecayFactor(), request.stable(),
                request.prices(), request.means(), request.drifts(), request.variances(), request.historicalReturns(),
                timings);
        if (terminalPrices == null) {
            throw new IllegalStateException("Metal evaluation returned no samples");
        }
        return new MetalEvaluationResult(timings[0], timings[1], timings[2], terminalPrices);
    }

    private static native String nativeProbe(int abiVersion);

    private static native float[] nativeEvaluate(int abiVersion, int fromInclusive, int decisionCount, int horizon,
            int iterationCount, int lookbackBarCount, long seed, int shockModel, int volatilityMode,
            double volatilityDecayFactor, int[] stable, double[] prices, double[] means, double[] drifts,
            double[] variances, double[] historicalReturns, long[] timings);
}
