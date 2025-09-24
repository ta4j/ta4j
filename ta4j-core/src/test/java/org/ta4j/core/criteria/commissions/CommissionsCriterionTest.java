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
package org.ta4j.core.criteria.commissions;

import java.util.stream.Stream;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CommissionsCriterionTest extends AbstractCriterionTest {

    public CommissionsCriterionTest(NumFactory numFactory) {
        super(params -> new CommissionsCriterion(), numFactory);
    }

    @Test
    public void calculateForOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120).build();
        var commission = 1.5;
        var costModel = new FixedTransactionCostModel(commission);
        var record = new BaseTradingRecord(TradeType.BUY, costModel, new ZeroCostModel());
        var amount = numFactory.one();

        record.enter(0, series.getFirstBar().getClosePrice(), amount);
        var openPosition = record.getCurrentPosition();

        var criterion = getCriterion();
        assertNumEquals(numFactory.numOf(commission), criterion.calculate(series, openPosition));
    }

    @Test
    public void calculateReturnsCommissionForClosedPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120).build();
        var costModel = new FixedTransactionCostModel(2.0);
        var amount = numFactory.one();

        var entry = Trade.buyAt(0, series.getBar(0).getClosePrice(), amount, costModel);
        var exit = Trade.sellAt(1, series.getBar(1).getClosePrice(), amount, costModel);
        var position = new Position(entry, exit);

        var criterion = getCriterion();
        var result = criterion.calculate(series, position);

        assertNumEquals(costModel.calculate(position), result);
    }

    @Test
    public void calculateSumsPositionsFromRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120, 130, 140).build();
        var costModel = new FixedTransactionCostModel(1.0);
        var record = new BaseTradingRecord(TradeType.BUY, costModel, new ZeroCostModel());
        var amount = numFactory.one();

        record.enter(0, series.getBar(0).getClosePrice(), amount);
        record.exit(1, series.getBar(1).getClosePrice(), amount);

        record.enter(2, series.getBar(2).getClosePrice(), amount);
        record.exit(3, series.getBar(3).getClosePrice(), amount);

        record.enter(4, series.getBar(4).getClosePrice(), amount);

        var criterion = getCriterion();
        var result = criterion.calculate(series, record);

        var expected = Stream.concat(record.getPositions().stream(), Stream.of(record.getCurrentPosition()))
                .map(p -> record.getTransactionCostModel().calculate(p))
                .reduce(numFactory.zero(), Num::plus);

        assertNumEquals(expected, result);
    }

    @Test
    public void betterThanPrefersLowerCommission() {
        var criterion = getCriterion();
        assertTrue(criterion.betterThan(numFactory.one(), numFactory.two()));
        assertFalse(criterion.betterThan(numFactory.two(), numFactory.one()));
    }
}
