/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.Instant;
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
 * rebalances with fractional units, subtracts transaction costs, and records a
 * portfolio-level snapshot for every aligned bar. It does not run per-asset
 * strategies or advanced optimizers; callers provide already decided static
 * target weights.
 * </p>
 *
 * @since 0.22.9
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
     * @since 0.22.9
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
     * @since 0.22.9
     */
    public PortfolioExecutor(AlignedPortfolioSeries series, PortfolioAllocation allocation, Num initialCash,
            RebalancePolicy rebalancePolicy, CostModel transactionCostModel) {
        this.series = Objects.requireNonNull(series, "series");
        this.allocation = Objects.requireNonNull(allocation, "allocation");
        this.initialCash = requirePositive(series.toPortfolioNum(initialCash), "initialCash");
        this.rebalancePolicy = Objects.requireNonNull(rebalancePolicy, "rebalancePolicy");
        this.transactionCostModel = Objects.requireNonNull(transactionCostModel, "transactionCostModel");
        requireAllocationAssetsInSeries();
        this.targetWeights = normalizedTargetWeights();
    }

    /**
     * Runs the portfolio execution.
     *
     * @return execution result
     * @since 0.22.9
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
        Map<PortfolioAsset, Num> nextHoldings = new LinkedHashMap<>(holdings);
        Num nextCash = cash;
        Num transactionCost = series.numFactory().zero();
        Num turnover = series.numFactory().zero();

        for (PortfolioAsset asset : series.assets()) {
            Num price = prices.get(asset);
            Num currentUnits = nextHoldings.get(asset);
            Num currentNotional = price.multipliedBy(currentUnits);
            Num targetNotional = portfolioValue.multipliedBy(targetWeight(asset));
            Num deltaNotional = targetNotional.minus(currentNotional);
            if (deltaNotional.isNegative()) {
                Num gross = deltaNotional.abs();
                Num amount = gross.dividedBy(price);
                Num cost = transactionCostModel.calculate(price, amount);
                nextCash = nextCash.plus(gross).minus(cost);
                nextHoldings.put(asset, currentUnits.minus(amount));
                transactionCost = transactionCost.plus(cost);
                turnover = turnover.plus(gross);
            }
        }

        BuyPlan buyPlan = buyPlan(nextHoldings, prices, portfolioValue);
        Num buyScale = buyPlan.requiredCash().isZero() || nextCash.isGreaterThanOrEqual(buyPlan.requiredCash())
                ? series.numFactory().one()
                : nextCash.dividedBy(buyPlan.requiredCash());

        for (PortfolioAsset asset : series.assets()) {
            Num desiredGross = buyPlan.buyGrossByAsset().get(asset).multipliedBy(buyScale);
            if (desiredGross.isZero() || desiredGross.isNegative()) {
                continue;
            }

            Num price = prices.get(asset);
            Num amount = desiredGross.dividedBy(price);
            Num cost = transactionCostModel.calculate(price, amount);
            Num requiredCash = desiredGross.plus(cost);
            if (requiredCash.isGreaterThan(nextCash)) {
                Num affordableGross = nextCash.minus(cost);
                if (affordableGross.isNegativeOrZero()) {
                    continue;
                }
                amount = affordableGross.dividedBy(price);
                desiredGross = affordableGross;
                cost = transactionCostModel.calculate(price, amount);
                requiredCash = desiredGross.plus(cost);
                if (requiredCash.isGreaterThan(nextCash)) {
                    continue;
                }
            }

            nextCash = nextCash.minus(requiredCash);
            nextHoldings.put(asset, nextHoldings.get(asset).plus(amount));
            transactionCost = transactionCost.plus(cost);
            turnover = turnover.plus(desiredGross);
        }

        return new RebalanceState(nextCash, Map.copyOf(nextHoldings), transactionCost, turnover);
    }

    private BuyPlan buyPlan(Map<PortfolioAsset, Num> holdings, Map<PortfolioAsset, Num> prices, Num portfolioValue) {
        Map<PortfolioAsset, Num> buyGrossByAsset = new LinkedHashMap<>();
        Num totalGross = series.numFactory().zero();
        Num totalCost = series.numFactory().zero();

        for (PortfolioAsset asset : series.assets()) {
            Num price = prices.get(asset);
            Num currentNotional = price.multipliedBy(holdings.get(asset));
            Num targetNotional = portfolioValue.multipliedBy(targetWeight(asset));
            Num deltaNotional = targetNotional.minus(currentNotional);
            Num gross = deltaNotional.isPositive() ? deltaNotional : series.numFactory().zero();
            buyGrossByAsset.put(asset, gross);
            if (gross.isPositive()) {
                Num amount = gross.dividedBy(price);
                totalGross = totalGross.plus(gross);
                totalCost = totalCost.plus(transactionCostModel.calculate(price, amount));
            }
        }

        return new BuyPlan(Map.copyOf(buyGrossByAsset), totalGross.plus(totalCost));
    }

    private Map<PortfolioAsset, Num> zeroHoldings() {
        Map<PortfolioAsset, Num> holdings = new LinkedHashMap<>();
        Num zero = series.numFactory().zero();
        for (PortfolioAsset asset : series.assets()) {
            holdings.put(asset, zero);
        }
        return Map.copyOf(holdings);
    }

    private Map<PortfolioAsset, Num> pricesAt(int index) {
        Map<PortfolioAsset, Num> prices = new LinkedHashMap<>();
        for (PortfolioAsset asset : series.assets()) {
            Num price = requirePositive(series.getClosePrice(asset, index), "close price for " + asset);
            prices.put(asset, price);
        }
        return Map.copyOf(prices);
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
        return Map.copyOf(normalizedWeights);
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

    private record RebalanceState(Num cash, Map<PortfolioAsset, Num> holdings, Num transactionCost, Num turnover) {
    }

    private record BuyPlan(Map<PortfolioAsset, Num> buyGrossByAsset, Num requiredCash) {
    }
}
