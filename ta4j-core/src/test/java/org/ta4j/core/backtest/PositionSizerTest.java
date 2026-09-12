/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.MathContext;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

/**
 * Verifies that factory-created position sizers preserve caller values exactly
 * through the initial capital comparison, and that the continuous affordability
 * search fails explicitly for number implementations with unbounded precision.
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

    private static TradingRecord spotRecord(NumFactory numFactory) {
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
        Num amount = sizer.amount(context(numFactory, spotRecord(numFactory)));
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
        PositionSizer.Context sizingContext = context(numFactory, spotRecord(numFactory));
        Num budget = numFactory.numOf(TWO_TO_100);
        Num amount = sizingContext.maxAffordableAmount(budget);
        assertNumEquals(budget.minus(numFactory.one()), amount);
    }

    @Test
    void maxAffordableAmountFailsExplicitlyForUnboundedPrecision() {
        NumFactory numFactory = DecimalNumFactory.getInstance(MathContext.UNLIMITED);
        PositionSizer.Context sizingContext = context(numFactory, spotRecord(numFactory));
        Num budget = numFactory.numOf(TWO_TO_100);
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> sizingContext.maxAffordableAmount(budget));
        assertTrue(exception.getMessage().contains("did not converge"));
    }
}