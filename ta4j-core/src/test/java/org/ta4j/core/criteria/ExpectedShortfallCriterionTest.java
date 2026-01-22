/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

public class ExpectedShortfallCriterionTest {
    private BarSeries series;

    private NumFactory numFactory = DoubleNumFactory.getInstance();

    private ExpectedShortfallCriterion getCriterion() {
        return new ExpectedShortfallCriterion(0.95);
    }

    @Test
    public void calculateOnlyWithGainPositions() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 105d, 106d, 107d, 108d, 115d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numFactory.one(), varCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithASimplePosition() {
        // if only one position in tail, VaR = ES
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 104d, 90d, 100d, 95d, 105d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        AnalysisCriterion esCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(90d / 104), esCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossPosition() {
        // regularly decreasing prices
        List<Double> prices = IntStream.rangeClosed(1, 100)
                .asDoubleStream()
                .boxed()
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        Position position = new Position(Trade.buyAt(series.getBeginIndex(), series),
                Trade.sellAt(series.getEndIndex(), series));
        AnalysisCriterion esCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(0.6988271187715792), esCriterion.calculate(series, position));
    }

    @Test
    public void calculateWithNoBarsShouldReturn0() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 95d, 100d, 80d, 85d, 70d).build();
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(1), varCriterion.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithNoBarsShouldReturnZeroRateOfReturn() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 95d, 100d, 80d, 85d, 70d).build();
        AnalysisCriterion varCriterion = new ExpectedShortfallCriterion(0.95, ReturnRepresentation.DECIMAL);
        assertNumEquals(numFactory.zero(), varCriterion.calculate(series, new BaseTradingRecord()));

        AnalysisCriterion esCriterionMultiplicative = new ExpectedShortfallCriterion(0.95,
                ReturnRepresentation.MULTIPLICATIVE);
        assertNumEquals(numFactory.one(), esCriterionMultiplicative.calculate(series, new BaseTradingRecord()));

        AnalysisCriterion esCriterionPercentage = new ExpectedShortfallCriterion(0.95, ReturnRepresentation.PERCENTAGE);
        assertNumEquals(numFactory.zero(), esCriterionPercentage.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithBuyAndHold() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 99d).build();
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(0.99), varCriterion.calculate(series, position));

        AnalysisCriterion esCriterionDecimal = new ExpectedShortfallCriterion(0.95, ReturnRepresentation.DECIMAL);
        assertNumEquals(numFactory.numOf(0.99 - 1), esCriterionDecimal.calculate(series, position));

        AnalysisCriterion esCriterionPercentage = new ExpectedShortfallCriterion(0.95, ReturnRepresentation.PERCENTAGE);
        assertNumEquals(numFactory.numOf((0.99 - 1) * 100), esCriterionPercentage.calculate(series, position));
    }

    @Test
    public void calculateRateOfReturnRepresentation() {
        // if only one position in tail, VaR = ES
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 104d, 90d, 100d, 95d, 105d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        AnalysisCriterion esCriterion = new ExpectedShortfallCriterion(0.95, ReturnRepresentation.DECIMAL);
        assertNumEquals(numFactory.numOf((90d / 104) - 1), esCriterion.calculate(series, tradingRecord));

        AnalysisCriterion esCriterionMultiplicative = new ExpectedShortfallCriterion(0.95,
                ReturnRepresentation.MULTIPLICATIVE);
        assertNumEquals(numFactory.numOf(90d / 104), esCriterionMultiplicative.calculate(series, tradingRecord));

        AnalysisCriterion esCriterionPercentage = new ExpectedShortfallCriterion(0.95, ReturnRepresentation.PERCENTAGE);
        assertNumEquals(numFactory.numOf(((90d / 104) - 1) * 100),
                esCriterionPercentage.calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numFactory.numOf(-0.1), numFactory.numOf(-0.2)));
        assertFalse(criterion.betterThan(numFactory.numOf(-0.1), numFactory.numOf(0.0)));
    }
}
