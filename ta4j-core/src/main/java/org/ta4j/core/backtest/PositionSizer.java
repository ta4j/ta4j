/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;
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
 * @since 0.22.9
 */
@FunctionalInterface
public interface PositionSizer {

    /**
     * Returns the amount used to open a new position.
     *
     * <p>
     * Implementations must return a non-negative, finite {@link Num} compatible
     * with {@link Context#numFactory()}. Zero skips the entry without submitting an
     * order; the run continues. Factory-created sizers validate their constructor
     * inputs eagerly; custom implementations are responsible for honoring this
     * contract when they are called.
     * </p>
     *
     * @param context entry sizing context
     * @return amount used for entry execution
     * @since 0.22.9
     */
    Num amount(Context context);

    /**
     * Multiplies this sizer's amount by the supplied leverage.
     *
     * <p>
     * With {@link #balance(Number)}, the balance acts as margin. Transaction fees
     * on the leveraged notional reduce profit; they are not reserved from margin.
     *
     * @param leverage positive, finite amount multiplier
     * @return a leveraged version of this sizer
     * @throws NullPointerException     if leverage is null
     * @throws IllegalArgumentException if leverage is not positive and finite
     * @since 0.28.1
     */
    default PositionSizer leveraged(Number leverage) {
        Number fixedLeverage = snapshotNumber(leverage, "leverage");
        return context -> amount(context).multipliedBy(context.numOf(fixedLeverage));
    }

    /**
     * Floors this sizer's amount to a multiple of the supplied quantity step.
     *
     * <p>
     * An amount smaller than one step becomes zero, except for the narrow
     * {@link DoubleNum} boundary tolerance described below. Apply this decorator
     * after every amount-changing decorator, such as {@link #leveraged(Number)}, to
     * round the final order quantity. Later decorators may change the amount and
     * break its alignment to the step.
     *
     * <p>
     * Quantization uses decimal values before converting the result with the
     * context's number factory. A {@code DoubleNum} amount is treated as aligned to
     * the next boundary if it is at most two representable double values and at
     * most one billionth of a step below that boundary. This corrects tiny
     * floating-point drift; other values are floored. A decimal result must
     * represent the rounded quantity exactly without exceeding the requested amount.
     *
     * @param quantityStep positive, finite quantity increment
     * @return a quantity-step-rounded version of this sizer
     * @throws NullPointerException     if the step is null
     * @throws IllegalArgumentException if the step is not positive and finite, or
     *                                  the rounded quantity cannot be represented
     *                                  safely by the decimal number factory
     * @since 0.28.1
     */
    default PositionSizer roundedDownTo(Number quantityStep) {
        Number fixedStep = snapshotNumber(quantityStep, "quantityStep");
        BigDecimal step = fixedStep instanceof BigDecimal decimal ? decimal : new BigDecimal(fixedStep.toString());
        BigDecimal maxDoubleBoundaryGap = step.movePointLeft(9);
        return context -> {
            Num rawAmount = amount(context);
            if (!Num.isFinite(rawAmount) || !rawAmount.isPositive()) {
                return rawAmount;
            }
            // Num division can round across a lot boundary before floor is applied.
            BigDecimal quantity = rawAmount.bigDecimalValue();
            BigDecimal remainder = quantity.remainder(step);
            if (remainder.signum() == 0) {
                return rawAmount;
            }
            BigDecimal rounded = quantity.subtract(remainder);
            if (rawAmount instanceof DoubleNum && step.subtract(remainder).compareTo(maxDoubleBoundaryGap) <= 0) {
                BigDecimal nextBoundary = rounded.add(step);
                // Num has no ulp operation, so compare adjacent double values directly.
                double value = rawAmount.doubleValue();
                double boundary = nextBoundary.doubleValue();
                if (Double.isFinite(boundary) && boundary > value && boundary <= Math.nextUp(Math.nextUp(value))) {
                    rounded = nextBoundary;
                }
            }
            Num result = context.numOf(rounded);
            if (result instanceof DecimalNum
                    && (result.bigDecimalValue().compareTo(rounded) != 0
                            || result.bigDecimalValue().compareTo(quantity) > 0)) {
                throw new IllegalArgumentException("Rounded amount cannot be represented by the decimal number factory");
            }
            return result;
        };
    }

    /**
     * Returns a position sizer that opens one unit.
     *
     * @return fixed unit position sizer
     * @since 0.22.9
     */
    static PositionSizer fixed() {
        return context -> context.numFactory().one();
    }

    /**
     * Returns a position sizer that opens a fixed numeric amount.
     *
     * @param amount fixed amount
     * @return fixed amount position sizer
     * @since 0.22.9
     */
    static PositionSizer fixed(Number amount) {
        Number fixedAmount = snapshotNumber(amount, "amount");
        return context -> context.numOf(fixedAmount);
    }

