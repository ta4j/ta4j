/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.analysis.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.BarSeries;
import org.junit.jupiter.api.Test;
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

    @Test
    public void configuredMarkToMarketIncludesAsOfStreakUnlessExplicitContextIgnoresIt() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 90, 100, 80, 100, 95, 100, 90, 70)
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
        MaxConsecutiveLossCriterion markToMarket = new MaxConsecutiveLossCriterion(OpenPositionHandling.MARK_TO_MARKET);
        assertNumEquals(-25, new MaxConsecutiveLossCriterion().calculate(series, record));
        // Selected closed streak is -25; the future-exit position adds its as-of-end
        // PnL.
        assertNumEquals(-35, markToMarket.calculate(series, record));
        AnalysisContext context = AnalysisContext.defaults()
                .withPositionInclusionPolicy(PositionInclusionPolicy.FULLY_CONTAINED)
                .withOpenPositionHandling(OpenPositionHandling.IGNORE);
        assertNumEquals(-25, markToMarket.calculate(series, record, AnalysisWindow.barRange(2, 8), context));
    }

}
