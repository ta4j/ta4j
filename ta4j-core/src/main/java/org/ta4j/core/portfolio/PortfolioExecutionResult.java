/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.Num;

/**
 * Immutable result of a static target-weight portfolio execution.
 *
 * @since 0.23.1
 */
public final class PortfolioExecutionResult {

    private final PortfolioSeries series;
    private final PortfolioAllocation allocation;
    private final Num initialCash;
    private final List<PortfolioSnapshot> snapshots;

    PortfolioExecutionResult(PortfolioSeries series, PortfolioAllocation allocation, Num initialCash,
            List<PortfolioSnapshot> snapshots) {
        this.series = Objects.requireNonNull(series, "series");
        this.allocation = Objects.requireNonNull(allocation, "allocation");
        this.initialCash = Objects.requireNonNull(initialCash, "initialCash");
        this.snapshots = List.copyOf(Objects.requireNonNull(snapshots, "snapshots"));
        if (snapshots.isEmpty()) {
            throw new IllegalArgumentException("snapshots must not be empty");
        }
    }

    /**
     * @return aligned source portfolio series
     * @since 0.23.1
     */
    public PortfolioSeries getPortfolioSeries() {
        return series;
    }

    /**
     * @return target allocation used for execution
     * @since 0.23.1
     */
    public PortfolioAllocation getAllocation() {
        return allocation;
    }

    /**
     * @return starting cash
     * @since 0.23.1
     */
    public Num getInitialCash() {
        return initialCash;
    }

    /**
     * @return immutable portfolio snapshots
     * @since 0.23.1
     */
    public List<PortfolioSnapshot> getSnapshots() {
        return snapshots;
    }

    /**
     * @return final portfolio snapshot
     * @since 0.23.1
     */
    public PortfolioSnapshot getFinalSnapshot() {
        return snapshots.getLast();
    }

    /**
     * @return final portfolio value
     * @since 0.23.1
     */
    public Num getFinalValue() {
        return getFinalSnapshot().getPortfolioValue();
    }

    /**
     * @return total return from initial cash to final portfolio value
     * @since 0.23.1
     */
    public Num getTotalReturn() {
        return getFinalValue().minus(initialCash).dividedBy(initialCash);
    }

    /**
     * @return cumulative transaction costs
     * @since 0.23.1
     */
    public Num getTotalTransactionCost() {
        Num totalCost = initialCash.getNumFactory().zero();
        for (PortfolioSnapshot snapshot : snapshots) {
            totalCost = totalCost.plus(snapshot.getTransactionCost());
        }
        return totalCost;
    }

    /**
     * @return cumulative gross notional traded, excluding costs
     * @since 0.23.1
     */
    public Num getTotalTurnover() {
        Num totalTurnover = initialCash.getNumFactory().zero();
        for (PortfolioSnapshot snapshot : snapshots) {
            totalTurnover = totalTurnover.plus(snapshot.getTurnover());
        }
        return totalTurnover;
    }

    /**
     * Converts the portfolio equity curve into a bar series whose OHLC values all
     * equal each snapshot's portfolio value.
     *
     * @param name series name
     * @return portfolio value series
     * @since 0.23.1
     */
    public BarSeries toPortfolioValueSeries(String name) {
        BarSeries valueSeries = new BaseBarSeriesBuilder().withName(name)
                .withNumFactory(initialCash.getNumFactory())
                .build();
        String firstAsset = series.getAssets().getFirst();
        Num zero = initialCash.getNumFactory().zero();
        for (PortfolioSnapshot snapshot : snapshots) {
            Num value = snapshot.getPortfolioValue();
            valueSeries.barBuilder()
                    .timePeriod(series.getBar(firstAsset, snapshot.getIndex()).getTimePeriod())
                    .endTime(snapshot.getEndTime())
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
     * @since 0.23.1
     */
    public Map<String, Num> getFinalWeights() {
        PortfolioSnapshot finalSnapshot = getFinalSnapshot();
        Map<String, Num> weights = new LinkedHashMap<>();
        for (String asset : series.getAssets()) {
            weights.put(asset, finalSnapshot.getAssetWeight(asset));
        }
        return Collections.unmodifiableMap(weights);
    }
}
