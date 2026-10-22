/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

/**
 * Benchmark for MinimumVarianceOptimizer covariance calculation and
 * optimization.
 */
@Tag("benchmark")
class MinimumVarianceOptimizerBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(MinimumVarianceOptimizerBenchmarkTest.class);

    private static final int ASSET_COUNT = 100;
    private static final int BAR_COUNT = 250;
    private static final int ITERATIONS = 100;

    @Test
    void benchmarkMinimumVarianceOptimization() {
        NumFactory numFactory = DoubleNumFactory.getInstance();
        List<BarSeries> assets = new ArrayList<>(ASSET_COUNT);

        for (int i = 0; i < ASSET_COUNT; i++) {
            double[] closes = new double[BAR_COUNT + 1];
            closes[0] = 100.0;
            for (int b = 0; b < BAR_COUNT; b++) {
                closes[b + 1] = closes[b] * (1.0 + ((i % 5 - 2) * 0.001) + Math.sin(b + i) * 0.01);
            }
            assets.add(PortfolioFixtures.series("ASSET_" + i, closes));
        }

        PortfolioSeries series = new PortfolioSeries(assets);

        // Warmup
        for (int i = 0; i < 10; i++) {
            MinimumVarianceOptimizer optimizer = new MinimumVarianceOptimizer(series, BAR_COUNT, BAR_COUNT);
            PortfolioAllocation allocation = optimizer.optimize();
            assertNotNull(allocation);
        }

        long start = System.nanoTime();
        PortfolioAllocation lastAllocation = null;
        for (int i = 0; i < ITERATIONS; i++) {
            MinimumVarianceOptimizer optimizer = new MinimumVarianceOptimizer(series, BAR_COUNT, BAR_COUNT);
            lastAllocation = optimizer.optimize();
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(lastAllocation);
        LOG.info("MinimumVarianceOptimizer benchmark: {} assets x {} bars x {} iterations -> {} ms", ASSET_COUNT,
                BAR_COUNT, ITERATIONS, elapsedMs);
    }
}
