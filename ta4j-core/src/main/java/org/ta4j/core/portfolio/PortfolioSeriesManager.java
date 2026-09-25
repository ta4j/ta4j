/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.portfolio.PortfolioSnapshot.RebalanceStatus;

/**
 * Runs deterministic static target-weight backtests over a
 * {@link PortfolioSeries}.
 *
 * <p>
 * Like ta4j's single-series {@code BarSeriesManager}, the constructor owns the
 * series-level configuration (data and transaction costs) while each
 * {@code run(...)} call supplies the allocation, starting capital, and
 * rebalance schedule, which makes side-by-side comparisons natural:
 * </p>
 *
 * <pre>{@code
 * PortfolioSeriesManager manager = new PortfolioSeriesManager(new PortfolioSeries(spy, tlt),
 *         new LinearTransactionCostModel(0.001));
 * PortfolioExecutionResult buyAndHold = manager.run(new PortfolioAllocation(Map.of("SPY", 0.6, "TLT", 0.4)), 10_000);
 * PortfolioExecutionResult monthly = manager.run(new PortfolioAllocation(Map.of("SPY", 0.6, "TLT", 0.4)), 10_000,
 *         RebalancePolicy.firstBarOf(ChronoUnit.MONTHS, ZoneOffset.UTC));
 * }</pre>
 *
 * <p>
 * <b>Accounting model.</b> The portfolio starts with the initial cash only and
 * trades exclusively on bars selected by the {@link RebalancePolicy}. Trades
 * execute at the aligned close price with fractional, long-only units, and all
 * prices share one quote currency. On a rebalance the manager solves for the
 * post-cost portfolio value, so every asset reaches its target weight and cash
 * reaches the allocation's cash weight after transaction costs are paid. Cash
 * never becomes negative: sells that release cash execute first, sells whose
 * costs consume cash next, and buys are scaled down uniformly when cash cannot
 * cover them. Trades smaller than one part per billion of portfolio value are
 * treated as already at target, so numerical residue never triggers fees. Every
 * snapshot reports whether its rebalance {@link RebalanceStatus completed, was
 * partial, or was skipped}.
 * </p>
 *
 * <p>
 * Transaction costs are computed per trade with
 * {@link CostModel#calculate(Num, Num)} (price and units). The post-cost solve
 * is exact for proportional models; for fixed or minimum fees it picks the
 * largest post-cost value found, always considering the option of leaving the
 * current holdings untouched.
 * </p>
 *
 * @since 0.25.1
 */
public final class PortfolioSeriesManager {

    private static final int MAX_SOLVER_ITERATIONS = 200;

    private final PortfolioSeries series;
    private final CostModel transactionCostModel;

    /**
     * Creates a manager with zero transaction costs.
     *
     * @param series aligned portfolio series
     * @since 0.25.1
     */
    public PortfolioSeriesManager(PortfolioSeries series) {
        this(series, new ZeroCostModel());
    }

    /**
     * Creates a manager.
     *
     * @param series               aligned portfolio series
     * @param transactionCostModel transaction cost model applied to every trade
     * @since 0.25.1
     */
    public PortfolioSeriesManager(PortfolioSeries series, CostModel transactionCostModel) {
        this.series = Objects.requireNonNull(series, "series");
        this.transactionCostModel = Objects.requireNonNull(transactionCostModel, "transactionCostModel");
    }

    /**
     * @return managed portfolio series
     * @since 0.25.1
     */
    public PortfolioSeries getPortfolioSeries() {
        return series;
    }

    /**
     * @return transaction cost model
     * @since 0.25.1
     */
    public CostModel getTransactionCostModel() {
        return transactionCostModel;
    }

    /**
     * Runs a buy-and-hold portfolio: one investment at the first aligned bar, no
     * later rebalances.
     *
     * @param allocation  target allocation
     * @param initialCash positive starting cash
     * @return execution result
     * @since 0.25.1
     */
    public PortfolioExecutionResult run(PortfolioAllocation allocation, Num initialCash) {
        return run(allocation, initialCash, RebalancePolicy.atStart());
    }

