/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.portfolio;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.portfolio.PortfolioAllocation;
import org.ta4j.core.portfolio.PortfolioExecutionResult;
import org.ta4j.core.portfolio.PortfolioSeries;
import org.ta4j.core.portfolio.PortfolioSeriesManager;
import org.ta4j.core.portfolio.PortfolioSnapshot;
import org.ta4j.core.portfolio.RebalancePolicy;

/**
 * Demonstrates the static target-weight portfolio foundation.
 *
 * <p>
 * The example uses deterministic synthetic prices so the API flow is easy to
 * inspect: align asset series by common bar end time, choose target weights,
 * rebalance on selected bars, and inspect the resulting portfolio snapshots.
 * </p>
 */
public final class StaticPortfolioBacktest {

    private StaticPortfolioBacktest() {
    }

    public static void main(String[] args) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        BarSeries equitySeries = series("Equity fund", start, 100, 103, 101, 108, 112);
        BarSeries bondSeries = series("Bond fund", start, 50, 51, 51.5, 52, 52.5);
        BarSeries commoditySeries = series("Commodity fund", start, 25, 24, 26, 27, 26.5);

        String equity = "EQUITY";
        String bonds = "BONDS";
        String commodities = "COMMODITIES";
        Map<String, BarSeries> assetSeries = new LinkedHashMap<>();
        assetSeries.put(equity, equitySeries);
        assetSeries.put(bonds, bondSeries);
        assetSeries.put(commodities, commoditySeries);
        PortfolioSeries portfolioSeries = new PortfolioSeries(assetSeries);

        Map<String, Num> targetWeights = new LinkedHashMap<>();
        targetWeights.put(equity, equitySeries.numFactory().numOf(0.60));
        targetWeights.put(bonds, equitySeries.numFactory().numOf(0.30));
        targetWeights.put(commodities, equitySeries.numFactory().numOf(0.05));
        PortfolioAllocation allocation = new PortfolioAllocation(targetWeights, equitySeries.numFactory());

        PortfolioSeriesManager manager = new PortfolioSeriesManager(portfolioSeries,
                new LinearTransactionCostModel(0.001));
        PortfolioExecutionResult result = manager.run(allocation, equitySeries.numFactory().numOf(10_000),
                RebalancePolicy.onIndexes(Set.of(0, 3)));

        System.out.println("Static target-weight portfolio backtest");
        System.out.printf("Aligned bars: %d%n", portfolioSeries.getBarCount());
        System.out.printf("Final value: %.2f%n", result.getFinalValue().doubleValue());
        System.out.printf("Total return: %.2f%%%n",
                result.getTotalReturn().multipliedBy(equitySeries.numFactory().hundred()).doubleValue());
        System.out.printf("Transaction costs: %.2f%n", result.getTotalTransactionCost().doubleValue());
        System.out.println();

        for (PortfolioSnapshot snapshot : result.getSnapshots()) {
            System.out.printf("index=%d value=%.2f cash=%.2f return=%.4f turnover=%.2f%n", snapshot.getIndex(),
                    snapshot.getPortfolioValue().doubleValue(), snapshot.getCash().doubleValue(),
                    snapshot.getPeriodReturn().doubleValue(), snapshot.getTurnover().doubleValue());
        }
    }

    private static BarSeries series(String name, Instant start, double... closes) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).build();
        Num zero = series.numFactory().zero();
        for (int i = 0; i < closes.length; i++) {
            Num close = series.numFactory().numOf(closes[i]);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(i)))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(zero)
                    .add();
        }
        return series;
    }
}
