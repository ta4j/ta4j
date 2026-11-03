/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.portfolio.PortfolioSnapshot.RebalanceStatus;

/**
 * Benchmark for {@link PortfolioSnapshot#getAssetWeights()}.
 */
@Tag("benchmark")
class PortfolioSnapshotBenchmarkTest {

    private static final Logger LOG = LogManager.getLogger(PortfolioSnapshotBenchmarkTest.class);
    private static final NumFactory NUM = DoubleNumFactory.getInstance();

    @Test
    void measuresGetAssetWeightsPerformance() {
        int numAssets = 500;
        int iterations = 100_000;

        Map<String, Num> prices = new LinkedHashMap<>();
        Map<String, Num> holdings = new LinkedHashMap<>();

        for (int i = 0; i < numAssets; i++) {
            String assetName = "ASSET_" + i;
            prices.put(assetName, NUM.numOf(100 + i));
            holdings.put(assetName, NUM.numOf(10 + (i % 5)));
        }

        PortfolioSnapshot snapshot = new PortfolioSnapshot(0, PortfolioFixtures.START, prices, holdings,
                NUM.numOf(10_000), NUM.numOf(1_000_000), NUM.zero(), NUM.zero(), NUM.zero(), NUM.zero(),
                RebalanceStatus.NOT_SCHEDULED);

        // Warmup
        for (int i = 0; i < 5_000; i++) {
            Map<String, Num> weights = snapshot.getAssetWeights();
            assertFalse(weights.isEmpty());
        }

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Map<String, Num> weights = snapshot.getAssetWeights();
            assertFalse(weights.isEmpty());
        }
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        LOG.info("getAssetWeights benchmark: {} assets, {} iterations -> {} ms", numAssets, iterations, elapsedMs);
    }
}
