/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

@Tag("benchmark")
public class PortfolioSeriesManagerBenchmarkTest {

    @Test
    public void benchmarkPortfolioSeriesManagerRun() {
        int barCount = 10_000;
        int assetCount = 5;
        NumFactory numFactory = DoubleNumFactory.getInstance();

        BarSeries[] assetSeries = new BarSeries[assetCount];
        Map<String, Double> weightsMap = new LinkedHashMap<>();

        for (int a = 0; a < assetCount; a++) {
            String name = "ASSET_" + a;
            String[] closes = new String[barCount];
            double basePrice = 100.0 + a * 10.0;
            for (int i = 0; i < barCount; i++) {
                closes[i] = Double.toString(basePrice + Math.sin(i * 0.1 + a));
            }
            assetSeries[a] = PortfolioFixtures.series(name, numFactory, 0, closes);
            weightsMap.put(name, 1.0 / assetCount);
        }

        PortfolioSeries portfolioSeries = new PortfolioSeries(assetSeries);
        PortfolioSeriesManager manager = new PortfolioSeriesManager(portfolioSeries);
        PortfolioAllocation allocation = new PortfolioAllocation(weightsMap);

        // Warmup
        for (int i = 0; i < 5; i++) {
            manager.run(allocation, 100_000, RebalancePolicy.everyBar());
        }

        long start = System.nanoTime();
        int iterations = 20;
        for (int i = 0; i < iterations; i++) {
            manager.run(allocation, 100_000, RebalancePolicy.everyBar());
        }
        long elapsed = System.nanoTime() - start;

        System.out.printf(
                "PortfolioSeriesManagerBenchmark: %d iterations over %d bars x %d assets took %.3f ms (avg %.3f ms/run)%n",
                iterations, barCount, assetCount, elapsed / 1e6, (elapsed / 1e6) / iterations);
    }
}