    /**
     * Returns a position sizer that opens a fixed {@link Num} amount.
     *
     * @param amount fixed amount
     * @return fixed amount position sizer
     * @since 0.22.9
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
     * @since 0.22.9
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
     * @since 0.22.9
     */
    static PositionSizer balance(Number principal, BalanceRule rule) {
        Number fixedPrincipal = snapshotNumber(principal, "principal");
        Objects.requireNonNull(rule, "rule");
        return context -> {
            Num balance = context.currentBalance(fixedPrincipal);
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
     * @since 0.22.9
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
     * @since 0.22.9
     */
    static PositionSizer kelly(Number principal, Number winProbability, Number payoffRatio, Number coefficient) {
        Number fixedPrincipal = snapshotNumber(principal, "principal");
        Number fixedWinProbability = snapshotNumber(winProbability, "winProbability");
        Number fixedPayoffRatio = snapshotNumber(payoffRatio, "payoffRatio");
        Number fixedCoefficient = snapshotNumber(coefficient, "coefficient");
        validateProbability(fixedWinProbability, "winProbability");
        validatePositiveNumber(fixedPayoffRatio, "payoffRatio");
        validatePositiveNumber(fixedCoefficient, "coefficient");
        validatePositiveKellyFraction(fixedWinProbability, fixedPayoffRatio, fixedCoefficient);
        return context -> {
            Num one = context.numFactory().one();
            Num probability = context.numOf(fixedWinProbability);
            Num lossProbability = one.minus(probability);
            Num ratio = context.numOf(fixedPayoffRatio);
            Num multiplier = context.numOf(fixedCoefficient);
            Num kellyFraction = probability.minus(lossProbability.dividedBy(ratio)).multipliedBy(multiplier);
            Num budget = context.currentBalance(fixedPrincipal).multipliedBy(kellyFraction);
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

    private static Number snapshotNumber(Number value, String name) {
        validatePositiveNumber(value, name);
        return value instanceof BigDecimal || value instanceof BigInteger ? value : Double.valueOf(value.doubleValue());
    }

    private static void validateProbability(Number value, String name) {
        Objects.requireNonNull(value, name);
        double doubleValue = value.doubleValue();
        if (!Double.isFinite(doubleValue) || doubleValue <= 0 || doubleValue >= 1) {
            throw new IllegalArgumentException(name + " must be finite and in (0, 1)");
        }
    }

    private static void validatePositiveKellyFraction(Number winProbability, Number payoffRatio, Number coefficient) {
        double probability = winProbability.doubleValue();
        double ratio = payoffRatio.doubleValue();
        double multiplier = coefficient.doubleValue();
        double kellyFraction = (probability - ((1 - probability) / ratio)) * multiplier;
        if (kellyFraction <= 0) {
            throw new IllegalArgumentException("Kelly fraction must be positive");
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
     * @since 0.22.9
     */
    @FunctionalInterface
    public interface BalanceRule {
        /**
         * Returns the entry amount for the given balance.
         *
         * @param context current sizing context
         * @param balance current realized balance
         * @return amount used for entry execution
         * @since 0.22.9
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
     * @param barSeries            backtested bar series; during a
     *                             {@link BarSeriesManager} run, a read-only view of
     *                             the caller's series that keeps its indexes and
     *                             ends at the run's last index
     * @param tradeType            entry trade type
     * @param tradingRecord        trading record for the current run
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     * @since 0.22.9
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "The context borrows the run's series view, "
            + "strategy, trading record and cost models so sizing observes the same bars the strategy rules and "
            + "execution models see; accessors expose them by contract.")
    public record Context(int signalIndex, int entryIndex, Num entryPrice, Strategy strategy, BarSeries barSeries,
            TradeType tradeType, TradingRecord tradingRecord, CostModel transactionCostModel,
            CostModel holdingCostModel) {

        private static final int MAX_AFFORDABLE_SEARCH_ITERATIONS = 80;

        /**
         * Creates an entry sizing context.
         *
         * @since 0.22.9
         */
        public Context {
            Objects.requireNonNull(entryPrice, "entryPrice");
            strategy = StrategySnapshots.copy(strategy);
            barSeries = Objects.requireNonNull(barSeries, "barSeries");
            Objects.requireNonNull(tradeType, "tradeType");
            Objects.requireNonNull(tradingRecord, "tradingRecord");
            Objects.requireNonNull(transactionCostModel, "transactionCostModel");
            Objects.requireNonNull(holdingCostModel, "holdingCostModel");
        }

        @Override
        public Strategy strategy() {
            return StrategySnapshots.copy(strategy);
        }

        /**
         * @return the number factory backing the series
         * @since 0.22.9
         */
        public NumFactory numFactory() {
            return barSeries.numFactory();
        }

        /**
         * Converts a {@link Number} with the series number factory.
         *
         * @param number number to convert
         * @return converted number
         * @since 0.22.9
         */
        public Num numOf(Number number) {
            return numFactory().numOf(number);
        }

        /**
         * Returns the realized balance from a starting principal.
         *
         * @param principal starting principal
         * @return current realized balance
         * @since 0.22.9
         */
        public Num currentBalance(Number principal) {
            return currentBalance(numOf(principal));
        }

        /**
         * Returns the realized balance from a starting principal.
         *
         * @param principal starting principal
         * @return current realized balance
         * @since 0.22.9
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
         * @since 0.22.9
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
         * @since 0.22.9
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
