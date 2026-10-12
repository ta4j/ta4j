/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.commissions;

import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.analysis.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.BarSeries;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
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

    @Test
    public void directBoundedRecordChargesOnlySelectedClosedTradesAndOpenEntry() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 110, 100, 120, 100, 105, 100, 110, 130)
                .build();
        FixedTransactionCostModel costs = new FixedTransactionCostModel(1);
        for (boolean futureExit : new boolean[] { false, true }) {
            BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, 2, 8, costs, new ZeroCostModel());
            record.enter(0, numFactory.hundred(), numFactory.one());
            record.exit(2, numFactory.numOf(110), numFactory.one());
            record.enter(3, numFactory.hundred(), numFactory.one());
            record.exit(4, numFactory.numOf(120), numFactory.one());
            record.enter(5, numFactory.hundred(), numFactory.one());
            record.exit(6, numFactory.numOf(105), numFactory.one());
            record.enter(7, numFactory.hundred(), numFactory.one());
            if (futureExit) {
                record.exit(9, numFactory.numOf(130), numFactory.one());
            }
            CommissionsCriterion criterion = new CommissionsCriterion();
            // Four selected closed-trade fees plus the entry paid at 7, without a future
            // exit fee.
            assertNumEquals(5, criterion.calculate(series, record));
            AnalysisContext context = AnalysisContext.defaults()
                    .withPositionInclusionPolicy(PositionInclusionPolicy.FULLY_CONTAINED);
            assertNumEquals(4, criterion.calculate(series, record, AnalysisWindow.barRange(2, 8), context));
            // Explicit mark-to-market retains its modeled synthetic exit cost.
            assertNumEquals(6, criterion.calculate(series, record, AnalysisWindow.barRange(2, 8),
                    context.withOpenPositionHandling(OpenPositionHandling.MARK_TO_MARKET)));
        }
    }

    @Test
    public void boundedNativeFeesCountEveryRetainedOpenEntry() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 100, 100, 100, 110, 120)
                .build();
        FixedTransactionCostModel model = new FixedTransactionCostModel(1);
        for (boolean futureClosed : new boolean[] { false, true }) {
            TradingRecord bounded = new BaseTradingRecord(TradeType.BUY, 2, 7, model, new ZeroCostModel());
            TradingRecord unbounded = new BaseTradingRecord(TradeType.BUY, model, new ZeroCostModel());
            for (TradingRecord record : java.util.List.of(bounded, unbounded)) {
                record.operate(Trade.buyAt(3, series, numFactory.one(), model));
                record.operate(Trade.buyAt(5, series, numFactory.one(), model));
                if (futureClosed) {
                    record.operate(Trade.sellAt(8, series, numFactory.one(), model));
                    record.operate(Trade.sellAt(9, series, numFactory.one(), model));
                }
            }
            assertNumEquals(2, getCriterion().calculate(series, bounded));
            // Historical unbounded net-open convention is retained.
            assertNumEquals(futureClosed ? 4 : 1, getCriterion().calculate(series, unbounded));
            assertNumEquals(2, new TotalFeesCriterion().calculate(series, bounded));
        }
    }

    @Test
    public void boundedNativeEntryCommissionDoesNotDependOnFuturePartialExits() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 100, 100, 100, 110, 120)
                .build();
        FixedTransactionCostModel model = new FixedTransactionCostModel(1);
        for (boolean futurePartialExit : new boolean[] { false, true }) {
            TradingRecord record = new BaseTradingRecord(TradeType.BUY, 2, 7, model, new ZeroCostModel());
            record.operate(Trade.buyAt(3, series, numFactory.two(), model));
            if (futurePartialExit) {
                record.operate(Trade.sellAt(8, series, numFactory.one(), model));
            }
            assertNumEquals(1, getCriterion().calculate(series, record));
            assertNumEquals(1, new TotalFeesCriterion().calculate(series, record));
        }
    }

}
