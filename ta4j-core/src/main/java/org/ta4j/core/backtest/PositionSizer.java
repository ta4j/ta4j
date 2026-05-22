/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Sizes new backtest entries from the current execution context.
 *
 * <p>
 * Implementations return the amount used to open a new position. Exits close
 * the currently open amount and do not call the sizer again.
 * </p>
 * <p>
 * When used with {@link BacktestExecutor} methods that evaluate strategies in
 * parallel, implementations may be called concurrently and should be
 * thread-safe.
 * </p>
 *
 * @since 0.22.7
 */
@FunctionalInterface
public interface PositionSizer {

    /**
     * Returns the amount used to open a new position.
     *
     * @param context entry sizing context
     * @return amount used for entry execution
     * @since 0.22.7
     */
    Num amount(Context context);

    /**
     * Returns a position sizer that opens one unit.
     *
     * @return fixed unit position sizer
     * @since 0.22.7
     */
    static PositionSizer fixed() {
        return context -> context.numFactory().one();
    }

    /**
     * Returns a position sizer that opens a fixed numeric amount.
     *
     * @param amount fixed amount
     * @return fixed amount position sizer
     * @since 0.22.7
     */
    static PositionSizer fixed(Number amount) {
        validatePositiveNumber(amount, "amount");
        return context -> context.numOf(amount);
    }

    /**
     * Returns a position sizer that opens a fixed {@link Num} amount.
     *
     * @param amount fixed amount
     * @return fixed amount position sizer
     * @since 0.22.7
     */
    static PositionSizer fixed(Num amount) {
        validatePositiveNum(amount, "amount");
        return context -> amount;
    }

    /**
     * Returns a position sizer that invests the maximum affordable amount from the
     * current realized balance.
     *
     * @param principal starting balance
     * @return balance-based position sizer
     * @since 0.22.7
     */
    static PositionSizer balance(Number principal) {
        return balance(principal, (context, balance) -> context.maxAffordableAmount(balance));
    }

    /**
     * Returns a position sizer that derives an entry amount from the current
     * realized balance.
     *
     * @param principal starting balance
     * @param rule      custom balance sizing rule
     * @return balance-based position sizer
     * @since 0.22.7
     */
    static PositionSizer balance(Number principal, BalanceRule rule) {
        validatePositiveNumber(principal, "principal");
        Objects.requireNonNull(rule, "rule");
        return context -> {
            Num balance = context.currentBalance(principal);
            return rule.amount(context, balance);
        };
    }

    /**
     * Returns a full-Kelly position sizer.
     *
     * @param principal      starting balance
     * @param winProbability probability of a winning position, in {@code (0, 1)}
     * @param payoffRatio    average win divided by average loss, must be positive
     * @return Kelly position sizer
     * @since 0.22.7
     */
    static PositionSizer kelly(Number principal, Number winProbability, Number payoffRatio) {
        return kelly(principal, winProbability, payoffRatio, 1);
    }

    /**
     * Returns a Kelly position sizer with an explicit coefficient.
     *
     * <p>
     * The coefficient multiplies the Kelly fraction, so {@code 0.5} is half Kelly
     * and {@code 1.2} is 120% Kelly.
     * </p>
     *
     * @param principal      starting balance
     * @param winProbability probability of a winning position, in {@code (0, 1)}
     * @param payoffRatio    average win divided by average loss, must be positive
     * @param coefficient    multiplier applied to the Kelly fraction, must be
     *                       positive
     * @return Kelly position sizer
     * @since 0.22.7
     */
    static PositionSizer kelly(Number principal, Number winProbability, Number payoffRatio, Number coefficient) {
        validatePositiveNumber(principal, "principal");
        validateProbability(winProbability, "winProbability");
        validatePositiveNumber(payoffRatio, "payoffRatio");
        validatePositiveNumber(coefficient, "coefficient");
        return context -> {
            Num one = context.numFactory().one();
            Num probability = context.numOf(winProbability);
            Num lossProbability = one.minus(probability);
            Num ratio = context.numOf(payoffRatio);
            Num multiplier = context.numOf(coefficient);
            Num kellyFraction = probability.minus(lossProbability.dividedBy(ratio)).multipliedBy(multiplier);
            if (!kellyFraction.isPositive()) {
                throw new IllegalArgumentException("Kelly fraction must be positive");
            }
            Num budget = context.currentBalance(principal).multipliedBy(kellyFraction);
            return context.maxAffordableAmount(budget);
        };
    }

