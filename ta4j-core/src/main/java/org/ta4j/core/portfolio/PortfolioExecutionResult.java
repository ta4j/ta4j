/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.Num;

/**
 * Result of a static target-weight portfolio execution.
 *
 * <p>
 * The result exposes snapshots directly and can also materialize the portfolio
 * equity curve as a {@link BarSeries}. That value series gives existing ta4j
 * indicators, reports, and future walk-forward/ranking flows a stable bridge
 * without changing single-series strategy APIs.
 * </p>
 *
 * @param series      aligned source series
 * @param allocation  target allocation used for execution
 * @param initialCash starting cash
 * @param snapshots   portfolio snapshots
 * @since 0.22.9
 */
public record PortfolioExecutionResult(AlignedPortfolioSeries series, PortfolioAllocation allocation, Num initialCash,
        List<PortfolioSnapshot> snapshots) {

    /**
     * Creates an execution result.
     *
     * @since 0.22.9
     */
    public PortfolioExecutionResult {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(allocation, "allocation");
        Objects.requireNonNull(initialCash, "initialCash");
        snapshots = List.copyOf(Objects.requireNonNull(snapshots, "snapshots"));
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("snapshots must not be empty");
        }
    }

    /**
     * @return final portfolio snapshot
     * @since 0.22.9
     */
    public PortfolioSnapshot finalSnapshot() {
        return snapshots.getLast();
    }

    /**
     * @return final portfolio value
     * @since 0.22.9
     */
    public Num finalValue() {
        return finalSnapshot().portfolioValue();
    }

    /**
     * @return total return from initial cash to final portfolio value
     * @since 0.22.9
     */
    public Num totalReturn() {
        return finalValue().minus(initialCash).dividedBy(initialCash);
    }

    /**
     * @return cumulative transaction costs
     * @since 0.22.9
     */
    public Num totalTransactionCost() {
        Num totalCost = initialCash.getNumFactory().zero();
        for (PortfolioSnapshot snapshot : snapshots) {
            totalCost = totalCost.plus(snapshot.transactionCost());
        }
        return totalCost;
    }

    /**
     * @return cumulative gross notional traded, excluding costs
     * @since 0.22.9
     */
    public Num totalTurnover() {
        Num totalTurnover = initialCash.getNumFactory().zero();
        for (PortfolioSnapshot snapshot : snapshots) {
            totalTurnover = totalTurnover.plus(snapshot.turnover());
        }
        return totalTurnover;
    }

    /**
     * Converts the portfolio equity curve into a bar series whose OHLC values all
     * equal each snapshot's portfolio value.
     *
     * @param name series name
     * @return portfolio value series
     * @since 0.22.9
     */
    public BarSeries toPortfolioValueSeries(String name) {
        BarSeries valueSeries = new BaseBarSeriesBuilder().withName(name)
                .withNumFactory(initialCash.getNumFactory())
                .build();
        PortfolioAsset firstAsset = series.assets().getFirst();
        Num zero = initialCash.getNumFactory().zero();
        for (PortfolioSnapshot snapshot : snapshots) {
            Num value = snapshot.portfolioValue();
            valueSeries.barBuilder()
                    .timePeriod(series.getBar(firstAsset, snapshot.index()).getTimePeriod())
                    .endTime(snapshot.endTime())
                    .openPrice(value)
                    .highPrice(value)
                    .lowPrice(value)
                    .closePrice(value)
                    .volume(zero)
                    .add();
        }
        return valueSeries;
    }

    /**
     * @return final actual asset weights
     * @since 0.22.9
     */
    public Map<PortfolioAsset, Num> finalWeights() {
        PortfolioSnapshot finalSnapshot = finalSnapshot();
        return finalSnapshot.prices()
                .keySet()
                .stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(asset -> asset, finalSnapshot::assetWeight));
    }
}
