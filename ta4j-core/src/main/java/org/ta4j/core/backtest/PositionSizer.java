/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
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
     * Implementations must return a positive, finite {@link Num} compatible with
     * {@link Context#numFactory()}. Factory-created sizers validate their
     * constructor inputs eagerly; custom implementations are responsible for
     * honoring this contract when they are called.
     * </p>
     *
     * @param context entry sizing context
     * @return amount used for entry execution
     * @since 0.22.9
     */
    Num amount(Context context);

    /**
     * Returns a position sizer that opens one unit.
     *
     * <p>
     * On a native futures record the unit is one contract and must satisfy the
     * contract quantity constraints.
     * </p>
     *
     * @return fixed unit position sizer
     * @since 0.22.9
     */
    static PositionSizer fixed() {
        return context -> {
            Num amount = context.numFactory().one();
            FuturesOrderQuantitySupport.requireTradable(context.futuresContract(), amount, context.entryPrice());
            return amount;
        };
    }

    /**
     * Returns a position sizer that opens a fixed numeric amount.
     * <p>
     * The amount is preserved exactly and re-wrapped to the record number precision
     * when sizing, so values beyond {@code 2^53} stay intact for decimal number
     * factories.
     * </p>
     *
     * @param amount fixed amount
     * @return fixed amount position sizer
     * @since 0.22.9
     */
    static PositionSizer fixed(Number amount) {
        Number fixedAmount = snapshotNumber(amount, "amount");
        return context -> {
            Num resolved = context.numOf(fixedAmount);
            FuturesOrderQuantitySupport.requireTradable(context.futuresContract(), resolved, context.entryPrice());
            return resolved;
        };
    }

    /**
     * Returns a position sizer that opens a fixed {@link Num} amount.
     *
     * <p>
     * On a native futures record the amount is a contract count and must be a
     * multiple of the contract quantity increment within the contract quantity and
     * notional bounds.
     * </p>
     *
     * @param amount fixed amount
     * @return fixed amount position sizer
     * @since 0.22.9
     */
    static PositionSizer fixed(Num amount) {
        validatePositiveNum(amount, "amount");
        return context -> {
            FuturesOrderQuantitySupport.requireTradable(context.futuresContract(), amount, context.entryPrice());
            return amount;
        };
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
     * <p>
     * The principal is preserved exactly and re-wrapped to the record number
     * precision when sizing, so values beyond {@code 2^53} stay intact for decimal
     * number factories.
     * </p>
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
     * <p>
     * The principal is preserved exactly and re-wrapped to the record number
     * precision when sizing, so values beyond {@code 2^53} stay intact for decimal
     * number factories.
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
        validatePositiveKellyFraction(fixedWinProbability, fixedPayoffRatio);
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
        if (value instanceof BigDecimal decimalValue) {
            requirePositiveSignum(decimalValue.signum(), name);
            return;
        }
        if (value instanceof BigInteger bigIntegerValue) {
            requirePositiveSignum(bigIntegerValue.signum(), name);
            return;
        }
        double doubleValue = value.doubleValue();
        if (!Double.isFinite(doubleValue) || doubleValue <= 0) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void requirePositiveSignum(int signum, String name) {
        if (signum <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static BigDecimal decimalValue(Number value) {
        if (value instanceof BigDecimal decimalValue) {
            return decimalValue;
        }
        if (value instanceof BigInteger bigIntegerValue) {
            return new BigDecimal(bigIntegerValue);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return BigDecimal.valueOf(value.longValue());
        }
        return BigDecimal.valueOf(value.doubleValue());
    }

    /**
     * Captures a factory input for later sizing.
     *
     * <p>
     * The value is captured at creation time without normalizing through
     * {@code double}: the JDK's immutable numeric types are kept as-is, and any
     * other {@link Number} implementation is copied into an immutable
     * {@code java.math.BigDecimal} through {@code toString()}, so later sizing
     * calls use the creation-time value even when the caller mutates the input.
     * Re-wrapping to the record's number precision happens at sizing time via
     * {@link Context#numOf(Number)}, which keeps exact values beyond {@code 2^53}
     * intact for decimal number factories.
     * </p>
     */
    private static Number snapshotNumber(Number value, String name) {
        validatePositiveNumber(value, name);
        if (value instanceof BigInteger || value instanceof BigDecimal || value instanceof Byte
                || value instanceof Short || value instanceof Integer || value instanceof Long || value instanceof Float
                || value instanceof Double) {
            return value;
        }
        return new BigDecimal(value.toString());
    }

    private static void validateProbability(Number value, String name) {
        Objects.requireNonNull(value, name);
        if (value instanceof Float || value instanceof Double) {
            double doubleValue = value.doubleValue();
            if (!Double.isFinite(doubleValue)) {
                throw new IllegalArgumentException(name + " must be finite and in (0, 1)");
            }
        }
        BigDecimal decimalValue = decimalValue(value);
        if (decimalValue.signum() <= 0 || decimalValue.compareTo(BigDecimal.ONE) >= 0) {
            throw new IllegalArgumentException(name + " must be finite and in (0, 1)");
        }
    }

    private static void validatePositiveKellyFraction(Number winProbability, Number payoffRatio) {
        BigDecimal probability = decimalValue(winProbability);
        BigDecimal ratio = decimalValue(payoffRatio);
        if (probability.multiply(ratio).compareTo(BigDecimal.ONE.subtract(probability)) <= 0) {
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
        if (!Num.isFinite(value)) {
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
     * @param fillTime             resolved execution timestamp of the entry target,
     *                             or {@code null} when unknown
     * @param strategy             strategy being evaluated
     * @param barSeries            backtested bar series
     * @param tradeType            entry trade type
     * @param tradingRecord        trading record for the current run
     * @param transactionCostModel transaction cost model
     * @param holdingCostModel     holding cost model
     * @since 0.22.9
     */
    public record Context(int signalIndex, int entryIndex, Num entryPrice, Instant fillTime, Strategy strategy,
            BarSeries barSeries, TradeType tradeType, TradingRecord tradingRecord, CostModel transactionCostModel,
            CostModel holdingCostModel) {

        /**
         * Creates an entry sizing context.
         *
         * @since 0.22.9
         */
        public Context {
            Objects.requireNonNull(entryPrice, "entryPrice");
            strategy = StrategySnapshots.copy(strategy);
            barSeries = snapshotSeries(barSeries);
            Objects.requireNonNull(tradeType, "tradeType");
            Objects.requireNonNull(tradingRecord, "tradingRecord");
            Objects.requireNonNull(transactionCostModel, "transactionCostModel");
            Objects.requireNonNull(holdingCostModel, "holdingCostModel");
        }

        /**
         * Creates an entry sizing context without a resolved execution timestamp.
         *
         * <p>
         * Retained for binary compatibility with callers compiled against the
         * pre-{@code fillTime} constructor; delegates with {@code fillTime} set to
         * {@code null}.
         * </p>
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
         * @since 0.25.1
         */
        public Context(int signalIndex, int entryIndex, Num entryPrice, Strategy strategy, BarSeries barSeries,
                TradeType tradeType, TradingRecord tradingRecord, CostModel transactionCostModel,
                CostModel holdingCostModel) {
            this(signalIndex, entryIndex, entryPrice, null, strategy, barSeries, tradeType, tradingRecord,
                    transactionCostModel, holdingCostModel);
        }

        @Override
        public BarSeries barSeries() {
            return snapshotSeries(barSeries);
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
            requireMatchingInitialCapital(principal);
            Num balance = principal;
            for (Position position : tradingRecord.getPositions()) {
                balance = balance.plus(position.getProfit());
            }
            return balance;
        }

        /**
         * Returns the futures contract of the trading record, or {@code null} for spot
         * records.
         *
         * @return traded contract, or {@code null} for spot records
         * @since 0.25.1
         */
        public FuturesContract futuresContract() {
            return tradingRecord.getFuturesContract();
        }

        /**
         * Returns estimated entry cash required for an amount, including entry
         * transaction costs.
         *
         * <p>
         * Spot records require the entry price times the amount. Native futures records
         * require the initial margin of the settlement notional plus the modeled entry
         * fee, so the amount is a contract count.
         * </p>
         *
         * @param amount candidate entry amount
         * @return estimated entry cost
         * @throws IllegalStateException when the record is a futures record without a
         *                               configured initial margin rate
         * @since 0.22.9
         */
        public Num entryCost(Num amount) {
            validateFiniteNum(amount, "amount");
            if (amount.isNegative()) {
                throw new IllegalArgumentException("amount must not be negative");
            }
            FuturesContract contract = futuresContract();
            if (contract == null) {
                return entryPrice.multipliedBy(amount).plus(transactionCostModel.calculate(entryPrice, amount));
            }
            return contract.marginRequirement(amount, entryPrice, requireInitialMarginRate())
                    .plus(modeledEntryFee(contract, amount));
        }

        /**
         * Returns the largest affordable amount for the provided budget.
         *
         * <p>
         * Native futures records size contracts against the initial margin requirement
         * per contract and then honor the contract quantity and notional constraints,
         * so the result is either zero or a tradable contract count.
         * </p>
         * <p>
         * The affordability search converges at the precision limit of the record
         * number factory after about {@code 3.32 * p} iterations, where {@code p} is
         * the number of significant digits, so the {@code Num} implementation must be
         * finite-precision, e.g. {@code DoubleNum} or {@code DecimalNum} with a bounded
         * {@code java.math.MathContext}, up to about 9800 significant digits.
         * </p>
         *
         * @param budget cash available for entry price and transaction costs
         * @return largest amount affordable by the budget, or zero when none is
         *         affordable
         * @throws IllegalStateException when the futures affordability range is
         *                               unbounded or the search does not converge for a
         *                               number implementation with unbounded precision
         */
        public Num maxAffordableAmount(Num budget) {
            validateFiniteNum(budget, "budget");
            validatePositiveNum(entryPrice, "entryPrice");
            Num zero = numFactory().zero();
            if (!budget.isPositive()) {
                return zero;
            }

            FuturesContract contract = futuresContract();
            Num upperBound;
            if (contract == null) {
                upperBound = budget.dividedBy(entryPrice);
            } else {
                Num marginPerContract = contract.marginRequirement(numFactory().one(), entryPrice,
                        requireInitialMarginRate());
                Num entryFeePerContract = modeledEntryFee(contract, numFactory().one());
                upperBound = marginPerContract.isPositive() ? budget.dividedBy(marginPerContract)
                        : entryFeePerContract.isPositive() ? budget.dividedBy(entryFeePerContract)
                                : maximumTradableAmount(contract);
            }
            if (!upperBound.isPositive()) {
                return zero;
            }

            if (contract == null) {
                return entryCost(upperBound).isLessThanOrEqual(budget) ? upperBound
                        : searchLargestAffordable(budget, upperBound);
            }
            return largestAffordableTradable(contract, upperBound, budget);
        }

        private Num maximumTradableAmount(FuturesContract contract) {
            Num maximum = FuturesOrderQuantitySupport.toNum(contract.maximumQuantity(), numFactory());
            Num maximumNotional = FuturesOrderQuantitySupport.toNum(contract.maximumNotional(), numFactory());
            if (maximumNotional != null) {
                Num perContractNotional = FuturesOrderQuantitySupport
                        .toNum(contract.quoteNotional(numFactory().one(), entryPrice), numFactory());
                if (perContractNotional == null || !perContractNotional.isPositive()) {
                    throw new IllegalStateException(
                            "native futures affordability has no positive per-contract notional bound");
                }
                Num notionalBound = maximumNotional.dividedBy(perContractNotional);
                maximum = maximum == null || notionalBound.isLessThan(maximum) ? notionalBound : maximum;
            }
            if (maximum == null || !maximum.isPositive()) {
                throw new IllegalStateException(
                        "native futures affordability is unbounded; configure maximumQuantity or maximumNotional");
            }
            return maximum;
        }

        /**
         * Finds the largest tradable contract count that the budget can afford.
         *
         * <p>
         * The count is bounded above by the margin-only upper bound, as any count above
         * it demands more margin than the budget. The search therefore runs as a binary
         * search over the quantity increment grid between zero and that bound,
         * converging in {@code log2} of the bound instead of stepping one increment at
         * a time. The result is zero when no tradable count is affordable, e.g. when
         * the affordable range lies below the minimum quantity or notional.
         * </p>
         *
         * @param contract   contract declaring the quantity constraints
         * @param upperBound margin-only upper bound on the affordable count
         * @param budget     cash available for entry
         * @return the largest tradable contract count the budget can afford, or zero
         */
        private Num largestAffordableTradable(FuturesContract contract, Num upperBound, Num budget) {
            Num zero = numFactory().zero();
            Num increment = FuturesOrderQuantitySupport.toNum(contract.quantityIncrement(), numFactory());
            if (increment == null) {
                Num affordable = entryCost(upperBound).isLessThanOrEqual(budget) ? upperBound
                        : searchLargestAffordable(budget, upperBound);
                return FuturesOrderQuantitySupport.largestTradable(contract, affordable, entryPrice);
            }
            Num maximum = FuturesOrderQuantitySupport.largestTradable(contract, upperBound, entryPrice);
            if (!maximum.isPositive()) {
                return zero;
            }
            if (entryCost(maximum).isLessThanOrEqual(budget)) {
                return maximum;
            }
            Num two = numFactory().two();
            Num low = zero;
            Num high = maximum;
            while (true) {
                Num mid = FuturesOrderQuantitySupport.roundDown(contract, low.plus(high.minus(low).dividedBy(two)));
                if (mid.isEqual(low) || mid.isEqual(high)) {
                    break;
                }
                if (entryCost(mid).isLessThanOrEqual(budget)) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            if (!FuturesOrderQuantitySupport.isTradable(contract, low, entryPrice)) {
                return zero;
            }
            return low;
        }

        /**
         * Convergence guard for the continuous affordability bisection. Each iteration
         * halves the interval width, so the midpoint degenerates to a bound after about
         * {@code 3.32 * p} iterations, where {@code p} is the number of significant
         * digits of the record number implementation; {@code DoubleNum} needs about 53
         * and a 5000-digit {@code DecimalNum} about 16610. The guard covers up to about
         * 9800 significant digits; beyond that, and for implementations with unbounded
         * precision, e.g. a {@code DecimalNumFactory} configured with
         * {@code java.math.MathContext.UNLIMITED}, the midpoint can remain strictly
         * between the bounds; the search then fails explicitly instead of looping.
         */
        private static final int AFFORDABILITY_SEARCH_GUARD_ITERATIONS = 32768;

        private Num searchLargestAffordable(Num budget, Num high) {
            Num low = numFactory().zero();
            Num two = numFactory().two();
            boolean converged = false;
            for (int iteration = 0; iteration < AFFORDABILITY_SEARCH_GUARD_ITERATIONS; iteration++) {
                Num mid = low.plus(high).dividedBy(two);
                if (mid.isEqual(low) || mid.isEqual(high)) {
                    converged = true;
                    break;
                }
                if (entryCost(mid).isLessThanOrEqual(budget)) {
                    low = mid;
                } else {
                    high = mid;
                }
            }
            if (!converged) {
                throw new IllegalStateException(
                        "affordability search did not converge within " + AFFORDABILITY_SEARCH_GUARD_ITERATIONS
                                + " iterations; the search converges for finite-precision Num implementations, e.g."
                                + " DoubleNum or DecimalNum with a bounded java.math.MathContext");
            }
            return low;
        }

        private Num requireInitialMarginRate() {
            Num marginRate = tradingRecord.getInitialMarginRate();
            if (marginRate == null) {
                throw new IllegalStateException(
                        "native futures entry sizing requires the record initial margin rate; configure the trading"
                                + " record with an initial margin rate or size entries with fixed contract quantities");
            }
            return marginRate;
        }

        private void requireMatchingInitialCapital(Num principal) {
            Num initialCapital = tradingRecord.getInitialCapital();
            if (initialCapital == null) {
                return;
            }
            Num expected = numFactory().numOf(initialCapital.getDelegate());
            if (!expected.isEqual(numFactory().numOf(principal.getDelegate()))) {
                throw new IllegalArgumentException(
                        "principal " + principal + " must equal the record initial capital " + initialCapital);
            }
        }

        private Num modeledEntryFee(FuturesContract contract, Num amount) {
            Num zero = numFactory().zero();
            if (!amount.isPositive() || transactionCostModel instanceof RecordedTradeCostModel) {
                return zero;
            }
            int index = clampedEntryIndex();
            // A resolved target timestamp is authoritative for the modeled fill;
            // fall back to the entry bar when the target carried none.
            Instant time = fillTime != null ? fillTime : entryTime(index);
            TradeFill fill = TradeFill.builder()
                    .index(index)
                    .time(time)
                    .price(entryPrice)
                    .amount(amount)
                    .side(entrySide())
                    .futuresContract(contract)
                    .build();
            Num fee = zero;
            for (TradeFee component : transactionCostModel.calculateFees(fill)) {
                Num settlementAmount = component.settlementAmount();
                if (settlementAmount == null) {
                    if (!contract.settlementCurrency().equals(component.currency())) {
                        throw new IllegalArgumentException("cannot size a futures entry with a " + component.currency()
                                + " fee component; express modeled fees in " + contract.settlementCurrency());
                    }
                    settlementAmount = component.amount();
                }
                fee = fee.plus(numFactory().numOf(settlementAmount.getDelegate()));
            }
            return fee;
        }

        private int clampedEntryIndex() {
            if (barSeries.getBarCount() == 0) {
                throw new IllegalStateException(
                        "native futures entry sizing requires at least one bar to price the entry");
            }
            return Math.max(barSeries.getBeginIndex(), Math.min(entryIndex, barSeries.getEndIndex()));
        }

        private Instant entryTime(int index) {
            Bar bar = barSeries.getBar(index);
            Instant time = bar.getEndTime() != null ? bar.getEndTime() : bar.getBeginTime();
            if (time == null) {
                throw new IllegalStateException("native futures sizing requires bar timestamps but bar " + index
                        + " has none; use a timestamped bar series or a spot trading record");
            }
            return time;
        }

        private ExecutionSide entrySide() {
            return tradeType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
        }

        private static BarSeries snapshotSeries(BarSeries barSeries) {
            BarSeries series = Objects.requireNonNull(barSeries, "barSeries");
            return new BaseBarSeriesBuilder().withName(series.getName())
                    .withNumFactory(series.numFactory())
                    .withBars(series.getBarData())
                    .withMaxBarCount(series.getMaximumBarCount())
                    .withBeginIndex(Math.max(0, series.getBeginIndex()))
                    .build();
        }
    }
}
