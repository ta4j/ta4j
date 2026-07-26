/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;

/**
 * Runs deterministic static target-weight backtests over a
 * {@link PortfolioSeries}.
 *
 * <p>
 * Like ta4j's single-series manager, the constructor owns series-level
 * execution configuration while each {@code run(...)} call supplies the
 * allocation and starting capital. Rebalances use fractional units, solve
 * target notionals against post-cost portfolio value, and never allow costs to
 * make cash negative.
 * </p>
 *
 * @since 0.23.1
 */
public final class PortfolioSeriesManager {

    private final PortfolioSeries series;
    private final CostModel transactionCostModel;

    /**
     * Creates a manager with zero transaction costs.
     *
     * @param series aligned portfolio series
     * @since 0.23.1
     */
    public PortfolioSeriesManager(PortfolioSeries series) {
        this(series, new ZeroCostModel());
    }

    /**
     * Creates a manager.
     *
     * @param series               aligned portfolio series
     * @param transactionCostModel transaction cost model for rebalance trades
     * @since 0.23.1
     */
    public PortfolioSeriesManager(PortfolioSeries series, CostModel transactionCostModel) {
        this.series = Objects.requireNonNull(series, "series");
        this.transactionCostModel = Objects.requireNonNull(transactionCostModel, "transactionCostModel");
    }

    /**
     * @return managed portfolio series
     * @since 0.23.1
     */
    public PortfolioSeries getPortfolioSeries() {
        return series;
    }

    /**
     * @return transaction cost model
     * @since 0.23.1
     */
    public CostModel getTransactionCostModel() {
        return transactionCostModel;
    }

    /**
     * Runs a portfolio with one rebalance at the first aligned bar.
     *
     * @param allocation  target allocation
     * @param initialCash starting cash
     * @return execution result
     * @since 0.23.1
     */
    public PortfolioExecutionResult run(PortfolioAllocation allocation, Num initialCash) {
        return run(allocation, initialCash, RebalancePolicy.atStart());
    }

    /**
     * Runs a portfolio with an explicit rebalance policy.
     *
     * @param allocation      target allocation
     * @param initialCash     starting cash
     * @param rebalancePolicy rebalance policy
     * @return execution result
     * @since 0.23.1
     */
    public PortfolioExecutionResult run(PortfolioAllocation allocation, Num initialCash,
            RebalancePolicy rebalancePolicy) {
        return new Execution(series, transactionCostModel, allocation, initialCash, rebalancePolicy).run();
    }

    private static final class Execution {

        private final PortfolioSeries series;
        private final CostModel transactionCostModel;
        private final PortfolioAllocation allocation;
        private final Map<String, Num> targetWeights;
        private final Num initialCash;
        private final RebalancePolicy rebalancePolicy;

        private Execution(PortfolioSeries series, CostModel transactionCostModel, PortfolioAllocation allocation,
                Num initialCash, RebalancePolicy rebalancePolicy) {
            this.series = series;
            this.transactionCostModel = transactionCostModel;
            this.allocation = Objects.requireNonNull(allocation, "allocation");
            this.initialCash = requirePositive(
                    series.toPortfolioNum(Objects.requireNonNull(initialCash, "initialCash")), "initialCash");
            this.rebalancePolicy = Objects.requireNonNull(rebalancePolicy, "rebalancePolicy");
            requireAllocationAssetsInSeries();
            this.targetWeights = normalizedTargetWeights();
        }

        private PortfolioExecutionResult run() {
            Map<String, Num> holdings = zeroHoldings();
            Num cash = initialCash;
            Num previousValue = initialCash;
            List<PortfolioSnapshot> snapshots = new ArrayList<>(series.getBarCount());

            for (int index = series.getBeginIndex(); index <= series.getEndIndex(); index++) {
                Map<String, Num> prices = pricesAt(index);
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
                Instant endTime = series.getEndTimes().get(index);
                snapshots.add(new PortfolioSnapshot(index, endTime, prices, holdings, cash, portfolioValue,
                        periodReturn, transactionCost, turnover));
                previousValue = portfolioValue;
            }

            return new PortfolioExecutionResult(series, allocation, initialCash, snapshots);
        }

