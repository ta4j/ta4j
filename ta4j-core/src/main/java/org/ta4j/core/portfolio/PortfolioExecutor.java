/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Executes a deterministic static target-weight portfolio backtest.
 *
 * <p>
 * The executor marks each asset to the aligned close price, applies scheduled
 * rebalances with fractional units, solves target notionals against post-cost
 * portfolio value, and records a portfolio-level snapshot for every aligned
 * bar. It does not run per-asset strategies or advanced optimizers; callers
 * provide already decided static target weights.
 * </p>
 *
 * @since 0.23.1
 */
public final class PortfolioExecutor {

    private final AlignedPortfolioSeries series;
    private final PortfolioAllocation allocation;
    private final Map<PortfolioAsset, Num> targetWeights;
    private final Num initialCash;
    private final RebalancePolicy rebalancePolicy;
    private final CostModel transactionCostModel;

    /**
     * Creates an executor with zero transaction costs.
     *
     * @param series          aligned portfolio series
     * @param allocation      target allocation
     * @param initialCash     starting cash
     * @param rebalancePolicy rebalance policy
     * @since 0.23.1
     */
    public PortfolioExecutor(AlignedPortfolioSeries series, PortfolioAllocation allocation, Num initialCash,
            RebalancePolicy rebalancePolicy) {
        this(series, allocation, initialCash, rebalancePolicy, new ZeroCostModel());
    }

    /**
     * Creates an executor.
     *
     * @param series               aligned portfolio series
     * @param allocation           target allocation
     * @param initialCash          starting cash
     * @param rebalancePolicy      rebalance policy
     * @param transactionCostModel transaction cost model for rebalance trades
     * @since 0.23.1
     */
    public PortfolioExecutor(AlignedPortfolioSeries series, PortfolioAllocation allocation, Num initialCash,
            RebalancePolicy rebalancePolicy, CostModel transactionCostModel) {
        this.series = Objects.requireNonNull(series, "series");
        this.allocation = Objects.requireNonNull(allocation, "allocation");
        this.initialCash = requirePositive(series.toPortfolioNum(Objects.requireNonNull(initialCash, "initialCash")),
                "initialCash");
        this.rebalancePolicy = Objects.requireNonNull(rebalancePolicy, "rebalancePolicy");
        this.transactionCostModel = Objects.requireNonNull(transactionCostModel, "transactionCostModel");
        requireAllocationAssetsInSeries();
        this.targetWeights = normalizedTargetWeights();
    }

    /**
     * Runs the portfolio execution.
     *
     * @return execution result
     * @since 0.23.1
     */
    public PortfolioExecutionResult run() {
        Map<PortfolioAsset, Num> holdings = zeroHoldings();
        Num cash = initialCash;
        Num previousValue = initialCash;
        List<PortfolioSnapshot> snapshots = new java.util.ArrayList<>(series.getBarCount());

        for (int index = 0; index < series.getBarCount(); index++) {
            Map<PortfolioAsset, Num> prices = pricesAt(index);
            Num transactionCost = series.numFactory().zero();
            Num turnover = series.numFactory().zero();

            if (rebalancePolicy.shouldRebalance(index)) {
                RebalanceState rebalanceState = rebalance(cash, holdings, prices);
                cash = rebalanceState.cash();
                holdings = rebalanceState.holdings();
                transactionCost = rebalanceState.transactionCost();
                turnover = rebalanceState.turnover();
            }

            Num portfolioValue = portfolioValue(cash, holdings, prices);
            Num periodReturn = previousValue.isZero() ? series.numFactory().zero()
                    : portfolioValue.minus(previousValue).dividedBy(previousValue);
            Instant endTime = series.endTimes().get(index);
            PortfolioSnapshot snapshot = new PortfolioSnapshot(index, endTime, prices, holdings, cash, portfolioValue,
                    periodReturn, transactionCost, turnover);
            snapshots.add(snapshot);
            previousValue = portfolioValue;
        }

        return new PortfolioExecutionResult(series, allocation, initialCash, snapshots);
    }

