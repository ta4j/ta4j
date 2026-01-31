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

public class MaxConsecutiveLossCriterionTest extends AbstractCriterionTest {

    public MaxConsecutiveLossCriterionTest(NumFactory numFactory) {
        super(params -> new MaxConsecutiveLossCriterion(), numFactory);
    }

    @Test
    public void calculateReturnsLossForLosingPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 90).build();
        var amount = numFactory.one();
        var entry = Trade.buyAt(0, series.getBar(0).getClosePrice(), amount);
        var exit = Trade.sellAt(1, series.getBar(1).getClosePrice(), amount);
        var position = new Position(entry, exit);

        var criterion = getCriterion();
        assertNumEquals(numFactory.numOf(-10), criterion.calculate(series, position));
    }

    @Test
    public void calculateReturnsZeroForWinningOrOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 120).build();
        var amount = numFactory.one();
        var winEntry = Trade.buyAt(0, series.getBar(0).getClosePrice(), amount);
        var winExit = Trade.sellAt(1, series.getBar(1).getClosePrice(), amount);
        var winningPosition = new Position(winEntry, winExit);

        var record = new BaseTradingRecord();
        record.enter(2, series.getBar(2).getClosePrice(), amount);
        var openPosition = record.getCurrentPosition();

        var criterion = getCriterion();
        assertNumEquals(numFactory.zero(), criterion.calculate(series, winningPosition));
        assertNumEquals(numFactory.zero(), criterion.calculate(series, openPosition));
    }

    @Test
    public void calculateReturnsZeroForRecordWithoutLosses() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 120, 130, 140, 150)
                .build();
        var amount = numFactory.one();
        var record = new BaseTradingRecord();

        record.enter(0, numFactory.numOf(100), amount);
        record.exit(1, numFactory.numOf(110), amount); // +10

        record.enter(2, numFactory.numOf(115), amount);
        record.exit(3, numFactory.numOf(125), amount); // +10

        record.enter(4, numFactory.numOf(140), amount);
        record.exit(5, numFactory.numOf(140), amount); // 0

        var criterion = getCriterion();
        assertNumEquals(numFactory.zero(), criterion.calculate(series, record));
    }

    @Test
    public void calculateIdentifiesWorstConsecutiveLoss() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
                .build();
        var amount = numFactory.one();
        var record = new BaseTradingRecord();

        record.enter(0, numFactory.numOf(10), amount);
        record.exit(1, numFactory.numOf(9), amount); // -1

        record.enter(2, numFactory.numOf(11), amount);
        record.exit(3, numFactory.numOf(9), amount); // -2 (streak total -3)

        record.enter(4, numFactory.numOf(8), amount);
        record.exit(5, numFactory.numOf(11), amount); // +3 resets streak

        record.enter(6, numFactory.numOf(7), amount);
        record.exit(7, numFactory.numOf(3), amount); // -4

        record.enter(8, numFactory.numOf(6), amount);
        record.exit(9, numFactory.numOf(5), amount); // -1 -> cumulative -5

        record.enter(10, numFactory.numOf(5), amount);
        record.exit(11, numFactory.numOf(3), amount); // -2 -> cumulative -7

        var criterion = getCriterion();
        assertNumEquals(numFactory.numOf(-7), criterion.calculate(series, record));
    }

    @Test
    public void betterThanPrefersSmallerLoss() {
        var criterion = getCriterion();
        assertTrue(criterion.betterThan(numFactory.numOf(-2), numFactory.numOf(-5)));
        assertFalse(criterion.betterThan(numFactory.numOf(-6), numFactory.numOf(-3)));
    }
}
