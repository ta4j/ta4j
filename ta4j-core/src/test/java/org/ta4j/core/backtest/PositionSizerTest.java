/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.FuturesTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

/**
 * Verifies that factory-created position sizers preserve caller values exactly
 * through the initial capital comparison and that bounded high-precision
 * affordability searches converge without losing the caller value.
 */
class PositionSizerTest {

    private static final long BEYOND_DOUBLE_PRECISION = 9007199254740993L; // 2^53 + 1

    private static final String TWO_TO_100 = "1267650600228229401496703205376";

    private static Strategy entryOnFirstBar() {
        return new BaseStrategy(new FixedRule(0), new FixedRule());
    }

    private static BarSeries flatSeries(NumFactory numFactory, double price) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(price).highPrice(price).lowPrice(price).closePrice(price).volume(1).add();
        return series;
    }

    private static FuturesContract linearContract(NumFactory numFactory) {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.one())
                .quantityIncrement(numFactory.one())
                .minimumQuantity(numFactory.one())
                .build();
    }

    private static TradingRecord spotRecord() {
        return BaseTradingRecord.builder().transactionCostModel(new FixedTransactionCostModel(1.0)).build();
    }

    private static TradingRecord futuresRecord(NumFactory numFactory, Num initialCapital) {
        return BaseTradingRecord.builder()
                .futuresContract(linearContract(numFactory))
                .initialCapital(initialCapital)
                .initialMarginRate(numFactory.one())
                .transactionCostModel(new FixedTransactionCostModel(1.0))
                .build();
    }

    private static PositionSizer.Context context(NumFactory numFactory, TradingRecord tradingRecord) {
        return new PositionSizer.Context(0, 0, numFactory.one(), null, entryOnFirstBar(), flatSeries(numFactory, 1),
                TradeType.BUY, tradingRecord, new FixedTransactionCostModel(1.0), new ZeroCostModel());
    }

    @Test
    void fixedPreservesExactAmountBeyondDoublePrecision() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        PositionSizer sizer = PositionSizer.fixed(BEYOND_DOUBLE_PRECISION);
        Num amount = sizer.amount(context(numFactory, spotRecord()));
        assertNumEquals(numFactory.numOf(BEYOND_DOUBLE_PRECISION), amount);
    }

    @Test
    void balancePreservesExactPrincipalBeyondDoublePrecision() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        TradingRecord tradingRecord = futuresRecord(numFactory, numFactory.numOf(BEYOND_DOUBLE_PRECISION));
        PositionSizer sizer = PositionSizer.balance(BEYOND_DOUBLE_PRECISION, (c, balance) -> balance);
        Num amount = sizer.amount(context(numFactory, tradingRecord));
        assertNumEquals(numFactory.numOf(BEYOND_DOUBLE_PRECISION), amount);
    }

    @Test
    void kellyPreservesExactPrincipalBeyondDoublePrecision() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        TradingRecord tradingRecord = futuresRecord(numFactory, numFactory.numOf(BEYOND_DOUBLE_PRECISION));
        PositionSizer sizer = PositionSizer.kelly(BEYOND_DOUBLE_PRECISION, 0.6, 2);
        Num amount = sizer.amount(context(numFactory, tradingRecord));
        assertTrue(amount.isPositive());
    }

    @Test
    void balanceRejectsMismatchedPrincipalBeyondDoublePrecision() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        TradingRecord tradingRecord = futuresRecord(numFactory, numFactory.numOf(BEYOND_DOUBLE_PRECISION - 1));
        PositionSizer sizer = PositionSizer.balance(BEYOND_DOUBLE_PRECISION, (c, balance) -> balance);
        assertThrows(IllegalArgumentException.class, () -> sizer.amount(context(numFactory, tradingRecord)));
    }

    @Test
    void maxAffordableAmountConvergesExactlyForHighPrecisionFactory() {
        NumFactory numFactory = DecimalNumFactory.getInstance(40);
        PositionSizer.Context sizingContext = context(numFactory, spotRecord());
        Num budget = numFactory.numOf(TWO_TO_100);
        Num amount = sizingContext.maxAffordableAmount(budget);
        assertNumEquals(budget.minus(numFactory.one()), amount);
    }

    @Test
    void fixedAcceptsExactDecimalBeyondDoubleRange() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        PositionSizer sizer = PositionSizer.fixed(new BigDecimal("1E400"));
        Num amount = sizer.amount(context(numFactory, spotRecord()));
        assertNumEquals(numFactory.numOf("1E400"), amount);
    }

    @Test
    void fixedSnapshotsMutableInputAtCreationTime() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        AtomicLong input = new AtomicLong(BEYOND_DOUBLE_PRECISION);
        PositionSizer sizer = PositionSizer.fixed(input);
        input.set(0);
        Num amount = sizer.amount(context(numFactory, spotRecord()));
        assertNumEquals(numFactory.numOf(BEYOND_DOUBLE_PRECISION), amount);
    }

    @Test
    void maxAffordableAmountConvergesForFiveThousandDigitPrecision() {
        NumFactory numFactory = DecimalNumFactory.getInstance(5000);
        PositionSizer.Context sizingContext = context(numFactory, spotRecord());
        Num budget = numFactory.numOf("1E2000");
        Num amount = sizingContext.maxAffordableAmount(budget);
        assertNumEquals(budget.minus(numFactory.one()), amount);
    }

    @Test
    void kellyAcceptsProbabilityBeyondDoubleRange() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        PositionSizer sizer = PositionSizer.kelly(BigDecimal.ONE, new BigDecimal("1E-400"), new BigDecimal("1E401"));
        Num amount = sizer.amount(new PositionSizer.Context(0, 0, numFactory.one(), null, entryOnFirstBar(),
                flatSeries(numFactory, 1), TradeType.BUY, spotRecord(), new ZeroCostModel(), new ZeroCostModel()));
        assertTrue(amount.isPositive());
    }

    @Test
    void fixedRewrapsAmountCreatedByAnotherFactory() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        Num foreignAmount = DoubleNumFactory.getInstance().numOf(5);
        PositionSizer sizer = PositionSizer.fixed(foreignAmount);

        Num amount = sizer.amount(context(numFactory, spotRecord()));

        assertEquals(numFactory.getClass(), amount.getNumFactory().getClass());
        assertNumEquals(numFactory.numOf(5), amount);
    }

    @Test
    void maxAffordableAmountHonorsPerContractRebates() {
        NumFactory numFactory = DoubleNumFactory.getInstance();
        FuturesContract contract = linearContract(numFactory);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(9))
                .initialMarginRate(numFactory.numOf(0.1))
                .build();
        PositionSizer.Context sizingContext = new PositionSizer.Context(0, 0, numFactory.hundred(), null,
                entryOnFirstBar(), flatSeries(numFactory, 100), TradeType.BUY, record,
                FuturesTransactionCostModel.builder()
                        .makerRate(numFactory.numOf(-0.01))
                        .takerRate(numFactory.numOf(-0.01))
                        .build(),
                new ZeroCostModel());

        // one contract costs 10 of margin but earns a 1 rebate, so a 9 budget affords
        // one contract
        assertNumEquals(numFactory.one(), sizingContext.maxAffordableAmount(numFactory.numOf(9)));
        assertNumEquals(numFactory.zero(), sizingContext.maxAffordableAmount(numFactory.numOf(8)));
    }

    @Test
    public void maxAffordableAmountHonorsFixedRebates() {
        NumFactory numFactory = DoubleNumFactory.getInstance();
        FuturesContract contract = linearContract(numFactory);
        FixedTransactionCostModel rebate = new FixedTransactionCostModel(-1);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(19))
                .initialMarginRate(numFactory.numOf(0.1))
                .build();
        PositionSizer.Context sizingContext = new PositionSizer.Context(0, 0, numFactory.hundred(), null,
                entryOnFirstBar(), flatSeries(numFactory, 100), TradeType.BUY, record, rebate, new ZeroCostModel());

        assertNumEquals(numFactory.numOf(2), sizingContext.maxAffordableAmount(numFactory.numOf(19)));
    }

    @Test
    public void maxAffordableAmountProbesMonotonicTieredFees() {
        NumFactory numFactory = DoubleNumFactory.getInstance();
        FuturesContract contract = linearContract(numFactory);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf("32.1"))
                .initialMarginRate(numFactory.numOf(0.1))
                .build();
        PositionSizer.Context sizingContext = new PositionSizer.Context(0, 0, numFactory.hundred(), null,
                entryOnFirstBar(), flatSeries(numFactory, 100), TradeType.BUY, record,
                new MonotonicTieredFuturesCostModel(), new ZeroCostModel());

        assertNumEquals(numFactory.numOf(3), sizingContext.maxAffordableAmount(numFactory.numOf("32.1")));
    }

    private static final class MonotonicTieredFuturesCostModel implements CostModel {

        @Override
        public Num calculate(Position position, int finalIndex) {
            return position.getEntry().getPricePerAsset().getNumFactory().zero();
        }

        @Override
        public Num calculate(Position position) {
            return position.getEntry().getPricePerAsset().getNumFactory().zero();
        }

        @Override
        public Num calculate(Num price, Num amount) {
            return tieredFee(amount);
        }

        @Override
        public Num calculate(TradeFill fill) {
            return tieredFee(fill.amount());
        }

        @Override
        public boolean equals(CostModel otherModel) {
            return otherModel instanceof MonotonicTieredFuturesCostModel;
        }

        private Num tieredFee(Num amount) {
            Num two = amount.getNumFactory().two();
            if (amount.isLessThanOrEqual(two)) {
                return amount;
            }
            return two.plus(amount.minus(two).multipliedBy(amount.getNumFactory().numOf("0.1")));
        }
    }

    @Test
    public void maxAffordableAmountReturnsZeroForUnderflowedNotionalLimit() {
        NumFactory numFactory = DoubleNumFactory.getInstance();
        FuturesContract contract = linearContract(numFactory).toBuilder()
                .maximumNotional(numFactory.numOf(Double.MIN_VALUE))
                .build();
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.hundred())
                .initialMarginRate(numFactory.one())
                .build();
        PositionSizer.Context sizingContext = new PositionSizer.Context(0, 0, numFactory.hundred(), null,
                entryOnFirstBar(), flatSeries(numFactory, 100), TradeType.BUY, record, new ZeroCostModel(),
                new ZeroCostModel());

        assertNumEquals(numFactory.zero(), sizingContext.maxAffordableAmount(numFactory.hundred()));
    }

    @Test
    public void maxAffordableAmountNormalizesForeignBudgets() {
        NumFactory[] factories = { DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance() };
        for (int i = 0; i < factories.length; i++) {
            NumFactory factory = factories[i];
            Num budget = factories[1 - i].numOf(5);
            assertNumEquals(4, context(factory, futuresRecord(factory, factory.numOf(5))).maxAffordableAmount(budget));
            assertNumEquals(4, context(factory, spotRecord()).maxAffordableAmount(budget));
        }
    }

    @Test
    public void maxAffordableAmountChecksMaximumBeyondFeeSpike() {
        for (NumFactory factory : new NumFactory[] { DoubleNumFactory.getInstance(),
                DecimalNumFactory.getInstance() }) {
            FuturesContract contract = linearContract(factory).toBuilder().maximumQuantity(factory.numOf(10)).build();
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(factory.numOf(5))
                    .initialMarginRate(factory.one())
                    .build();
            CostModel feeModel = new ZeroCostModel() {
                @Override
                public Num calculate(TradeFill fill) {
                    NumFactory numFactory = fill.amount().getNumFactory();
                    return numFactory.numOf(fill.amount().isLessThan(numFactory.numOf(10)) ? 10 : -10);
                }
            };
            PositionSizer.Context sizingContext = new PositionSizer.Context(0, 0, factory.one(), null,
                    entryOnFirstBar(), flatSeries(factory, 1), TradeType.BUY, record, feeModel, new ZeroCostModel());
            assertNumEquals(11, sizingContext.entryCost(factory.one()));
            assertNumEquals(0, sizingContext.entryCost(factory.numOf(10)));
            assertNumEquals(10, sizingContext.maxAffordableAmount(factory.numOf(5)));
        }
    }
}