    /**
     * Runs a buy-and-hold portfolio: one investment at the first aligned bar, no
     * later rebalances.
     *
     * @param allocation  target allocation
     * @param initialCash positive starting cash
     * @return execution result
     * @since 0.25.1
     */
    public PortfolioExecutionResult run(PortfolioAllocation allocation, Number initialCash) {
        return run(allocation, initialCash, RebalancePolicy.atStart());
    }

    /**
     * Runs a portfolio that trades toward its targets on the bars selected by
     * {@code rebalancePolicy}. The policy also schedules the initial investment:
     * the portfolio holds only cash until the first selected bar.
     *
     * @param allocation      target allocation
     * @param initialCash     positive starting cash
     * @param rebalancePolicy rebalance schedule
     * @return execution result
     * @since 0.25.1
     */
    public PortfolioExecutionResult run(PortfolioAllocation allocation, Number initialCash,
            RebalancePolicy rebalancePolicy) {
        Objects.requireNonNull(initialCash, "initialCash");
        return run(allocation, series.numFactory().numOf(initialCash), rebalancePolicy);
    }

    /**
     * Runs a portfolio that trades toward its targets on the bars selected by
     * {@code rebalancePolicy}. The policy also schedules the initial investment:
     * the portfolio holds only cash until the first selected bar.
     *
     * @param allocation      target allocation
     * @param initialCash     positive starting cash
     * @param rebalancePolicy rebalance schedule
     * @return execution result
     * @since 0.25.1
     */
    public PortfolioExecutionResult run(PortfolioAllocation allocation, Num initialCash,
            RebalancePolicy rebalancePolicy) {
        return new Execution(allocation, initialCash, rebalancePolicy).run();
    }

    /** One run's mutable state; assets are addressed by portfolio position. */
    private final class Execution {

        private final PortfolioAllocation allocation;
        private final Num initialCash;
        private final RebalancePolicy rebalancePolicy;
        private final NumFactory numFactory;
        private final List<String> assets;
        private final Num[] targetWeights;
        private final Num zero;

        private Execution(PortfolioAllocation allocation, Num initialCash, RebalancePolicy rebalancePolicy) {
            this.allocation = Objects.requireNonNull(allocation, "allocation");
            this.rebalancePolicy = Objects.requireNonNull(rebalancePolicy, "rebalancePolicy");
            this.numFactory = series.numFactory();
            this.assets = series.getAssets();
            this.zero = numFactory.zero();
            this.initialCash = series.toPortfolioNum(Objects.requireNonNull(initialCash, "initialCash"));
            if (!Num.isFinite(this.initialCash) || this.initialCash.isNegativeOrZero()) {
                throw new IllegalArgumentException("initialCash must be finite and > 0 but was " + initialCash);
            }
            for (String asset : allocation.getTargetWeights().keySet()) {
                if (!assets.contains(asset)) {
                    throw new IllegalArgumentException(
                            "allocation asset is not in the portfolio series: " + asset + " (assets: " + assets + ")");
                }
            }
            this.targetWeights = new Num[assets.size()];
            for (int position = 0; position < assets.size(); position++) {
                targetWeights[position] = series.toPortfolioNum(allocation.getTargetWeight(assets.get(position)));
            }
        }

