/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.ZeroCostModel;
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

    @Test
    public void directBoundedRecordExcludesEarlierEntryAndFutureExit() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 110, 100, 120, 100, 105, 100, 110, 130)
                .build();
        TradingRecord record = new BaseTradingRecord(Trade.TradeType.BUY, 2, 8, new ZeroCostModel(),
                new ZeroCostModel());
        record.operate(Trade.buyAt(0, series, numFactory.one()));
        record.operate(Trade.sellAt(2, series, numFactory.one()));
        record.operate(Trade.buyAt(3, series, numFactory.one()));
        record.operate(Trade.sellAt(4, series, numFactory.one()));
        record.operate(Trade.buyAt(5, series, numFactory.one()));
        record.operate(Trade.sellAt(6, series, numFactory.one()));
        record.operate(Trade.buyAt(7, series, numFactory.one()));
        record.operate(Trade.sellAt(9, series, numFactory.one()));
        // The future +30 must not replace the selected maximum of +20.
        assertNumEquals(20, getCriterion().calculate(series, record));
    }

}
