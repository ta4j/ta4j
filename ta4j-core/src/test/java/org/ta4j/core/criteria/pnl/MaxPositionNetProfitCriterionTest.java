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
package org.ta4j.core.criteria.pnl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class MaxPositionNetProfitCriterionTest extends AbstractCriterionTest {

    public MaxPositionNetProfitCriterionTest(NumFactory numFactory) {
        super(params -> new MaxPositionNetProfitCriterion(), numFactory);
    }

    @Test
    public void calculateReturnsNetProfitOfPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 130).build();
        var amount = numFactory.one();
        var entry = Trade.buyAt(0, series.getBar(0).getClosePrice(), amount);
        var exit = Trade.sellAt(1, series.getBar(1).getClosePrice(), amount);
        var position = new Position(entry, exit);

        var criterion = getCriterion();
        assertNumEquals(numFactory.numOf(30), criterion.calculate(series, position));
    }

    @Test
    public void calculateFindsLargestClosedProfit() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        var amount = numFactory.one();
        var record = new BaseTradingRecord();

        record.enter(0, numFactory.numOf(5), amount);
        record.exit(1, numFactory.numOf(7), amount); // +2

        record.enter(2, numFactory.numOf(6), amount);
        record.exit(3, numFactory.numOf(11), amount); // +5

        record.enter(4, numFactory.numOf(8), amount);
        record.exit(5, numFactory.numOf(6), amount); // -2

        var criterion = getCriterion();
        assertNumEquals(numFactory.numOf(5), criterion.calculate(series, record));
    }

    @Test
    public void calculateReturnsZeroWhenNoProfits() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        var amount = numFactory.one();
        var record = new BaseTradingRecord();

        record.enter(0, numFactory.numOf(10), amount);
        record.exit(1, numFactory.numOf(7), amount); // -3

        record.enter(2, numFactory.numOf(9), amount);
        record.exit(3, numFactory.numOf(6), amount); // -3

        var criterion = getCriterion();
        assertNumEquals(numFactory.zero(), criterion.calculate(series, record));
    }

    @Test
    public void betterThanPrefersGreaterProfit() {
        var criterion = getCriterion();
        assertTrue(criterion.betterThan(numFactory.numOf(4), numFactory.numOf(2)));
        assertFalse(criterion.betterThan(numFactory.numOf(1), numFactory.numOf(3)));
    }
}
