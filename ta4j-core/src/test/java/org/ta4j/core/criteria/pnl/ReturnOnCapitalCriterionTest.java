/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.FundingCostModel;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ReturnOnCapitalCriterionTest {

    static Stream<NumFactory> factories() {
        return Stream.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void addsSizedProfitBeforeRepresentingReturnOnCapital(NumFactory factory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(100, 200, 100, 150).build();
        BaseTradingRecord record = new BaseTradingRecord();
        Num amount = factory.numOf(10);
        record.enter(0, factory.numOf(100), amount);
        record.exit(1, factory.numOf(200), amount);
        record.enter(2, factory.numOf(100), amount);
        record.exit(3, factory.numOf(150), amount);

        ReturnOnCapitalCriterion decimal = new ReturnOnCapitalCriterion(10_000, ReturnRepresentation.DECIMAL);
        assertNumEquals(0.15, decimal.calculate(series, record));
        assertNumEquals(0.1, decimal.calculate(series, record.getPositions().getFirst()));
        assertNumEquals(1.15,
                new ReturnOnCapitalCriterion(10_000, ReturnRepresentation.MULTIPLICATIVE).calculate(series, record));
        assertNumEquals(15,
                new ReturnOnCapitalCriterion(10_000, ReturnRepresentation.PERCENTAGE).calculate(series, record));
        assertNumEquals(Math.log(1.15),
                new ReturnOnCapitalCriterion(10_000, ReturnRepresentation.LOG).calculate(series, record));
        assertTrue(decimal.betterThan(factory.zero(), factory.one().negate()));
        assertFalse(decimal.betterThan(factory.zero(), factory.zero()));
        assertFalse(decimal.betterThan(factory.one().negate(), factory.zero()));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void includesFeesAndFundingWhileIgnoringOpenPositions(NumFactory factory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(100, 110, 100, 80).build();
        FundingCostModel funding = new FundingCostModel(new FixedNumIndicator(series, 0, 0.01, 0, 0.01));
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, new FixedTransactionCostModel(5), funding);
        record.enter(0, factory.numOf(100), factory.two());
        record.exit(1, factory.numOf(110), factory.two());
        record.enter(2, factory.numOf(100), factory.two());

        ReturnOnCapitalCriterion criterion = new ReturnOnCapitalCriterion(100, ReturnRepresentation.DECIMAL);
        // Gross profit 20, entry and exit fees 10, funding 2.2: net profit 7.8.
        assertNumEquals(0.078, criterion.calculate(series, record));
        assertNumEquals(0.078, criterion.calculate(series, record.getPositions().getFirst()));
        assertNumEquals(0, criterion.calculate(series, record.getCurrentPosition()));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void preservesLossesBeyondCapitalAndTheLogarithmicDomain(NumFactory factory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(200, 80).build();
        BaseTradingRecord record = new BaseTradingRecord();
        record.enter(0, factory.numOf(200), factory.numOf(100));
        record.exit(1, factory.numOf(80), factory.numOf(100));

        assertNumEquals(-1.2,
                new ReturnOnCapitalCriterion(10_000, ReturnRepresentation.DECIMAL).calculate(series, record));
        assertNumEquals(-0.2,
                new ReturnOnCapitalCriterion(10_000, ReturnRepresentation.MULTIPLICATIVE).calculate(series, record));
        assertNumEquals(-120,
                new ReturnOnCapitalCriterion(10_000, ReturnRepresentation.PERCENTAGE).calculate(series, record));
        assertTrue(new ReturnOnCapitalCriterion(10_000, ReturnRepresentation.LOG).calculate(series, record).isNaN());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void emptyAndOpenRecordsHaveTheRepresentedNeutralReturn(NumFactory factory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(100, 200).build();
        BaseTradingRecord empty = new BaseTradingRecord();
        BaseTradingRecord open = new BaseTradingRecord();
        open.enter(0, factory.numOf(100), factory.one());

        for (ReturnRepresentation representation : ReturnRepresentation.values()) {
            ReturnOnCapitalCriterion criterion = new ReturnOnCapitalCriterion(100, representation);
            int expected = representation == ReturnRepresentation.MULTIPLICATIVE ? 1 : 0;
            assertNumEquals(expected, criterion.calculate(series, empty));
            assertNumEquals(expected, criterion.calculate(series, open));
            assertNumEquals(expected, criterion.calculate(series, open.getCurrentPosition()));
        }
    }

    @Test
    void snapshotsMutableCapitalAndPreservesItsDecimalPrecision() {
        NumFactory factory = DecimalNumFactory.getInstance(32);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(100, 200).build();
        BaseTradingRecord record = new BaseTradingRecord();
        record.enter(0, factory.numOf(100), factory.one());
        record.exit(1, factory.numOf(200), factory.one());
        AtomicInteger capital = new AtomicInteger(100);
        ReturnOnCapitalCriterion snapshot = new ReturnOnCapitalCriterion(capital, ReturnRepresentation.DECIMAL);
        capital.set(1);
        assertNumEquals(1, snapshot.calculate(series, record));

        ReturnOnCapitalCriterion precise = new ReturnOnCapitalCriterion(new BigDecimal("100.0000000000000001"),
                ReturnRepresentation.DECIMAL);
        assertTrue(precise.calculate(series, record).isLessThan(factory.one()));
    }

    @Test
    void rejectsCapitalOutsideTheActiveDoubleRange() {
        NumFactory factory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(1, 2).build();
        BaseTradingRecord record = new BaseTradingRecord();
        record.enter(0, factory.one(), factory.one());
        record.exit(1, factory.two(), factory.one());
        for (String capital : new String[] { "1E-400", "1E400" }) {
            ReturnOnCapitalCriterion criterion = new ReturnOnCapitalCriterion(new BigDecimal(capital),
                    ReturnRepresentation.DECIMAL);
            assertThrows(IllegalArgumentException.class, () -> criterion.calculate(series, record));
            assertThrows(IllegalArgumentException.class,
                    () -> criterion.calculate(series, record.getPositions().getFirst()));
        }
    }

    @Test
    void preservesFiniteDecimalCapitalOutsideTheDoubleRange() {
        NumFactory factory = DecimalNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(1, 2).build();
        BaseTradingRecord record = new BaseTradingRecord();
        record.enter(0, factory.one(), factory.one());
        record.exit(1, factory.two(), factory.one());
        for (String capital : new String[] { "1E-400", "1E400" }) {
            BigDecimal value = new BigDecimal(capital);
            ReturnOnCapitalCriterion criterion = new ReturnOnCapitalCriterion(value, ReturnRepresentation.DECIMAL);
            Num expected = factory.numOf(BigDecimal.ONE.divide(value));
            assertTrue(criterion.calculate(series, record).isEqual(expected));
            assertTrue(criterion.calculate(series, record.getPositions().getFirst()).isEqual(expected));
        }
    }

    @Test
    void convertsCapitalWithTheSeriesRoundingMode() {
        NumFactory factory = DecimalNumFactory.getInstance(new MathContext(2, RoundingMode.DOWN));
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(1, 2).build();
        BaseTradingRecord record = new BaseTradingRecord();
        record.enter(0, factory.one(), factory.one());
        record.exit(1, factory.two(), factory.one());
        ReturnOnCapitalCriterion criterion = new ReturnOnCapitalCriterion(new BigDecimal("1.05"),
                ReturnRepresentation.DECIMAL);
        assertNumEquals(1, criterion.calculate(series, record));
        assertNumEquals(1, criterion.calculate(series, record.getPositions().getFirst()));
    }

    @Test
    void rejectsInvalidCapitalAndNullRepresentation() {
        for (Number capital : new Number[] { 0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class, () -> new ReturnOnCapitalCriterion(capital));
            assertThrows(IllegalArgumentException.class,
                    () -> new ReturnOnCapitalCriterion(capital, ReturnRepresentation.DECIMAL));
        }
        assertThrows(NullPointerException.class, () -> new ReturnOnCapitalCriterion(null));
        assertThrows(NullPointerException.class, () -> new ReturnOnCapitalCriterion(100, null));
    }
}
