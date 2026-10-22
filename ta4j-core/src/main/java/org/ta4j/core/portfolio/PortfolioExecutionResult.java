/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.math.MathContext;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.portfolio.PortfolioSnapshot.RebalanceStatus;

/**
 * Immutable result of a static target-weight portfolio run.
 *
 * <p>
 * {@link #toString()} gives a compact summary (date range, value, return,
 * costs, rebalance outcomes); the full per-bar history is available through
 * {@link #getSnapshots()}. Returns are fractions ({@code 0.05} means 5%).
 * </p>
 *
 * @since 0.25.1
 */
public final class PortfolioExecutionResult {

    private static final MathContext SUMMARY_PRECISION = new MathContext(6);

    private final PortfolioSeries series;
    private final PortfolioAllocation allocation;
    private final Num initialCash;
    private final List<PortfolioSnapshot> snapshots;
    private final Num totalTransactionCost;
    private final Num totalTradedNotional;
    private final Num totalTurnover;
    private final Map<RebalanceStatus, Integer> rebalanceCounts;

    PortfolioExecutionResult(PortfolioSeries series, PortfolioAllocation allocation, Num initialCash,
            List<PortfolioSnapshot> snapshots) {
        this.series = Objects.requireNonNull(series, "series");
        this.allocation = Objects.requireNonNull(allocation, "allocation");
        this.initialCash = Objects.requireNonNull(initialCash, "initialCash");
        this.snapshots = List.copyOf(snapshots);
        if (this.snapshots.isEmpty()) {
            throw new IllegalArgumentException("snapshots must not be empty");
        }
        Num zero = initialCash.getNumFactory().zero();
        Num costs = zero;
        Num traded = zero;
        Num turnover = zero;
        Map<RebalanceStatus, Integer> counts = new EnumMap<>(RebalanceStatus.class);
        for (RebalanceStatus status : RebalanceStatus.values()) {
            counts.put(status, 0);
        }
        for (PortfolioSnapshot snapshot : this.snapshots) {
            costs = costs.plus(snapshot.getTransactionCost());
            traded = traded.plus(snapshot.getTradedNotional());
            turnover = turnover.plus(snapshot.getTurnover());
            counts.merge(snapshot.getRebalanceStatus(), 1, Integer::sum);
        }
        this.totalTransactionCost = costs;
        this.totalTradedNotional = traded;
        this.totalTurnover = turnover;
        this.rebalanceCounts = counts;
    }

    /**
     * @return aligned portfolio series the run used
     * @since 0.25.1
     */
    public PortfolioSeries getPortfolioSeries() {
        return series;
    }

    /**
     * @return target allocation the run traded toward
     * @since 0.25.1
     */
    public PortfolioAllocation getAllocation() {
        return allocation;
    }

    /**
     * @return starting cash in the portfolio numeric factory
     * @since 0.25.1
     */
    public Num getInitialCash() {
        return initialCash;
    }

    /**
     * @return one immutable snapshot per aligned bar, in chronological order
     * @since 0.25.1
     */
    public List<PortfolioSnapshot> getSnapshots() {
        return snapshots;
    }

    /**
     * Returns the snapshots whose rebalance ended with {@code status}, for example
     * {@code getSnapshots(RebalanceStatus.PARTIAL)} to audit rebalances that could
     * not restore the targets.
     *
     * @param status rebalance status to select
     * @return matching snapshots in chronological order
     * @since 0.25.1
     */
    public List<PortfolioSnapshot> getSnapshots(RebalanceStatus status) {
        Objects.requireNonNull(status, "status");
        return snapshots.stream().filter(snapshot -> snapshot.getRebalanceStatus() == status).toList();
    }

    /**
     * @return final portfolio snapshot
     * @since 0.25.1
     */
    public PortfolioSnapshot getFinalSnapshot() {
        return snapshots.getLast();
    }

    /**
     * @return final portfolio value
     * @since 0.25.1
     */
    public Num getFinalValue() {
        return getFinalSnapshot().getPortfolioValue();
    }

    /**
     * @return fractional return from the initial cash to the final value, net of
     *         all transaction costs including the initial investment's
     * @since 0.25.1
     */
    public Num getTotalReturn() {
        return getFinalValue().minus(initialCash).dividedBy(initialCash);
    }

    /**
     * @return cumulative transaction costs
     * @since 0.25.1
     */
    public Num getTotalTransactionCost() {
        return totalTransactionCost;
    }

    /**
     * @return cumulative gross notional traded (buys plus sells, excluding costs),
     *         in currency units
     * @since 0.25.1
     */
    public Num getTotalTradedNotional() {
        return totalTradedNotional;
    }