        private PortfolioExecutionResult run() {
            int assetCount = assets.size();
            Num[] units = new Num[assetCount];
            Arrays.fill(units, zero);
            Map<String, Num> holdings = toMap(units);
            Num cash = initialCash;
            Num previousValue = initialCash;
            List<PortfolioSnapshot> snapshots = new ArrayList<>(series.getBarCount());

            for (int index = 0; index < series.getBarCount(); index++) {
                Num[] prices = pricesAt(index);
                Num preTradeValue = portfolioValue(cash, units, prices);
                Num transactionCost = zero;
                Num tradedNotional = zero;
                RebalanceStatus status = RebalanceStatus.NOT_SCHEDULED;

                if (rebalancePolicy.shouldRebalance(series, index)) {
                    Rebalance rebalance = new Rebalance(prices, units, cash, preTradeValue);
                    rebalance.execute();
                    units = rebalance.units;
                    cash = rebalance.cash;
                    transactionCost = rebalance.transactionCost;
                    tradedNotional = rebalance.tradedNotional;
                    status = rebalance.status();
                    holdings = toMap(units);
                }

                Num portfolioValue = portfolioValue(cash, units, prices);
                Num periodReturn = previousValue.isZero() ? zero
                        : portfolioValue.minus(previousValue).dividedBy(previousValue);
                Num turnover = preTradeValue.isZero() ? zero : tradedNotional.dividedBy(preTradeValue);
                snapshots.add(new PortfolioSnapshot(index, series.getEndTimes().get(index), toMap(prices), holdings,
                        cash, portfolioValue, periodReturn, transactionCost, tradedNotional, turnover, status));
                previousValue = portfolioValue;
            }

            return new PortfolioExecutionResult(series, allocation, initialCash, snapshots);
        }

        private Num[] pricesAt(int index) {
            Num[] prices = new Num[assets.size()];
            for (int position = 0; position < prices.length; position++) {
                Num price = series.closePrice(position, index);
                if (!Num.isFinite(price) || price.isNegativeOrZero()) {
                    throw new IllegalArgumentException("close price for " + assets.get(position) + " at "
                            + series.getEndTimes().get(index) + " must be finite and > 0 but was " + price);
                }
                prices[position] = price;
            }
            return prices;
        }

        private Num portfolioValue(Num cash, Num[] units, Num[] prices) {
            Num value = cash;
            for (int position = 0; position < units.length; position++) {
                value = value.plus(prices[position].multipliedBy(units[position]));
            }
            return value;
        }

        private Map<String, Num> toMap(Num[] values) {
            Map<String, Num> map = new LinkedHashMap<>();
            for (int position = 0; position < values.length; position++) {
                map.put(assets.get(position), values[position]);
            }
            return map;
        }

        /**
         * Costs of trading {@code gross} notional; trades within the no-trade band are
         * free because they are never executed.
         */
        private Num tradeCost(Num price, Num gross, Num noTradeBand) {
            if (gross.isLessThanOrEqual(noTradeBand)) {
                return zero;
            }
            Num cost = transactionCostModel.calculate(price, gross.dividedBy(price));
            cost = series.toPortfolioNum(Objects.requireNonNull(cost, "transaction cost"));
            if (!Num.isFinite(cost) || cost.isNegative()) {
                throw new IllegalArgumentException("transaction cost must be finite and >= 0 but was " + cost);
            }
            return cost;
        }

        /**
         * Bisection midpoint, or {@code null} once the interval is below the tolerance
         * or numeric precision can no longer split it.
         */
        private Num midpoint(Num low, Num high, Num tolerance) {
            if (high.minus(low).isLessThanOrEqual(tolerance)) {
                return null;
            }
            Num mid = low.plus(high).dividedBy(numFactory.two());
            return mid.isEqual(low) || mid.isEqual(high) ? null : mid;
        }

        /** Trades one aligned bar toward the target weights. */
        private final class Rebalance {

            private final Num[] prices;
            private final Num[] startUnits;
            private final Num preTradeValue;
            private final Num noTradeBand;
            private final Num solverTolerance;
            private final Num[] units;
            private Num cash;
            private Num transactionCost = zero;
            private Num tradedNotional = zero;

            private Rebalance(Num[] prices, Num[] units, Num cash, Num preTradeValue) {
                this.prices = prices;
                this.startUnits = units;
                this.units = units.clone();
                this.cash = cash;
                this.preTradeValue = preTradeValue;
                // Scale-aware tolerances: relative to portfolio value, never absolute.
                this.solverTolerance = preTradeValue.multipliedBy(numFactory.epsilon());
                this.noTradeBand = solverTolerance.multipliedBy(numFactory.thousand());
            }

