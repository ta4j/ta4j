/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
        assertNumEquals(numFactory.zero(), varCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithASimplePosition() {
        // if only one position in tail, VaR = ES
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 104d, 90d, 100d, 95d, 105d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        AnalysisCriterion esCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(Math.log(90d / 104)), esCriterion.calculate(series, tradingRecord));
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
        assertNumEquals(numFactory.numOf(-0.35835189384561106), esCriterion.calculate(series, position));
    }

    @Test
    public void calculateWithNoBarsShouldReturn0() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 95d, 100d, 80d, 85d, 70d).build();
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(0), varCriterion.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithBuyAndHold() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 99d).build();
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(Math.log(99d / 100)), varCriterion.calculate(series, position));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numFactory.numOf(-0.1), numFactory.numOf(-0.2)));
        assertFalse(criterion.betterThan(numFactory.numOf(-0.1), numFactory.numOf(0.0)));
    }
}