    /**
     * @return sum of every snapshot's {@link PortfolioSnapshot#getTurnover()
     *         turnover ratio}
     * @since 0.25.1
     */
    public Num getTotalTurnover() {
        return totalTurnover;
    }

    /**
     * @param status rebalance status to count
     * @return number of bars that ended with {@code status}
     * @since 0.25.1
     */
    public int getRebalanceCount(RebalanceStatus status) {
        return rebalanceCounts.get(Objects.requireNonNull(status, "status"));
    }

    /**
     * @return achieved asset weights at the final bar, in portfolio order
     * @since 0.25.1
     */
    public Map<String, Num> getFinalWeights() {
        return getFinalSnapshot().getAssetWeights();
    }

    /**
     * Converts the run into a portfolio value (equity) series for existing ta4j
     * indicators and criteria, for example
     * {@code new EnterAndHoldCriterion(new NetReturnCriterion())} or
     * {@code new EnterAndHoldCriterion(new MaximumDrawdownCriterion())}.
     *
     * <p>
     * The series has one more bar than {@link #getSnapshots()}: bar {@code 0} is
     * the initial-capital observation, ending at the begin time of the first
     * aligned bar, so the initial investment's transaction costs are visible to
     * return and drawdown analysis. Bar {@code i + 1} holds snapshot {@code i}.
     * OHLC prices all equal the portfolio value and volume is zero.
     * </p>
     *
     * @param name series name
     * @return portfolio value series
     * @since 0.25.1
     */
    public BarSeries toPortfolioValueSeries(String name) {
        BarSeries valueSeries = new BaseBarSeriesBuilder().withName(name)
                .withNumFactory(initialCash.getNumFactory())
                .build();
        Duration firstPeriod = series.timePeriod(0);
        addValueBar(valueSeries, firstPeriod, series.getEndTimes().getFirst().minus(firstPeriod), initialCash);
        for (PortfolioSnapshot snapshot : snapshots) {
            addValueBar(valueSeries, series.timePeriod(snapshot.getIndex()), snapshot.getEndTime(),
                    snapshot.getPortfolioValue());
        }
        return valueSeries;
    }

    /**
     * Same as {@link #toPortfolioValueSeries(String)} with the name
     * {@code "Portfolio value"}.
     *
     * @return portfolio value series
     * @since 0.25.1
     */
    public BarSeries toPortfolioValueSeries() {
        return toPortfolioValueSeries("Portfolio value");
    }

    /**
     * @return compact summary: aligned date range, initial and final value, total
     *         return, costs, traded notional, rebalance outcomes, and final weights
     */
    @Override
    public String toString() {
        StringBuilder weights = new StringBuilder("{");
        for (Map.Entry<String, Num> entry : getFinalWeights().entrySet()) {
            weights.append(entry.getKey()).append('=').append(brief(entry.getValue())).append(", ");
        }
        weights.append("cash=").append(brief(getFinalSnapshot().getCashWeight())).append('}');
        Num totalReturn = getTotalReturn();
        return "PortfolioExecutionResult{bars=" + snapshots.size() + ", from=" + series.getEndTimes().getFirst()
                + ", to=" + series.getEndTimes().getLast() + ", initialCash=" + brief(initialCash) + ", finalValue="
                + brief(getFinalValue()) + ", totalReturn=" + brief(totalReturn) + " ("
                + brief(totalReturn.multipliedBy(totalReturn.getNumFactory().hundred())) + "%), transactionCost="
                + brief(totalTransactionCost) + ", tradedNotional=" + brief(totalTradedNotional) + ", rebalances="
                + rebalanceSummary() + ", finalWeights=" + weights + '}';
    }

    private String rebalanceSummary() {
        return "{completed=" + rebalanceCounts.get(RebalanceStatus.COMPLETED) + ", partial="
                + rebalanceCounts.get(RebalanceStatus.PARTIAL) + ", skipped="
                + rebalanceCounts.get(RebalanceStatus.SKIPPED) + '}';
    }

    private static String brief(Num value) {
        if (!Num.isFinite(value)) {
            return value.toString();
        }
        return value.bigDecimalValue().round(SUMMARY_PRECISION).stripTrailingZeros().toPlainString();
    }

    private static void addValueBar(BarSeries valueSeries, Duration timePeriod, Instant endTime, Num value) {
        valueSeries.barBuilder()
                .timePeriod(timePeriod)
                .endTime(endTime)
                .openPrice(value)
                .highPrice(value)
                .lowPrice(value)
                .closePrice(value)
                .volume(valueSeries.numFactory().zero())
                .add();
    }
}
