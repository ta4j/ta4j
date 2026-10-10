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
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.commissions.CommissionsImpactPercentageCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.math.BigDecimal;
import java.util.Optional;

public class ActiveReturnCriterionTest extends AbstractCriterionTest {

    public ActiveReturnCriterionTest(NumFactory numFactory) {
        super(params -> params.length == 2
                ? new ActiveReturnCriterion((AnalysisCriterion) params[0], (AnalysisCriterion) params[1])
                : new ActiveReturnCriterion((AnalysisCriterion) params[0], (AnalysisCriterion) params[1],
                        (ReturnRepresentation) params[2]),
                numFactory);
    }

    @Test
    public void calculatesTradingRecordActiveReturnFromMixedInputRepresentations() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105).build();
        TradingRecord tradingRecord = new BaseTradingRecord();
        AnalysisCriterion primaryCriterion = new ConstantReturnCriterion(15.5, ReturnRepresentation.PERCENTAGE);
        AnalysisCriterion benchmarkCriterion = new ConstantReturnCriterion(0.05, ReturnRepresentation.DECIMAL);

        ActiveReturnCriterion criterion = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion,
                ReturnRepresentation.PERCENTAGE);

        assertNumEquals(10.5, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void formatsOutputRepresentationFromActiveRate() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105).build();
        TradingRecord tradingRecord = new BaseTradingRecord();
        AnalysisCriterion primaryCriterion = new ConstantReturnCriterion(0.155, ReturnRepresentation.DECIMAL);
        AnalysisCriterion benchmarkCriterion = new ConstantReturnCriterion(0.05, ReturnRepresentation.DECIMAL);

        ActiveReturnCriterion decimal = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion,
                ReturnRepresentation.DECIMAL);
        ActiveReturnCriterion percentage = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion,
                ReturnRepresentation.PERCENTAGE);
        ActiveReturnCriterion multiplicative = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion,
                ReturnRepresentation.MULTIPLICATIVE);
        ActiveReturnCriterion log = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion,
                ReturnRepresentation.LOG);

        assertNumEquals(0.105, decimal.calculate(series, tradingRecord));
        assertNumEquals(10.5, percentage.calculate(series, tradingRecord));
        assertNumEquals(1.105, multiplicative.calculate(series, tradingRecord));
        assertNumEquals(Math.log(1.105), log.calculate(series, tradingRecord));
    }

    @Test
    public void calculatesNegativeActiveReturnWithoutBenchmarkMagnitudeNormalization() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95).build();
        TradingRecord tradingRecord = new BaseTradingRecord();
        AnalysisCriterion primaryCriterion = new ConstantReturnCriterion(-33.5, ReturnRepresentation.PERCENTAGE);
        AnalysisCriterion benchmarkCriterion = new ConstantReturnCriterion(-0.30, ReturnRepresentation.DECIMAL);

        ActiveReturnCriterion criterion = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion,
                ReturnRepresentation.PERCENTAGE);

        assertNumEquals(-3.5, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void convertsLogInputRepresentationBeforeSubtractingBenchmark() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 112).build();
        TradingRecord tradingRecord = new BaseTradingRecord();
        AnalysisCriterion primaryCriterion = new ConstantReturnCriterion(Math.log(1.12), ReturnRepresentation.LOG);
        AnalysisCriterion benchmarkCriterion = new ConstantReturnCriterion(5.0, ReturnRepresentation.PERCENTAGE);

        ActiveReturnCriterion criterion = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion,
                ReturnRepresentation.PERCENTAGE);

        assertNumEquals(7.0, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculatesPositionActiveReturn() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        Position position = new Position();
        AnalysisCriterion primaryCriterion = new ConstantReturnCriterion(0.20, ReturnRepresentation.DECIMAL);
        AnalysisCriterion benchmarkCriterion = new ConstantReturnCriterion(0.10, ReturnRepresentation.DECIMAL);

        ActiveReturnCriterion criterion = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion,
                ReturnRepresentation.PERCENTAGE);

        assertNumEquals(10.0, criterion.calculate(series, position));
    }

    @Test
    public void returnsNeutralValueWhenPrimaryAndBenchmarkMatch() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105).build();
        TradingRecord tradingRecord = new BaseTradingRecord();
        AnalysisCriterion primaryCriterion = new ConstantReturnCriterion(5.0, ReturnRepresentation.PERCENTAGE);
        AnalysisCriterion benchmarkCriterion = new ConstantReturnCriterion(0.05, ReturnRepresentation.DECIMAL);

        ActiveReturnCriterion decimal = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion,
                ReturnRepresentation.DECIMAL);
        ActiveReturnCriterion percentage = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion,
                ReturnRepresentation.PERCENTAGE);
        ActiveReturnCriterion multiplicative = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion,
                ReturnRepresentation.MULTIPLICATIVE);
        ActiveReturnCriterion log = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion,
                ReturnRepresentation.LOG);

        assertNumEquals(0.0, decimal.calculate(series, tradingRecord));
        assertNumEquals(0.0, percentage.calculate(series, tradingRecord));
        assertNumEquals(1.0, multiplicative.calculate(series, tradingRecord));
        assertNumEquals(0.0, log.calculate(series, tradingRecord));
    }

    @Test
    public void defaultsToDecimalOutputRepresentation() {
        AnalysisCriterion primaryCriterion = new ConstantReturnCriterion(0.12, ReturnRepresentation.DECIMAL);
        AnalysisCriterion benchmarkCriterion = new ConstantReturnCriterion(0.05, ReturnRepresentation.DECIMAL);

        ActiveReturnCriterion criterion = new ActiveReturnCriterion(primaryCriterion, benchmarkCriterion);

        assertTrue(criterion.getReturnRepresentation().isPresent());
        assertEquals(ReturnRepresentation.DECIMAL, criterion.getReturnRepresentation().get());
    }

    @Test
    public void rejectsCriteriaWithoutReturnRepresentation() {
        AnalysisCriterion returnCriterion = new ConstantReturnCriterion(0.05, ReturnRepresentation.DECIMAL);

        assertThrows(IllegalArgumentException.class,
                () -> new ActiveReturnCriterion(new NumberOfBarsCriterion(), returnCriterion));
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveReturnCriterion(returnCriterion, new NumberOfBarsCriterion()));
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveReturnCriterion(new PlainReturnlessCriterion(), returnCriterion));
    }

    @Test
    public void rejectsRepresentedNonReturnCriteria() {
        AnalysisCriterion returnCriterion = new GrossReturnCriterion(ReturnRepresentation.DECIMAL);

        assertThrows(IllegalArgumentException.class, () -> new ActiveReturnCriterion(
                new PositionsRatioCriterion(AnalysisCriterion.PositionFilter.PROFIT, ReturnRepresentation.DECIMAL),
                returnCriterion));
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveReturnCriterion(new InPositionPercentageCriterion(ReturnRepresentation.DECIMAL),
                        returnCriterion));
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveReturnCriterion(new CommissionsImpactPercentageCriterion(ReturnRepresentation.DECIMAL),
                        returnCriterion));
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveReturnCriterion(new ReturnOverMaxDrawdownCriterion(ReturnRepresentation.DECIMAL),
                        returnCriterion));
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveReturnCriterion(
                        new EnterAndHoldCriterion(TradeType.BUY, new PositionsRatioCriterion(
                                AnalysisCriterion.PositionFilter.PROFIT, ReturnRepresentation.DECIMAL), BigDecimal.ONE),
                        returnCriterion));
    }

    @Test
    public void acceptsEnterAndHoldWhenWrappedCriterionIsReturnCriterion() {
        AnalysisCriterion returnCriterion = new GrossReturnCriterion(ReturnRepresentation.DECIMAL);
        AnalysisCriterion enterAndHoldCriterion = new EnterAndHoldCriterion(TradeType.BUY, returnCriterion,
                BigDecimal.ONE);

        ActiveReturnCriterion criterion = new ActiveReturnCriterion(returnCriterion, enterAndHoldCriterion,
                ReturnRepresentation.PERCENTAGE);

        assertEquals(ReturnRepresentation.PERCENTAGE, criterion.getReturnRepresentation().get());
    }

    @Test
    public void rejectsRelativeCriteriaAsInputs() {
        AnalysisCriterion returnCriterion = new ConstantReturnCriterion(0.05, ReturnRepresentation.DECIMAL);
        ActiveReturnCriterion activeReturn = new ActiveReturnCriterion(returnCriterion, returnCriterion);
        VersusEnterAndHoldCriterion versusEnterAndHold = new VersusEnterAndHoldCriterion(TradeType.BUY,
                new GrossReturnCriterion(ReturnRepresentation.DECIMAL), BigDecimal.ONE, ReturnRepresentation.DECIMAL);
        ActiveReturnVersusEnterAndHoldCriterion activeReturnVersusEnterAndHold = new ActiveReturnVersusEnterAndHoldCriterion(
                TradeType.BUY, new GrossReturnCriterion(ReturnRepresentation.DECIMAL), BigDecimal.ONE,
                ReturnRepresentation.DECIMAL);

        assertThrows(IllegalArgumentException.class, () -> new ActiveReturnCriterion(activeReturn, returnCriterion));
        assertThrows(IllegalArgumentException.class, () -> new ActiveReturnCriterion(returnCriterion, activeReturn));
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveReturnCriterion(versusEnterAndHold, returnCriterion));
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveReturnCriterion(returnCriterion, activeReturnVersusEnterAndHold));
    }

    @Test
    public void rejectsNullConstructorArguments() {
        AnalysisCriterion returnCriterion = new ConstantReturnCriterion(0.05, ReturnRepresentation.DECIMAL);

        assertThrows(NullPointerException.class, () -> new ActiveReturnCriterion(null, returnCriterion));
        assertThrows(NullPointerException.class, () -> new ActiveReturnCriterion(returnCriterion, null));
        assertThrows(NullPointerException.class,
                () -> new ActiveReturnCriterion(returnCriterion, returnCriterion, null));
    }

    @Test
    public void betterThanUsesHigherActiveReturn() {
        ActiveReturnCriterion criterion = new ActiveReturnCriterion(
                new ConstantReturnCriterion(0.10, ReturnRepresentation.DECIMAL),
                new ConstantReturnCriterion(0.05, ReturnRepresentation.DECIMAL));

        assertTrue(criterion.betterThan(numOf(0.10), numOf(0.05)));
        assertFalse(criterion.betterThan(numOf(0.05), numOf(0.10)));
    }

    private static final class ConstantReturnCriterion extends AbstractAnalysisCriterion implements ReturnCriterion {

        private final double value;
        private final ReturnRepresentation returnRepresentation;

        private ConstantReturnCriterion(double value, ReturnRepresentation returnRepresentation) {
            this.value = value;
            this.returnRepresentation = returnRepresentation;
        }

        @Override
        public Num calculate(BarSeries series, Position position) {
            return series.numFactory().numOf(value);
        }

        @Override
        public Num calculate(BarSeries series, TradingRecord tradingRecord) {
            return series.numFactory().numOf(value);
        }

        @Override
        public Optional<ReturnRepresentation> getReturnRepresentation() {
            return Optional.of(returnRepresentation);
        }

        @Override
        public boolean betterThan(Num criterionValue1, Num criterionValue2) {
            return criterionValue1.isGreaterThan(criterionValue2);
        }
    }

    private static final class PlainReturnlessCriterion implements AnalysisCriterion {

        @Override
        public Num calculate(BarSeries series, Position position) {
            return series.numFactory().zero();
        }

        @Override
        public Num calculate(BarSeries series, TradingRecord tradingRecord) {
            return series.numFactory().zero();
        }

        @Override
        public boolean betterThan(Num criterionValue1, Num criterionValue2) {
            return criterionValue1.isGreaterThan(criterionValue2);
        }
    }
}