            private void execute() {
                if (preTradeValue.isZero()) {
                    return;
                }
                Num targetValue = postCostTargetValue();
                if (targetValue == null) {
                    return;
                }
                Num[] deltas = new Num[units.length];
                List<Integer> sells = new ArrayList<>();
                List<Integer> buys = new ArrayList<>();
                for (int position = 0; position < units.length; position++) {
                    deltas[position] = targetWeights[position].multipliedBy(targetValue).minus(notional(position));
                    if (deltas[position].abs().isGreaterThan(noTradeBand)) {
                        (deltas[position].isNegative() ? sells : buys).add(position);
                    }
                }

                // Sells that release cash cannot fail and fund everything else, so they
                // go first; fee-dominated sells follow in ascending cash need. Ties
                // break by asset name, never by presentation order.
                List<Integer> cashConsumingSells = new ArrayList<>();
                for (int position : sells) {
                    Num gross = deltas[position].abs();
                    if (gross.isGreaterThanOrEqual(tradeCost(prices[position], gross, noTradeBand))) {
                        trade(position, gross.negate(), true);
                    } else {
                        cashConsumingSells.add(position);
                    }
                }
                cashConsumingSells.sort(Comparator.comparing((Integer position) -> {
                    Num gross = deltas[position].abs();
                    return tradeCost(prices[position], gross, noTradeBand).minus(gross);
                }).thenComparing(assets::get));
                for (int position : cashConsumingSells) {
                    sell(position, deltas[position].abs());
                }

                buy(buys, deltas);
                if (cash.isNegative()) {
                    // Affordability checks keep exact cash >= 0; only summation-order
                    // rounding can land a hair below zero.
                    if (cash.abs().isGreaterThan(solverTolerance)) {
                        throw new IllegalStateException("rebalance produced negative cash: " + cash);
                    }
                    cash = zero;
                }
            }

            /** Compares achieved with target weights of the post-trade value. */
            private RebalanceStatus status() {
                Num value = portfolioValue(cash, units, prices);
                for (int position = 0; position < units.length; position++) {
                    Num achieved = prices[position].multipliedBy(units[position]);
                    if (achieved.minus(targetWeights[position].multipliedBy(value)).abs().isGreaterThan(noTradeBand)) {
                        return tradedNotional.isZero() ? RebalanceStatus.SKIPPED : RebalanceStatus.PARTIAL;
                    }
                }
                return RebalanceStatus.COMPLETED;
            }

            /**
             * Largest post-cost portfolio value {@code T} with
             * {@code T + cost(T) <= preTradeValue}, where {@code cost(T)} is the cost of
             * trading every asset to {@code weight * T}; {@code null} when no positive
             * value is feasible, so costs would consume the whole portfolio.
             */
            private Num postCostTargetValue() {
                if (rebalanceCost(preTradeValue).isZero()) {
                    return preTradeValue;
                }
                Num low = zero;
                Num high = preTradeValue;
                for (int iteration = 0; iteration < MAX_SOLVER_ITERATIONS; iteration++) {
                    Num mid = midpoint(low, high, solverTolerance);
                    if (mid == null) {
                        break;
                    }
                    if (mid.plus(rebalanceCost(mid)).isLessThanOrEqual(preTradeValue)) {
                        low = mid;
                    } else {
                        high = mid;
                    }
                }
                // Leaving holdings untouched is a feasible candidate that bisection can
                // miss when fixed fees make feasibility discontinuous.
                Num holdValue = holdValue();
                if (holdValue != null && holdValue.isGreaterThan(low)) {
                    return holdValue;
                }
                return low.isZero() ? null : low;
            }

            private Num rebalanceCost(Num targetValue) {
                Num totalCost = zero;
                for (int position = 0; position < units.length; position++) {
                    Num gross = targetWeights[position].multipliedBy(targetValue).minus(notional(position)).abs();
                    totalCost = totalCost.plus(tradeCost(prices[position], gross, noTradeBand));
                }
                return totalCost;
            }