        private RebalanceState rebalance(Num cash, Map<String, Num> holdings, Map<String, Num> prices) {
            Num portfolioValue = portfolioValue(cash, holdings, prices);
            Num targetPortfolioValue = postCostPortfolioValue(holdings, prices, portfolioValue);
            Map<String, Num> targetNotionals = targetNotionals(targetPortfolioValue);
            Map<String, Num> nextHoldings = new LinkedHashMap<>(holdings);
            Num nextCash = cash;
            Num totalTransactionCost = series.numFactory().zero();
            Num turnover = series.numFactory().zero();

            for (String asset : series.getAssets()) {
                Num price = prices.get(asset);
                Num currentUnits = nextHoldings.get(asset);
                Num currentNotional = price.multipliedBy(currentUnits);
                Num deltaNotional = targetNotionals.get(asset).minus(currentNotional);
                if (deltaNotional.isNegative()) {
                    Num gross = affordableSellGross(price, deltaNotional.abs(), nextCash);
                    if (gross.isNegativeOrZero()) {
                        continue;
                    }
                    Num cost = transactionCost(price, gross);
                    Num amount = gross.dividedBy(price);
                    nextCash = nextCash.plus(gross).minus(cost);
                    nextHoldings.put(asset, currentUnits.minus(amount));
                    totalTransactionCost = totalTransactionCost.plus(cost);
                    turnover = turnover.plus(gross);
                }
            }

            for (String asset : series.getAssets()) {
                Num price = prices.get(asset);
                Num currentUnits = nextHoldings.get(asset);
                Num currentNotional = price.multipliedBy(currentUnits);
                Num desiredGross = targetNotionals.get(asset).minus(currentNotional);
                if (desiredGross.isNegativeOrZero()) {
                    continue;
                }

                desiredGross = affordableBuyGross(price, desiredGross, nextCash);
                if (desiredGross.isNegativeOrZero()) {
                    continue;
                }

                Num amount = desiredGross.dividedBy(price);
                Num cost = transactionCost(price, desiredGross);
                nextCash = nextCash.minus(desiredGross.plus(cost));
                nextHoldings.put(asset, currentUnits.plus(amount));
                totalTransactionCost = totalTransactionCost.plus(cost);
                turnover = turnover.plus(desiredGross);
            }

            return new RebalanceState(nextCash, immutableMap(nextHoldings), totalTransactionCost, turnover);
        }