    private RebalanceState rebalance(Num cash, Map<PortfolioAsset, Num> holdings, Map<PortfolioAsset, Num> prices) {
        Num portfolioValue = portfolioValue(cash, holdings, prices);
        Num targetPortfolioValue = postCostPortfolioValue(holdings, prices, portfolioValue);
        Map<PortfolioAsset, Num> targetNotionals = targetNotionals(targetPortfolioValue);
        Map<PortfolioAsset, Num> nextHoldings = new LinkedHashMap<>(holdings);
        Num nextCash = cash;
        Num transactionCost = series.numFactory().zero();
        Num turnover = series.numFactory().zero();

        for (PortfolioAsset asset : series.assets()) {
            Num price = prices.get(asset);
            Num currentUnits = nextHoldings.get(asset);
            Num currentNotional = price.multipliedBy(currentUnits);
            Num targetNotional = targetNotionals.get(asset);
            Num deltaNotional = targetNotional.minus(currentNotional);
            if (deltaNotional.isNegative()) {
                Num gross = deltaNotional.abs();
                Num cost = transactionCost(price, gross);
                Num amount = gross.dividedBy(price);
                nextCash = nextCash.plus(gross).minus(cost);
                nextHoldings.put(asset, currentUnits.minus(amount));
                transactionCost = transactionCost.plus(cost);
                turnover = turnover.plus(gross);
            }
        }

        for (PortfolioAsset asset : series.assets()) {
            Num price = prices.get(asset);
            Num currentUnits = nextHoldings.get(asset);
            Num currentNotional = price.multipliedBy(currentUnits);
            Num desiredGross = targetNotionals.get(asset).minus(currentNotional);
            if (desiredGross.isZero() || desiredGross.isNegative()) {
                continue;
            }

            desiredGross = affordableGross(price, desiredGross, nextCash);
            if (desiredGross.isZero() || desiredGross.isNegative()) {
                continue;
            }

            Num amount = desiredGross.dividedBy(price);
            Num cost = transactionCost(price, desiredGross);
            Num requiredCash = desiredGross.plus(cost);

            nextCash = nextCash.minus(requiredCash);
            nextHoldings.put(asset, nextHoldings.get(asset).plus(amount));
            transactionCost = transactionCost.plus(cost);
            turnover = turnover.plus(desiredGross);
        }

        return new RebalanceState(nextCash, orderedUnmodifiableMap(nextHoldings), transactionCost, turnover);
    }

    private Num postCostPortfolioValue(Map<PortfolioAsset, Num> holdings, Map<PortfolioAsset, Num> prices,
            Num preCostPortfolioValue) {
        Num costAtPreCostValue = rebalanceCost(holdings, prices, preCostPortfolioValue);
        if (costAtPreCostValue.isZero()) {
            return preCostPortfolioValue;
        }

        Num low = series.numFactory().zero();
        Num high = preCostPortfolioValue;
        Num tolerance = valueTolerance(preCostPortfolioValue);

        for (int iteration = 0; iteration < 64; iteration++) {
            Num mid = low.plus(high).dividedBy(series.numFactory().two());
            Num remainingValue = preCostPortfolioValue.minus(rebalanceCost(holdings, prices, mid));
            if (remainingValue.isGreaterThanOrEqual(mid)) {
                low = mid;
            } else {
                high = mid;
            }
            if (high.minus(low).abs().isLessThanOrEqual(tolerance)) {
                break;
            }
        }

        return low;
    }

    private Num rebalanceCost(Map<PortfolioAsset, Num> holdings, Map<PortfolioAsset, Num> prices,
            Num targetPortfolioValue) {
        Num totalCost = series.numFactory().zero();

        for (PortfolioAsset asset : series.assets()) {
            Num price = prices.get(asset);
            Num currentNotional = price.multipliedBy(holdings.get(asset));
            Num targetNotional = targetPortfolioValue.multipliedBy(targetWeight(asset));
            Num gross = targetNotional.minus(currentNotional).abs();
            totalCost = totalCost.plus(transactionCost(price, gross));
        }

        return totalCost;
    }

