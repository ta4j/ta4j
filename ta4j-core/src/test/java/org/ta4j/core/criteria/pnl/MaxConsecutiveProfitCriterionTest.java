/*
 * SPDX-License-Identifier: MIT
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

public class MaxConsecutiveProfitCriterionTest extends AbstractCriterionTest {

    public MaxConsecutiveProfitCriterionTest(NumFactory numFactory) {
        super(params -> new MaxConsecutiveProfitCriterion(), numFactory);
    }

    @Test
    public void calculateReturnsProfitForWinningPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120).build();
        var amount = numFactory.one();
        var entry = Trade.buyAt(0, series.getBar(0).getClosePrice(), amount);
        var exit = Trade.sellAt(1, series.getBar(1).getClosePrice(), amount);
        var position = new Position(entry, exit);

        var criterion = getCriterion();
        assertNumEquals(numFactory.numOf(20), criterion.calculate(series, position));
    }

    @Test
    public void calculateReturnsZeroForLosingOrOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 90, 80).build();
        var amount = numFactory.one();
        var lossEntry = Trade.buyAt(0, series.getBar(0).getClosePrice(), amount);
        var lossExit = Trade.sellAt(1, series.getBar(1).getClosePrice(), amount);
        var losingPosition = new Position(lossEntry, lossExit);

        var record = new BaseTradingRecord();
        record.enter(2, series.getBar(2).getClosePrice(), amount);
        var openPosition = record.getCurrentPosition();

        var criterion = getCriterion();
        assertNumEquals(numFactory.zero(), criterion.calculate(series, losingPosition));
        assertNumEquals(numFactory.zero(), criterion.calculate(series, openPosition));
    }

    @Test
    public void calculateReturnsZeroForRecordWithOnlyLosses() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 95, 90, 85).build();
        var amount = numFactory.one();
        var record = new BaseTradingRecord();

        record.enter(0, series.getBar(0).getClosePrice(), amount);
        record.exit(1, series.getBar(1).getClosePrice(), amount); // -5

        record.enter(2, series.getBar(2).getClosePrice(), amount);
        record.exit(3, series.getBar(3).getClosePrice(), amount); // -5

        var criterion = getCriterion();
        assertNumEquals(numFactory.zero(), criterion.calculate(series, record));
    }

    @Test
    public void calculateIdentifiesBestConsecutiveProfit() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
                .build();
        var amount = numFactory.one();
        var record = new BaseTradingRecord();

        record.enter(0, numFactory.numOf(10), amount);
        record.exit(1, numFactory.numOf(12), amount); // +2

        record.enter(2, numFactory.numOf(8), amount);
        record.exit(3, numFactory.numOf(10), amount); // +2 -> streak 4

        record.enter(4, numFactory.numOf(9), amount);
        record.exit(5, numFactory.numOf(7), amount); // -2 resets

        record.enter(6, numFactory.numOf(6), amount);
        record.exit(7, numFactory.numOf(9), amount); // +3

        record.enter(8, numFactory.numOf(5), amount);
        record.exit(9, numFactory.numOf(8), amount); // +3 -> streak 6

        var criterion = getCriterion();
        assertNumEquals(numFactory.numOf(6), criterion.calculate(series, record));
    }

    @Test
    public void betterThanPrefersGreaterProfit() {
        var criterion = getCriterion();
        assertTrue(criterion.betterThan(numFactory.numOf(5), numFactory.numOf(3)));
        assertFalse(criterion.betterThan(numFactory.numOf(1), numFactory.numOf(4)));
    }
}
