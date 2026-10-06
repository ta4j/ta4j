/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;

/**
 * Benchmark for PortfolioSeriesManager allocation check and run execution over
 * large asset sets.
 */
@Tag("benchmark")
class PortfolioSeriesManagerBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(PortfolioSeriesManagerBenchmarkTest.class);

    private static final int ASSET_COUNT = 500;
    private static final int ITERATIONS = 5_000;

    @Test
    void benchmarkExecutionOverLargePortfolio() {
        List<BarSeries> seriesList = new ArrayList<>(ASSET_COUNT);
        Map<String, Double> weights = new LinkedHashMap<>();
        double weight = 1.0 / ASSET_COUNT;

        for (int i = 0; i < ASSET_COUNT; i++) {
            String assetName = "ASSET_" + i;
            seriesList.add(PortfolioFixtures.series(assetName, 100.0, 105.0));
            weights.put(assetName, weight);
        }

        PortfolioSeries series = new PortfolioSeries(seriesList);
        PortfolioSeriesManager manager = new PortfolioSeriesManager(series);
        PortfolioAllocation allocation = new PortfolioAllocation(weights);

        // Warmup
        for (int i = 0; i < 200; i++) {
            manager.run(allocation, 10_000);
        }

        long start = System.nanoTime();
        PortfolioExecutionResult lastResult = null;
        for (int i = 0; i < ITERATIONS; i++) {
            lastResult = manager.run(allocation, 10_000);
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertNotNull(lastResult);
        LOG.info("PortfolioSeriesManager benchmark: {} assets x {} iterations -> {} ms", ASSET_COUNT, ITERATIONS,
                elapsedMs);
    }
}
