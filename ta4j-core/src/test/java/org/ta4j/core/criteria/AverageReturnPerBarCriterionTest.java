/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class AverageReturnPerBarCriterionTest extends AbstractCriterionTest {
    private MockBarSeries series;

    public AverageReturnPerBarCriterionTest(Function<Number, Num> numFunction) {
        super((params) -> new AverageReturnPerBarCriterion(), numFunction);
    }

    @Test
    public void calculateOnlyWithGainPositions() {
        series = new MockBarSeries(numFunction, 100d, 105d, 110d, 100d, 95d, 105d);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));
        AnalysisCriterion averageProfit = getCriterion();
        assertNumEquals(1.0243, averageProfit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithASimplePosition() {
        series = new MockBarSeries(numFunction, 100d, 105d, 110d, 100d, 95d, 105d);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        AnalysisCriterion averageProfit = getCriterion();
        assertNumEquals(numOf(110d / 100).pow(numOf(1d / 3)), averageProfit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossPositions() {
        series = new MockBarSeries(numFunction, 100, 95, 100, 80, 85, 70);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));
        AnalysisCriterion averageProfit = getCriterion();
        assertNumEquals(numOf(95d / 100 * 70d / 100).pow(numOf(1d / 6)),
                averageProfit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithLosingAShortPositions() {
        series = new MockBarSeries(numFunction, 100d, 105d, 110d, 100d, 95d, 105d);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.sellAt(0, series), Trade.buyAt(2, series));
        AnalysisCriterion averageProfit = getCriterion();
        assertNumEquals(numOf(90d / 100).pow(numOf(1d / 3)), averageProfit.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithNoBarsShouldReturn1() {
        series = new MockBarSeries(numFunction, 100, 95, 100, 80, 85, 70);
        AnalysisCriterion averageProfit = getCriterion();
        assertNumEquals(1, averageProfit.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithOnePosition() {
        series = new MockBarSeries(numFunction, 100, 105);
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
        AnalysisCriterion average = getCriterion();
        assertNumEquals(numOf(105d / 100).pow(numOf(0.5)), average.calculate(series, position));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(2.0), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.0)));
    }
}