            /**
             * Post-cost value at which the current holdings already match the targets, or
             * {@code null} when holding is not a consistent option.
             */
            private Num holdValue() {
                Num investedValue = zero;
                Num totalWeight = zero;
                for (int position = 0; position < units.length; position++) {
                    investedValue = investedValue.plus(notional(position));
                    totalWeight = totalWeight.plus(targetWeights[position]);
                }
                if (totalWeight.isZero()) {
                    return null;
                }
                Num holdValue = investedValue.dividedBy(totalWeight);
                if (holdValue.isGreaterThan(preTradeValue)) {
                    return null;
                }
                for (int position = 0; position < units.length; position++) {
                    Num gross = targetWeights[position].multipliedBy(holdValue).minus(notional(position)).abs();
                    if (gross.isGreaterThan(noTradeBand)) {
                        return null;
                    }
                }
                return holdValue;
            }

            private void sell(int position, Num desiredGross) {
                Num price = prices[position];
                if (isSellAffordable(price, desiredGross)) {
                    trade(position, desiredGross.negate(), true);
                    return;
                }
                Num low = zero;
                Num high = desiredGross;
                for (int iteration = 0; iteration < MAX_SOLVER_ITERATIONS; iteration++) {
                    Num mid = midpoint(low, high, solverTolerance);
                    if (mid == null) {
                        break;
                    }
                    if (isSellAffordable(price, mid)) {
                        low = mid;
                    } else {
                        high = mid;
                    }
                }
                if (low.isGreaterThan(noTradeBand)) {
                    trade(position, low.negate(), false);
                }
            }

            private boolean isSellAffordable(Num price, Num gross) {
                return cash.plus(gross).minus(tradeCost(price, gross, noTradeBand)).isPositiveOrZero();
            }

            /**
             * Buys every underweight asset, scaling all buys by one common factor when cash
             * cannot cover them so no asset is favored by its position.
             */
            private void buy(List<Integer> buys, Num[] deltas) {
                if (buys.isEmpty()) {
                    return;
                }
                Num one = numFactory.one();
                Num scale = one;
                if (buyCashNeed(buys, deltas, one).isGreaterThan(cash)) {
                    Num low = zero;
                    Num high = one;
                    for (int iteration = 0; iteration < MAX_SOLVER_ITERATIONS; iteration++) {
                        Num mid = midpoint(low, high, numFactory.epsilon());
                        if (mid == null) {
                            break;
                        }
                        if (buyCashNeed(buys, deltas, mid).isLessThanOrEqual(cash)) {
                            low = mid;
                        } else {
                            high = mid;
                        }
                    }
                    scale = low;
                }
                for (int position : buys) {
                    Num gross = deltas[position].multipliedBy(scale);
                    if (gross.isGreaterThan(noTradeBand)) {
                        trade(position, gross, false);
                    }
                }
            }

            private Num buyCashNeed(List<Integer> buys, Num[] deltas, Num scale) {
                Num need = zero;
                for (int position : buys) {
                    Num gross = deltas[position].multipliedBy(scale);
                    need = need.plus(gross).plus(tradeCost(prices[position], gross, noTradeBand));
                }
                return need;
            }

            /**
             * Applies a signed notional trade; a complete sale of a zero-target asset lands
             * exactly on zero units instead of rounding residue.
             */
            private void trade(int position, Num signedGross, boolean fullSale) {
                Num price = prices[position];
                Num gross = signedGross.abs();
                Num cost = tradeCost(price, gross, noTradeBand);
                Num targetUnits = startUnits[position].plus(signedGross.dividedBy(price));
                units[position] = fullSale && targetWeights[position].isZero() ? zero : targetUnits;
                cash = cash.minus(signedGross).minus(cost);
                transactionCost = transactionCost.plus(cost);
                tradedNotional = tradedNotional.plus(gross);
            }

            private Num notional(int position) {
                return prices[position].multipliedBy(startUnits[position]);
            }
        }
    }
}