    private Map<PortfolioAsset, Num> targetNotionals(Num targetPortfolioValue) {
        Map<PortfolioAsset, Num> targetNotionals = new LinkedHashMap<>();
        for (PortfolioAsset asset : series.assets()) {
            targetNotionals.put(asset, targetPortfolioValue.multipliedBy(targetWeight(asset)));
        }
        return orderedUnmodifiableMap(targetNotionals);
    }

    private Num affordableGross(Num price, Num desiredGross, Num cash) {
        if (desiredGross.isNegativeOrZero() || cash.isNegativeOrZero()) {
            return series.numFactory().zero();
        }
        if (isBuyAffordable(price, desiredGross, cash)) {
            return desiredGross;
        }

        Num low = series.numFactory().zero();
        Num high = desiredGross;
        Num tolerance = valueTolerance(desiredGross);
        for (int iteration = 0; iteration < 64; iteration++) {
            Num mid = low.plus(high).dividedBy(series.numFactory().two());
            if (isBuyAffordable(price, mid, cash)) {
                low = mid;
            } else {
                high = mid;
            }
            if (high.minus(low).abs().isLessThanOrEqual(tolerance)) {
                break;
            }
        }

        return low;
    }

    private boolean isBuyAffordable(Num price, Num gross, Num cash) {
        Num requiredCash = gross.plus(transactionCost(price, gross));
        return requiredCash.isLessThanOrEqual(cash);
    }

    private Num transactionCost(Num price, Num gross) {
        if (gross.isNegativeOrZero()) {
            return series.numFactory().zero();
        }
        return transactionCostModel.calculate(price, gross.dividedBy(price));
    }

    private Num valueTolerance(Num reference) {
        Num epsilon = series.numFactory().epsilon();
        return epsilon.max(reference.abs().multipliedBy(epsilon));
    }

    private Map<PortfolioAsset, Num> zeroHoldings() {
        Map<PortfolioAsset, Num> holdings = new LinkedHashMap<>();
        Num zero = series.numFactory().zero();
        for (PortfolioAsset asset : series.assets()) {
            holdings.put(asset, zero);
        }
        return orderedUnmodifiableMap(holdings);
    }

    private Map<PortfolioAsset, Num> pricesAt(int index) {
        Map<PortfolioAsset, Num> prices = new LinkedHashMap<>();
        for (PortfolioAsset asset : series.assets()) {
            Num price = requirePositive(series.getClosePrice(asset, index), "close price for " + asset);
            prices.put(asset, price);
        }
        return orderedUnmodifiableMap(prices);
    }

    private Num portfolioValue(Num cash, Map<PortfolioAsset, Num> holdings, Map<PortfolioAsset, Num> prices) {
        Num value = cash;
        for (PortfolioAsset asset : series.assets()) {
            value = value.plus(prices.get(asset).multipliedBy(holdings.get(asset)));
        }
        return value;
    }

    private void requireAllocationAssetsInSeries() {
        for (PortfolioAsset asset : allocation.targetWeights().keySet()) {
            if (!series.assets().contains(asset)) {
                throw new IllegalArgumentException("allocation asset is not in the portfolio series: " + asset);
            }
        }
    }

    private Map<PortfolioAsset, Num> normalizedTargetWeights() {
        Map<PortfolioAsset, Num> normalizedWeights = new LinkedHashMap<>();
        for (PortfolioAsset asset : series.assets()) {
            normalizedWeights.put(asset, series.toPortfolioNum(allocation.targetWeight(asset)));
        }
        return orderedUnmodifiableMap(normalizedWeights);
    }

    private Num targetWeight(PortfolioAsset asset) {
        return targetWeights.get(asset);
    }

    private static Num requirePositive(Num value, String name) {
        Objects.requireNonNull(value, name);
        if (Num.isNaNOrNull(value) || Double.isInfinite(value.doubleValue()) || value.isNegativeOrZero()) {
            throw new IllegalArgumentException(name + " must be finite and > 0");
        }
        return value;
    }

    private static <K, V> Map<K, V> orderedUnmodifiableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private record RebalanceState(Num cash, Map<PortfolioAsset, Num> holdings, Num transactionCost, Num turnover) {
    }
}