        private Num postCostPortfolioValue(Map<String, Num> holdings, Map<String, Num> prices,
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

        private Num rebalanceCost(Map<String, Num> holdings, Map<String, Num> prices, Num targetPortfolioValue) {
            Num totalCost = series.numFactory().zero();
            for (String asset : series.getAssets()) {
                Num price = prices.get(asset);
                Num currentNotional = price.multipliedBy(holdings.get(asset));
                Num targetNotional = targetPortfolioValue.multipliedBy(targetWeight(asset));
                Num gross = targetNotional.minus(currentNotional).abs();
                totalCost = totalCost.plus(transactionCost(price, gross));
            }
            return totalCost;
        }

        private Map<String, Num> targetNotionals(Num targetPortfolioValue) {
            Map<String, Num> targetNotionals = new LinkedHashMap<>();
            for (String asset : series.getAssets()) {
                targetNotionals.put(asset, targetPortfolioValue.multipliedBy(targetWeight(asset)));
            }
            return immutableMap(targetNotionals);
        }

        private Num affordableSellGross(Num price, Num desiredGross, Num cash) {
            if (desiredGross.isNegativeOrZero()) {
                return series.numFactory().zero();
            }
            if (isSellAffordable(price, desiredGross, cash)) {
                return desiredGross;
            }
            return affordableGrossByBisection(price, desiredGross, cash, false);
        }

        private Num affordableBuyGross(Num price, Num desiredGross, Num cash) {
            if (desiredGross.isNegativeOrZero() || cash.isNegativeOrZero()) {
                return series.numFactory().zero();
            }
            if (isBuyAffordable(price, desiredGross, cash)) {
                return desiredGross;
            }
            return affordableGrossByBisection(price, desiredGross, cash, true);
        }

        private Num affordableGrossByBisection(Num price, Num desiredGross, Num cash, boolean buy) {
            Num low = series.numFactory().zero();
            Num high = desiredGross;
            Num tolerance = valueTolerance(desiredGross);
            for (int iteration = 0; iteration < 64; iteration++) {
                Num mid = low.plus(high).dividedBy(series.numFactory().two());
                boolean affordable = buy ? isBuyAffordable(price, mid, cash) : isSellAffordable(price, mid, cash);
                if (affordable) {
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

        private boolean isSellAffordable(Num price, Num gross, Num cash) {
            return cash.plus(gross).minus(transactionCost(price, gross)).isPositiveOrZero();
        }

        private boolean isBuyAffordable(Num price, Num gross, Num cash) {
            return gross.plus(transactionCost(price, gross)).isLessThanOrEqual(cash);
        }

        private Num transactionCost(Num price, Num gross) {
            if (gross.isNegativeOrZero()) {
                return series.numFactory().zero();
            }
            Num cost = series.toPortfolioNum(Objects
                    .requireNonNull(transactionCostModel.calculate(price, gross.dividedBy(price)), "transaction cost"));
            if (!Num.isFinite(cost) || cost.isNegative()) {
                throw new IllegalArgumentException("transaction cost must be finite and >= 0");
            }
            return cost;
        }

        private Num valueTolerance(Num reference) {
            Num epsilon = series.numFactory().epsilon();
            return epsilon.max(reference.abs().multipliedBy(epsilon));
        }

        private Map<String, Num> zeroHoldings() {
            Map<String, Num> holdings = new LinkedHashMap<>();
            Num zero = series.numFactory().zero();
            for (String asset : series.getAssets()) {
                holdings.put(asset, zero);
            }
            return immutableMap(holdings);
        }

        private Map<String, Num> pricesAt(int index) {
            Map<String, Num> prices = new LinkedHashMap<>();
            for (String asset : series.getAssets()) {
                Num price = requirePositive(series.getClosePrice(asset, index), "close price for " + asset);
                prices.put(asset, price);
            }
            return immutableMap(prices);
        }

        private Num portfolioValue(Num cash, Map<String, Num> holdings, Map<String, Num> prices) {
            Num value = cash;
            for (String asset : series.getAssets()) {
                value = value.plus(prices.get(asset).multipliedBy(holdings.get(asset)));
            }
            return value;
        }

        private void requireAllocationAssetsInSeries() {
            for (String asset : allocation.getTargetWeights().keySet()) {
                if (!series.getAssets().contains(asset)) {
                    throw new IllegalArgumentException("allocation asset is not in the portfolio series: " + asset);
                }
            }
        }

        private Map<String, Num> normalizedTargetWeights() {
            Map<String, Num> normalizedWeights = new LinkedHashMap<>();
            for (String asset : series.getAssets()) {
                normalizedWeights.put(asset, series.toPortfolioNum(allocation.getTargetWeight(asset)));
            }
            return immutableMap(normalizedWeights);
        }

        private Num targetWeight(String asset) {
            return targetWeights.get(asset);
        }
    }

    private static Num requirePositive(Num value, String name) {
        Objects.requireNonNull(value, name);
        if (!Num.isFinite(value) || value.isNegativeOrZero()) {
            throw new IllegalArgumentException(name + " must be finite and > 0");
        }
        return value;
    }

    private static <K, V> Map<K, V> immutableMap(Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private record RebalanceState(Num cash, Map<String, Num> holdings, Num transactionCost, Num turnover) {
    }
}