    private static void validatePositiveNumber(Number value, String name) {
        Objects.requireNonNull(value, name);
        double doubleValue = value.doubleValue();
        if (!Double.isFinite(doubleValue) || doubleValue <= 0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void validateProbability(Number value, String name) {
        Objects.requireNonNull(value, name);
        double doubleValue = value.doubleValue();
        if (!Double.isFinite(doubleValue) || doubleValue <= 0 || doubleValue >= 1) {
            throw new IllegalArgumentException(name + " must be finite and in (0, 1)");
        }
    }

    private static void validatePositiveNum(Num value, String name) {
        validateFiniteNum(value, name);
        if (!value.isPositive()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void validateFiniteNum(Num value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isNaN() || !Double.isFinite(value.doubleValue())) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    /**
     * Derives an entry amount from the current realized balance.
     *
     * @since 0.22.7
     */
    @FunctionalInterface
    public interface BalanceRule {
        /**
         * Returns the entry amount for the given balance.
         *
         * @param context current sizing context
         * @param balance current realized balance
         * @return amount used for entry execution
         * @since 0.22.7
         */
        Num amount(Context context, Num balance);
    }

    /**
     * Context available when sizing a new entry.
     *
     * @param signalIndex          bar index where the strategy emitted an operation
     * @param entryIndex           estimated execution bar index
     * @param entryPrice           estimated entry price
     * @param strategy             strategy being evaluated
     * @param barSeries            backtested bar series
     * @param tradeType            entry trade type
     * @param tradingRecord        trading record for the current run
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     * @since 0.22.7
     */
    public record Context(int signalIndex, int entryIndex, Num entryPrice, Strategy strategy, BarSeries barSeries,
            TradeType tradeType, TradingRecord tradingRecord, CostModel transactionCostModel,
            CostModel holdingCostModel) {

        private static final int MAX_AFFORDABLE_SEARCH_ITERATIONS = 80;

        /**
         * Creates an entry sizing context.
         *
         * @since 0.22.7
         */
        public Context {
            Objects.requireNonNull(entryPrice, "entryPrice");
            Objects.requireNonNull(strategy, "strategy");
            Objects.requireNonNull(barSeries, "barSeries");
            Objects.requireNonNull(tradeType, "tradeType");
            Objects.requireNonNull(tradingRecord, "tradingRecord");
            Objects.requireNonNull(transactionCostModel, "transactionCostModel");
            Objects.requireNonNull(holdingCostModel, "holdingCostModel");
        }

        /**
         * @return the number factory backing the series
         * @since 0.22.7
         */
        public NumFactory numFactory() {
            return barSeries.numFactory();
        }

        /**
         * Converts a {@link Number} with the series number factory.
         *
         * @param number number to convert
         * @return converted number
         * @since 0.22.7
         */
        public Num numOf(Number number) {
            return numFactory().numOf(number);
        }

        /**
         * Returns the realized balance from a starting principal.
         *
         * @param principal starting principal
         * @return current realized balance
         * @since 0.22.7
         */
        public Num currentBalance(Number principal) {
            return currentBalance(numOf(principal));
        }

        /**
         * Returns the realized balance from a starting principal.
         *
         * @param principal starting principal
         * @return current realized balance
         * @since 0.22.7
         */
        public Num currentBalance(Num principal) {
            validatePositiveNum(principal, "principal");
            Num balance = principal;
            for (Position position : tradingRecord.getPositions()) {
                balance = balance.plus(position.getProfit());
            }
            return balance;
        }

        /**
         * Returns estimated entry cash required for an amount, including entry
         * transaction costs.
         *
         * @param amount candidate entry amount
         * @return estimated entry cost
         * @since 0.22.7
         */
        public Num entryCost(Num amount) {
            validateFiniteNum(amount, "amount");
            if (amount.isNegative()) {
                throw new IllegalArgumentException("amount must not be negative");
            }
            return entryPrice.multipliedBy(amount).plus(transactionCostModel.calculate(entryPrice, amount));
        }

        /**
         * Returns the largest affordable amount for the provided budget.
         *
         * @param budget cash available for entry price and transaction costs
         * @return largest amount affordable by the budget, or zero when none is
         *         affordable
         * @since 0.22.7
         */
        public Num maxAffordableAmount(Num budget) {
            validateFiniteNum(budget, "budget");
            validatePositiveNum(entryPrice, "entryPrice");
            Num zero = numFactory().zero();
            if (!budget.isPositive()) {
                return zero;
            }

            Num high = budget.dividedBy(entryPrice);
            if (!high.isPositive()) {
                return zero;
            }
            if (entryCost(high).isLessThanOrEqual(budget)) {
                return high;
            }

            Num low = zero;
            Num two = numFactory().two();
            for (int i = 0; i < MAX_AFFORDABLE_SEARCH_ITERATIONS; i++) {
                Num mid = low.plus(high).dividedBy(two);
                if (mid.isZero() || mid.isEqual(low) || mid.isEqual(high)) {
                    break;
                }
                if (entryCost(mid).isLessThanOrEqual(budget)) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            return low;
        }
    }
}
