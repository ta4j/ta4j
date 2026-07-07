/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.portfolio;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.portfolio.AlignedPortfolioSeries;
import org.ta4j.core.portfolio.PortfolioAllocation;
import org.ta4j.core.portfolio.PortfolioAsset;
import org.ta4j.core.portfolio.PortfolioExecutionResult;
import org.ta4j.core.portfolio.PortfolioExecutor;
import org.ta4j.core.portfolio.PortfolioSeries;
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

        PortfolioAsset equity = PortfolioAsset.of("EQUITY");
        PortfolioAsset bonds = PortfolioAsset.of("BONDS");
        PortfolioAsset commodities = PortfolioAsset.of("COMMODITIES");
        AlignedPortfolioSeries portfolioSeries = AlignedPortfolioSeries
                .of(List.of(new PortfolioSeries(equity, equitySeries), new PortfolioSeries(bonds, bondSeries),
                        new PortfolioSeries(commodities, commoditySeries)));

        Map<PortfolioAsset, Num> targetWeights = new LinkedHashMap<>();
        targetWeights.put(equity, equitySeries.numFactory().numOf(0.60));
        targetWeights.put(bonds, equitySeries.numFactory().numOf(0.30));
        targetWeights.put(commodities, equitySeries.numFactory().numOf(0.05));
        PortfolioAllocation allocation = PortfolioAllocation.targetWeights(targetWeights, equitySeries.numFactory());

        PortfolioExecutionResult result = new PortfolioExecutor(portfolioSeries, allocation,
                equitySeries.numFactory().numOf(10_000), RebalancePolicy.onIndexes(Set.of(0, 3)),
                new LinearTransactionCostModel(0.001)).run();

        System.out.println("Static target-weight portfolio backtest");
        System.out.printf("Aligned bars: %d%n", portfolioSeries.getBarCount());
        System.out.printf("Final value: %.2f%n", result.finalValue().doubleValue());
        System.out.printf("Total return: %.2f%%%n",
                result.totalReturn().multipliedBy(equitySeries.numFactory().hundred()).doubleValue());
        System.out.printf("Transaction costs: %.2f%n", result.totalTransactionCost().doubleValue());
        System.out.println();

        for (PortfolioSnapshot snapshot : result.snapshots()) {
            System.out.printf("index=%d value=%.2f cash=%.2f return=%.4f turnover=%.2f%n", snapshot.index(),
                    snapshot.portfolioValue().doubleValue(), snapshot.cash().doubleValue(),
                    snapshot.periodReturn().doubleValue(), snapshot.turnover().doubleValue());
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
