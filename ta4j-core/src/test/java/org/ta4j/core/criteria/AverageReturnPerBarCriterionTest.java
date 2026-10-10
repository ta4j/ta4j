/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AverageReturnPerBarCriterionTest extends AbstractCriterionTest {
    private BarSeries series;

    public AverageReturnPerBarCriterionTest(NumFactory numFactory) {
        super(params -> new AverageReturnPerBarCriterion(), numFactory);
    }

    @Test
    public void calculateOnlyWithGainPositions() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 105d, 110d, 100d, 95d, 105d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));
        AnalysisCriterion averageProfit = getCriterion();
        assertNumEquals(1.0243, averageProfit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithASimplePosition() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 105d, 110d, 100d, 95d, 105d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        AnalysisCriterion averageProfit = getCriterion();
        assertNumEquals(numOf(110d / 100).pow(numOf(1d / 3)), averageProfit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossPositions() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));
        AnalysisCriterion averageProfit = getCriterion();
        assertNumEquals(numOf(95d / 100 * 70d / 100).pow(numOf(1d / 6)),
                averageProfit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithLosingAShortPositions() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 105d, 110d, 100d, 95d, 105d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(2, series));
        AnalysisCriterion averageProfit = getCriterion();
        assertNumEquals(numOf(90d / 100).pow(numOf(1d / 3)), averageProfit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithNoBarsShouldReturn1() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        AnalysisCriterion averageProfit = getCriterion();
        assertNumEquals(1, averageProfit.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithNoBarsShouldReturnZeroRateOfReturn() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 100, 80, 85, 70).build();
        AnalysisCriterion averageProfit = new AverageReturnPerBarCriterion(ReturnRepresentation.DECIMAL);
        assertNumEquals(0, averageProfit.calculate(series, new BaseTradingRecord()));

        AnalysisCriterion averageMultiplicative = new AverageReturnPerBarCriterion(ReturnRepresentation.MULTIPLICATIVE);
        assertNumEquals(1, averageMultiplicative.calculate(series, new BaseTradingRecord()));

        AnalysisCriterion averagePercentage = new AverageReturnPerBarCriterion(ReturnRepresentation.PERCENTAGE);
        assertNumEquals(0, averagePercentage.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithOnePosition() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105).build();
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
        AnalysisCriterion average = getCriterion();
        assertNumEquals(numOf(105d / 100).pow(numOf(0.5)), average.calculate(series, position));

        AnalysisCriterion averageDecimal = new AverageReturnPerBarCriterion(ReturnRepresentation.DECIMAL);
        Num expectedDecimal = numOf(105d / 100).pow(numOf(0.5)).minus(numFactory.one());
        assertNumEquals(expectedDecimal, averageDecimal.calculate(series, position));

        AnalysisCriterion averagePercentage = new AverageReturnPerBarCriterion(ReturnRepresentation.PERCENTAGE);
        Num expectedPercentage = expectedDecimal.multipliedBy(numFactory.numOf(100));
        assertNumEquals(expectedPercentage, averagePercentage.calculate(series, position));
    }

    @Test
    public void calculateRateOfReturnRepresentation() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110, 100, 95, 105).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        AnalysisCriterion averageProfit = new AverageReturnPerBarCriterion(ReturnRepresentation.DECIMAL);
        Num expected = numOf(110d / 100).pow(numOf(1d / 3)).minus(numFactory.one());
        assertNumEquals(expected, averageProfit.calculate(series, tradingRecord));

        AnalysisCriterion averageMultiplicative = new AverageReturnPerBarCriterion(ReturnRepresentation.MULTIPLICATIVE);
        Num expectedMultiplicative = numOf(110d / 100).pow(numOf(1d / 3));
        assertNumEquals(expectedMultiplicative, averageMultiplicative.calculate(series, tradingRecord));

        AnalysisCriterion averagePercentage = new AverageReturnPerBarCriterion(ReturnRepresentation.PERCENTAGE);
        Num expectedPercentage = expected.multipliedBy(numFactory.numOf(100));
        assertNumEquals(expectedPercentage, averagePercentage.calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
    }
}
