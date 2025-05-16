/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.NumFactory;

public class ReturnOverMaxDrawdownCriterionTest extends AbstractCriterionTest {

    private AnalysisCriterion criterionWithBase;
    private AnalysisCriterion criterionWithoutBase;

    public ReturnOverMaxDrawdownCriterionTest(NumFactory numFactory) {
        super(params -> params.length == 1 ? new ReturnOverMaxDrawdownCriterion((boolean) params[0])
                : new ReturnOverMaxDrawdownCriterion(), numFactory);
    }

    @Before
    public void setUp() {
        // with base uses the formula: "return / maximumDrawdown"
        this.criterionWithBase = getCriterion();

        // without base uses the formula: "(return - 1) / maximumDrawdown"
        this.criterionWithoutBase = getCriterion(false);
    }

    @Test
    public void rewardRiskRatioCriterion() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 95, 100, 90, 95, 80, 120)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(4, series), Trade.buyAt(5, series), Trade.sellAt(7, series));

        double totalProfit = (105d / 100) * (90d / 95d) * (120d / 95);
        double peak = (105d / 100) * (100d / 95);
        double low = (105d / 100) * (90d / 95) * (80d / 95);

        assertNumEquals(totalProfit / ((peak - low) / peak), criterionWithBase.calculate(series, tradingRecord));
        assertNumEquals((totalProfit - 1) / ((peak - low) / peak),
                criterionWithoutBase.calculate(series, tradingRecord));
    }

    @Test
    public void rewardRiskRatioCriterionOnlyWithGain() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 6, 8, 20, 3).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));
        assertTrue(criterionWithBase.calculate(series, tradingRecord).isNaN());
        assertTrue(criterionWithoutBase.calculate(series, tradingRecord).isNaN());
    }

    @Test
    public void rewardRiskRatioCriterionWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 6, 8, 20, 3).build();
        assertTrue(criterionWithBase.calculate(series, new BaseTradingRecord()).isNaN());
        assertTrue(criterionWithoutBase.calculate(series, new BaseTradingRecord()).isNaN());
    }

    @Test
    public void withOnePosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 95, 95, 100, 90, 95, 80, 120)
                .build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        assertNumEquals((95d / 100) / (1d - 0.95d), criterionWithBase.calculate(series, position));
        assertNumEquals(((95d / 100) - 1) / ((1d - 0.95d)), criterionWithoutBase.calculate(series, position));
    }

    @Test
    public void betterThan() {
        var criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(3.5), numOf(2.2)));
        assertFalse(criterion.betterThan(numOf(1.5), numOf(2.7)));
    }

    @Test
    public void testNoDrawDownForTradingRecord() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 95, 100, 90, 95, 80, 120)
                .build();
        final var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(3, series));

        final var resultWithBase = criterionWithBase.calculate(series, tradingRecord);
        final var resultWithoutBase = criterionWithoutBase.calculate(series, tradingRecord);

        assertNumEquals(NaN.NaN, resultWithBase);
        assertNumEquals(NaN.NaN, resultWithoutBase);
    }

    @Test
    public void testNoDrawDownForPosition() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 95, 100, 90, 95, 80, 120)
                .build();
        final var position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));

        final var resultWithBase = criterionWithBase.calculate(series, position);
        final var resultWithoutBase = criterionWithoutBase.calculate(series, position);

        assertNumEquals(NaN.NaN, resultWithBase);
        assertNumEquals(NaN.NaN, resultWithoutBase);
    }
}
