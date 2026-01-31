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

public class MaxPositionNetLossCriterionTest extends AbstractCriterionTest {

    public MaxPositionNetLossCriterionTest(NumFactory numFactory) {
        super(params -> new MaxPositionNetLossCriterion(), numFactory);
    }

    @Test
    public void calculateReturnsNetProfitOfPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 90).build();
        var amount = numFactory.one();
        var entry = Trade.buyAt(0, series.getBar(0).getClosePrice(), amount);
        var exit = Trade.sellAt(1, series.getBar(1).getClosePrice(), amount);
        var position = new Position(entry, exit);

        var criterion = getCriterion();
        assertNumEquals(numFactory.numOf(-10), criterion.calculate(series, position));
    }

    @Test
    public void calculateFindsMostNegativeClosedPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
        var amount = numFactory.one();
        var record = new BaseTradingRecord();

        record.enter(0, numFactory.numOf(10), amount);
        record.exit(1, numFactory.numOf(8), amount); // -2

        record.enter(2, numFactory.numOf(6), amount);
        record.exit(3, numFactory.numOf(11), amount); // +5

        record.enter(4, numFactory.numOf(7), amount);
        record.exit(5, numFactory.numOf(6), amount); // -1

        var criterion = getCriterion();
        assertNumEquals(numFactory.numOf(-2), criterion.calculate(series, record));
    }

    @Test
    public void calculateReturnsZeroWhenNoLosses() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        var amount = numFactory.one();
        var record = new BaseTradingRecord();

        record.enter(0, numFactory.numOf(5), amount);
        record.exit(1, numFactory.numOf(7), amount); // +2

        record.enter(2, numFactory.numOf(6), amount);
        record.exit(3, numFactory.numOf(9), amount); // +3

        var criterion = getCriterion();
        assertNumEquals(numFactory.zero(), criterion.calculate(series, record));
    }

    @Test
    public void betterThanPrefersSmallerLoss() {
        var criterion = getCriterion();
        assertTrue(criterion.betterThan(numFactory.numOf(-1), numFactory.numOf(-3)));
        assertFalse(criterion.betterThan(numFactory.numOf(-4), numFactory.numOf(-2)));
    }
}
