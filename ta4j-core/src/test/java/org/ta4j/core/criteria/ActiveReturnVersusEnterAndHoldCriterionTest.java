/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

import java.math.BigDecimal;

public class ActiveReturnVersusEnterAndHoldCriterionTest extends AbstractCriterionTest {

    public ActiveReturnVersusEnterAndHoldCriterionTest(NumFactory numFactory) {
        super(params -> params.length == 1 ? new ActiveReturnVersusEnterAndHoldCriterion((AnalysisCriterion) params[0])
                : new ActiveReturnVersusEnterAndHoldCriterion((TradeType) params[0], (AnalysisCriterion) params[1]),
                numFactory);
    }

    @Test
    public void calculatesTradingRecordActiveReturnVersusBuyAndHold() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));

        ActiveReturnVersusEnterAndHoldCriterion decimal = new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL), BigDecimal.ONE, ReturnRepresentation.DECIMAL);
        ActiveReturnVersusEnterAndHoldCriterion percentage = new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE,
                ReturnRepresentation.PERCENTAGE);
        ActiveReturnVersusEnterAndHoldCriterion multiplicative = new ActiveReturnVersusEnterAndHoldCriterion(
                TradeType.BUY, new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE), BigDecimal.ONE,
                ReturnRepresentation.MULTIPLICATIVE);

        assertNumEquals(0.105, decimal.calculate(series, tradingRecord));
        assertNumEquals(10.5, percentage.calculate(series, tradingRecord));
        assertNumEquals(1.105, multiplicative.calculate(series, tradingRecord));
    }

    @Test
    public void calculatesNegativeActiveReturnWithoutBenchmarkMagnitudeNormalization() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        ActiveReturnVersusEnterAndHoldCriterion criterion = new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE,
                ReturnRepresentation.PERCENTAGE);

        assertNumEquals(-3.5, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculatesPositionActiveReturnVersusBuyAndHold() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        ActiveReturnVersusEnterAndHoldCriterion criterion = new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE,
                ReturnRepresentation.PERCENTAGE);

        assertNumEquals(25.0, criterion.calculate(series, position));
    }

    @Test
    public void calculatesActiveReturnWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();

        ActiveReturnVersusEnterAndHoldCriterion criterion = new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE,
                ReturnRepresentation.PERCENTAGE);

        assertNumEquals(30.0, criterion.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculatesNeutralActiveReturnWithEmptySeries() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var tradingRecord = new BaseTradingRecord();
        var position = new Position();

        ActiveReturnVersusEnterAndHoldCriterion decimal = new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL), BigDecimal.ONE, ReturnRepresentation.DECIMAL);
        ActiveReturnVersusEnterAndHoldCriterion percentage = new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE,
                ReturnRepresentation.PERCENTAGE);
        ActiveReturnVersusEnterAndHoldCriterion multiplicative = new ActiveReturnVersusEnterAndHoldCriterion(
                TradeType.BUY, new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE), BigDecimal.ONE,
                ReturnRepresentation.MULTIPLICATIVE);

        assertNumEquals(0.0, decimal.calculate(series, tradingRecord));
        assertNumEquals(0.0, percentage.calculate(series, tradingRecord));
        assertNumEquals(1.0, multiplicative.calculate(series, tradingRecord));
        assertNumEquals(0.0, decimal.calculate(series, position));
    }

    @Test
    public void supportsSellAndHoldBenchmark() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();

        ActiveReturnVersusEnterAndHoldCriterion criterion = new ActiveReturnVersusEnterAndHoldCriterion(TradeType.SELL,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE,
                ReturnRepresentation.PERCENTAGE);

        assertNumEquals(-30.0, criterion.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void defaultsToDecimalOutputRepresentation() {
        ActiveReturnVersusEnterAndHoldCriterion criterion = new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE);

        assertTrue(criterion.getReturnRepresentation().isPresent());
        assertEquals(ReturnRepresentation.DECIMAL, criterion.getReturnRepresentation().get());
    }

    @Test
    public void shortConstructorsDefaultToDecimalOutputRepresentation() {
        ActiveReturnVersusEnterAndHoldCriterion buyAndHold = new ActiveReturnVersusEnterAndHoldCriterion(
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE));
        ActiveReturnVersusEnterAndHoldCriterion sellAndHold = new ActiveReturnVersusEnterAndHoldCriterion(
                TradeType.SELL, new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE));

        assertTrue(buyAndHold.getReturnRepresentation().isPresent());
        assertEquals(ReturnRepresentation.DECIMAL, buyAndHold.getReturnRepresentation().get());
        assertTrue(sellAndHold.getReturnRepresentation().isPresent());
        assertEquals(ReturnRepresentation.DECIMAL, sellAndHold.getReturnRepresentation().get());
    }

    @Test
    public void rejectsCriteriaWithoutReturnRepresentation() {
        assertThrows(IllegalArgumentException.class, () -> new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                new NumberOfBarsCriterion(), BigDecimal.ONE, ReturnRepresentation.DECIMAL));
    }

    @Test
    public void rejectsRepresentedNonReturnCriteria() {
        assertThrows(IllegalArgumentException.class, () -> new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                new PositionsRatioCriterion(AnalysisCriterion.PositionFilter.PROFIT, ReturnRepresentation.DECIMAL),
                BigDecimal.ONE, ReturnRepresentation.DECIMAL));
    }

    @Test
    public void rejectsNullConstructorArguments() {
        AnalysisCriterion returnCriterion = new GrossReturnCriterion(ReturnRepresentation.DECIMAL);

        assertThrows(NullPointerException.class, () -> new ActiveReturnVersusEnterAndHoldCriterion(null,
                returnCriterion, BigDecimal.ONE, ReturnRepresentation.DECIMAL));
        assertThrows(NullPointerException.class, () -> new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY, null,
                BigDecimal.ONE, ReturnRepresentation.DECIMAL));
        assertThrows(NullPointerException.class, () -> new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                returnCriterion, null, ReturnRepresentation.DECIMAL));
        assertThrows(NullPointerException.class, () -> new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                returnCriterion, BigDecimal.ONE, null));
    }

    @Test
    public void betterThanUsesHigherActiveReturn() {
        ActiveReturnVersusEnterAndHoldCriterion criterion = new ActiveReturnVersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE,
                ReturnRepresentation.PERCENTAGE);

        assertTrue(criterion.betterThan(numOf(10.0), numOf(5.0)));
        assertFalse(criterion.betterThan(numOf(5.0), numOf(10.0)));
    }
}
