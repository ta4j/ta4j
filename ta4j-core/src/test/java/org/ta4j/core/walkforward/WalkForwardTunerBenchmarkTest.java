/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

/**
 * Benchmark for patchCalibrationMetrics method in WalkForwardTuner.
 */
@Tag("benchmark")
class WalkForwardTunerBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(WalkForwardTunerBenchmarkTest.class);

    private static final int ITERATIONS = 10_000_000;

    @Test
    void benchmarkPatchCalibrationMetricsDirectly() {
        DoubleNumFactory numFactory = DoubleNumFactory.getInstance();
        Map<String, Num> metricMap = new HashMap<>();
        metricMap.put("brier_horizon_1", numFactory.zero());
        metricMap.put("logloss_horizon_1", numFactory.zero());
        metricMap.put("ece_horizon_1", numFactory.zero());
        metricMap.put("agreement_horizon_1", numFactory.one());
        metricMap.put("return_horizon_1", numFactory.one());

        // Dummy score values
        Num brier = numFactory.numOf(0.1);
        Num logLoss = numFactory.numOf(0.2);
        Num ece = numFactory.numOf(0.05);

        // Warmup
        for (int i = 0; i < 1_000_000; i++) {
            patchCalibrationMetricsHelper(metricMap, brier, logLoss, ece);
        }

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            patchCalibrationMetricsHelper(metricMap, brier, logLoss, ece);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertFalse(metricMap.isEmpty());
        LOG.info("patchCalibrationMetrics benchmark: {} iterations -> {} ms ({} ops/sec)", ITERATIONS, elapsedMs,
                ITERATIONS * 1000L / Math.max(1L, elapsedMs));
    }

    private static void patchCalibrationMetricsHelper(Map<String, Num> metricMap, Num brier, Num logLoss, Num ece) {
        for (Map.Entry<String, Num> entry : metricMap.entrySet()) {
            String normalized = entry.getKey().toLowerCase();
            if (normalized.contains("brier")) {
                entry.setValue(brier);
            } else if (normalized.contains("logloss") || normalized.contains("log_loss")
                    || normalized.contains("log-loss")) {
                entry.setValue(logLoss);
            } else if (normalized.contains("ece")) {
                entry.setValue(ece);
            }
        }
    }
}
