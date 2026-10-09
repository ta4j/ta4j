/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.AnalysisCriterion.PositionFilter;
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.analysis.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.criteria.commissions.CommissionsCriterion;
import org.ta4j.core.criteria.helpers.AverageCriterion;
import org.ta4j.core.criteria.helpers.VarianceCriterion;
import org.ta4j.core.criteria.pnl.MaxConsecutiveProfitCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.rules.FixedRule;

public class AbstractAnalysisCriterionTest extends AbstractCriterionTest {

    private Strategy alwaysStrategy;

    private Strategy buyAndHoldStrategy;

    private List<Strategy> strategies;

    public AbstractAnalysisCriterionTest(NumFactory numFactory) {
        super(params -> new GrossReturnCriterion(), numFactory);
    }

    @BeforeEach
    public void setUp() {
        alwaysStrategy = new BaseStrategy(BooleanRule.TRUE, BooleanRule.TRUE);
        buyAndHoldStrategy = new BaseStrategy(new FixedRule(0), new FixedRule(4));
        strategies = new ArrayList<>();
        strategies.add(alwaysStrategy);
        strategies.add(buyAndHoldStrategy);
    }

    @Test
    public void bestShouldBeAlwaysOperateOnProfit() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(6.0, 9.0, 6.0, 6.0).build();
        var manager = new BarSeriesManager(series);
        Strategy bestStrategy = getCriterion().chooseBest(manager, TradeType.BUY, strategies);
        assertEquals(alwaysStrategy, bestStrategy);
    }

    @Test
    public void bestShouldBeBuyAndHoldOnLoss() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(6.0, 3.0, 6.0, 6.0).build();
        var manager = new BarSeriesManager(series, new TradeOnCurrentCloseModel());
        Strategy bestStrategy = getCriterion().chooseBest(manager, TradeType.BUY, strategies);
        assertEquals(buyAndHoldStrategy, bestStrategy);
    }

    @Test
    public void toStringMethod() {
        AbstractAnalysisCriterion c1 = new AverageReturnPerBarCriterion();
        assertEquals("Average Return Per Bar", c1.toString());
        AbstractAnalysisCriterion c2 = new EnterAndHoldCriterion(new GrossReturnCriterion());
        assertEquals("EnterAndHoldCriterion of GrossReturnCriterion", c2.toString());
        AbstractAnalysisCriterion c3 = new ReturnOverMaxDrawdownCriterion();
        assertEquals("Return Over Max Drawdown", c3.toString());
    }

    @Test
    public void directStartOnlyBoundsSelectFullyContainedPositions() {
        BarSeries series = boundedSeries();
        TradingRecord source = closedRecord(series, 2, null);
        ProjectionProbe probe = new ProjectionProbe();
        TradingRecord selected = probe.select(series, source);

        assertEquals(3, selected.getPositionCount());
        assertEquals(3, selected.getPositions().get(0).getEntry().getIndex());
        // The selected profits are 20, 5, and 30; gross returns multiply 1.2 * 1.05 *
        // 1.3.
        assertNumEquals(55, probe.calculate(series, source));
        assertNumEquals(1.638,
                new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE).calculate(series, selected));
        assertEquals(4, source.getPositionCount());
    }

    @Test
    public void directEndOnlyBoundsRespectTheSelectedOpenPositionHandling() {
        BarSeries series = boundedSeries();
        TradingRecord source = closedRecord(series, null, 8);
        ProjectionProbe probe = new ProjectionProbe();
        TradingRecord ignored = probe.select(series, source);
        TradingRecord marked = probe.select(series, source, OpenPositionHandling.MARK_TO_MARKET);

        // Profits 10, 20, and 5 are closed at 8; the 7→9 position is worth 10 at 8.
        assertEquals(3, ignored.getPositionCount());
        assertNumEquals(35, new NetProfitCriterion().calculate(series, ignored));
        assertEquals(4, marked.getPositionCount());
        assertNumEquals(45, new NetProfitCriterion().calculate(series, marked));
        assertEquals(8, marked.getPositions().get(3).getExit().getIndex());
        assertEquals(9, source.getPositions().get(3).getExit().getIndex());
    }

    @Test
    public void reversedDirectBoundsReturnAnEmptySelectedPopulation() {
        BarSeries series = boundedSeries();
        TradingRecord source = closedRecord(series, 8, 2);
        TradingRecord selected = new ProjectionProbe().select(series, source);

        assertEquals(4, source.getPositionCount());
        assertTrue(selected.getPositions().isEmpty());
        assertNumEquals(0, new NetProfitCriterion().calculate(series, selected));
    }

    @Test
    public void directBoundsClampToRetainedHistoryWithoutResettingAbsoluteIndices() {
        BarSeries series = boundedSeries();
        TradingRecord source = closedRecord(series, 0, 8);
        series.setMaximumBarCount(6);
        TradingRecord selected = new ProjectionProbe().select(series, source);

        assertEquals(4, series.getBeginIndex());
        assertEquals(9, series.getEndIndex());
        assertEquals(1, selected.getPositionCount());
        assertEquals(5, selected.getPositions().get(0).getEntry().getIndex());
        assertEquals(6, selected.getPositions().get(0).getExit().getIndex());
        assertSame(source.getPositions().get(2), selected.getPositions().get(0));
        assertNumEquals(5, new NetProfitCriterion().calculate(series, selected));
    }

    @Test
    public void unboundedRecordsRetainTheirOriginalPopulationAndIdentity() {
        BarSeries series = boundedSeries();
        TradingRecord source = closedRecord(series, null, null);
        ProjectionProbe probe = new ProjectionProbe();

        assertSame(source, probe.select(series, source));
        assertSame(source, probe.select(series, source, OpenPositionHandling.MARK_TO_MARKET));
        // All profits [10, 20, 5, 30] remain present, including the final exit at 9.
        assertNumEquals(65, probe.calculate(series, source));
        assertNumEquals(1.8018,
                new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE).calculate(series, source));
    }

    @Test
    public void composedCriteriaPreserveExplicitExitAndFullyContainedPopulations() {
        BarSeries series = boundedSeries();
        TradingRecord source = closedRecord(series, null, null);
        AnalysisWindow window = AnalysisWindow.barRange(2, 8);
        AnalysisContext exitInWindow = AnalysisContext.defaults().withOpenPositionHandling(OpenPositionHandling.IGNORE);
        AnalysisContext fullyContained = exitInWindow
                .withPositionInclusionPolicy(PositionInclusionPolicy.FULLY_CONTAINED);
        ProjectionProbe probe = new ProjectionProbe();
        AverageCriterion average = new AverageCriterion(new NetProfitLossCriterion());
        VarianceCriterion variance = new VarianceCriterion(new NetProfitLossCriterion());

        // EXIT keeps profits [10, 20, 5]; FULLY_CONTAINED keeps [20, 5].
        assertNumEquals(35, probe.calculate(series, source, window, exitInWindow));
        assertNumEquals(25, probe.calculate(series, source, window, fullyContained));
        assertNumEquals(35.0 / 3, average.calculate(series, source, window, exitInWindow));
        assertNumEquals(12.5, average.calculate(series, source, window, fullyContained));
        assertNumEquals(350.0 / 9, variance.calculate(series, source, window, exitInWindow));
        assertNumEquals(56.25, variance.calculate(series, source, window, fullyContained));
    }

    @Test
    public void directSelectionRetainsSourceOrderWhileExplicitProjectionKeepsItsOrder() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 95, 100, 120, 100, 100, 100, 110)
                .build();
        TradingRecord source = new BaseTradingRecord(TradeType.BUY, 0, 8, new ZeroCostModel(), new ZeroCostModel());
        source.operate(Trade.buyAt(7, series, numFactory.one()));
        source.operate(Trade.sellAt(8, series, numFactory.one()));
        source.operate(Trade.buyAt(1, series, numFactory.one()));
        source.operate(Trade.sellAt(2, series, numFactory.one()));
        source.operate(Trade.buyAt(3, series, numFactory.one()));
        source.operate(Trade.sellAt(4, series, numFactory.one()));
        TradingRecord selected = new ProjectionProbe().select(series, source);
        MaxConsecutiveProfitCriterion profitStreak = new MaxConsecutiveProfitCriterion();
        NumberOfConsecutivePositionsCriterion countStreak = new NumberOfConsecutivePositionsCriterion(
                PositionFilter.PROFIT);

        assertEquals(List.of(7, 1, 3),
                selected.getPositions().stream().map(position -> position.getEntry().getIndex()).toList());
        for (int index = 0; index < source.getPositionCount(); index++) {
            assertSame(source.getPositions().get(index), selected.getPositions().get(index));
        }
        // Native order [10, -5, 20] has isolated wins; explicit order [-5, 20, 10]
        // joins them.
        assertNumEquals(20, profitStreak.calculate(series, source));
        assertNumEquals(1, countStreak.calculate(series, source));
        AnalysisWindow window = AnalysisWindow.barRange(0, 8);
        AnalysisContext context = AnalysisContext.defaults();
        assertNumEquals(30, profitStreak.calculate(series, source, window, context));
        assertNumEquals(2, countStreak.calculate(series, source, window, context));
    }

    @Test
    public void emptySeriesExplicitWindowRetainsNativeUnboundedOpenEntryFee() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        FixedTransactionCostModel costs = new FixedTransactionCostModel(1);
        TradingRecord source = new BaseTradingRecord(TradeType.BUY, costs, new ZeroCostModel());
        source.operate(Trade.buyAt(0, numFactory.hundred(), numFactory.one(), costs));
        CommissionsCriterion commissions = new CommissionsCriterion();

        assertSame(source, new ProjectionProbe().select(series, source));
        assertNumEquals(1, commissions.calculate(series, source));
        assertNumEquals(1,
                commissions.calculate(series, source, AnalysisWindow.barRange(0, 0), AnalysisContext.defaults()));
        assertTrue(source.getCurrentPosition().isOpened());
    }

    @Test
    public void boundedPartialLotRetainsNativeClosedAndOpenFeeCalculation() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        FixedTransactionCostModel costs = new FixedTransactionCostModel(1);
        TradingRecord bounded = new BaseTradingRecord(TradeType.BUY, 0, 1, costs, new ZeroCostModel());
        TradingRecord unbounded = new BaseTradingRecord(TradeType.BUY, costs, new ZeroCostModel());
        for (TradingRecord record : List.of(bounded, unbounded)) {
            record.operate(Trade.buyAt(0, numFactory.hundred(), numFactory.two(), costs));
            record.operate(Trade.sellAt(1, numFactory.numOf(110), numFactory.one(), costs));
        }
        CommissionsCriterion commissions = new CommissionsCriterion();

        assertEquals(1, bounded.getPositionCount());
        assertNumEquals(1, bounded.getCurrentPosition().getEntry().getAmount());
        // Preserve the native model: closed-position cost 2 plus residual-open cost 1.
        assertNumEquals(3, commissions.calculate(series, unbounded));
        assertNumEquals(3, commissions.calculate(series, bounded));
        assertTrue(bounded.getCurrentPosition().isOpened());
    }

    @Test
    public void equalEntryPartialClosesKeepActualAndSyntheticExitsDistinct() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 110, 120, 130)
                .build();
        TradingRecord source = new BaseTradingRecord(TradeType.BUY, 0, 6, new ZeroCostModel(), new ZeroCostModel());
        source.enter(1, numFactory.hundred(), numFactory.two());
        source.exit(7, numFactory.numOf(130), numFactory.one());
        source.exit(5, numFactory.numOf(110), numFactory.one());

        TradingRecord selected = new ProjectionProbe().select(series, source, OpenPositionHandling.MARK_TO_MARKET);

        // Equal entry slices: the first is still open at6, the second actually closed
        // at5.
        assertEquals(2, selected.getPositionCount());
        assertEquals(6, selected.getPositions().get(0).getExit().getIndex());
        assertEquals(5, selected.getPositions().get(1).getExit().getIndex());
        assertNumEquals(30, new NetProfitCriterion().calculate(series, selected));
        assertEquals(7, source.getPositions().get(0).getExit().getIndex());
        assertEquals(5, source.getPositions().get(1).getExit().getIndex());
    }

    @Test
    public void customTradeEqualityDoesNotChangeDirectOrderOrPositionSnapshots() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 95, 100, 120, 100, 100, 100, 110)
                .build();
        List<Position> positions = new ArrayList<>();
        for (int[] leg : new int[][] { { 7, 8 }, { 1, 2 }, { 3, 4 } }) {
            positions.add(new Position(new IdentityTrade(Trade.buyAt(leg[0], series, numFactory.one())),
                    new IdentityTrade(Trade.sellAt(leg[1], series, numFactory.one()))));
        }
        TradingRecord source = new BaseTradingRecord(TradeType.BUY, 0, 8, new ZeroCostModel(), new ZeroCostModel()) {
            @Override
            public List<Position> getPositions() {
                return List.copyOf(positions);
            }
        };
        TradingRecord selected = new ProjectionProbe().select(series, source);

        assertNumEquals(20, new MaxConsecutiveProfitCriterion().calculate(series, source));
        assertEquals(List.of(7, 1, 3),
                selected.getPositions().stream().map(position -> position.getEntry().getIndex()).toList());
        for (int index = 0; index < positions.size(); index++) {
            assertSame(positions.get(index), selected.getPositions().get(index));
        }
    }

    // A valid public Trade adapter whose equality is intentionally identity-based.
    private static final class IdentityTrade implements Trade {
        private final Trade delegate;

        private IdentityTrade(Trade delegate) {
            this.delegate = delegate;
        }

        @Override
        public TradeType getType() {
            return delegate.getType();
        }

        @Override
        public int getIndex() {
            return delegate.getIndex();
        }

        @Override
        public Num getPricePerAsset() {
            return delegate.getPricePerAsset();
        }

        @Override
        public Num getNetPrice() {
            return delegate.getNetPrice();
        }

        @Override
        public Num getAmount() {
            return delegate.getAmount();
        }

        @Override
        public Num getCost() {
            return delegate.getCost();
        }

        @Override
        public CostModel getCostModel() {
            return delegate.getCostModel();
        }
    }

    @Test
    public void equalActualAndMarkedExitsRetainTheirSourceOccurrences() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 110, 120, 130)
                .build();
        TradingRecord source = new BaseTradingRecord(TradeType.BUY, 0, 6, new ZeroCostModel(), new ZeroCostModel());
        source.enter(1, numFactory.hundred(), numFactory.two());
        source.exit(7, numFactory.numOf(130), numFactory.one());
        source.exit(6, numFactory.numOf(120), numFactory.one());

        TradingRecord selected = new ProjectionProbe().select(series, source, OpenPositionHandling.MARK_TO_MARKET);

        // Equal-valued actual and synthetic exits still represent distinct source
        // occurrences.
        assertEquals(2, selected.getPositionCount());
        assertSame(source.getPositions().get(1), selected.getPositions().get(1));
        assertNumEquals(40, new NetProfitCriterion().calculate(series, selected));
    }

    private BarSeries boundedSeries() {
        return new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 110, 100, 120, 100, 105, 100, 110, 130)
                .build();
    }

    private TradingRecord closedRecord(BarSeries series, Integer startIndex, Integer endIndex) {
        TradingRecord record = new BaseTradingRecord(TradeType.BUY, startIndex, endIndex, new ZeroCostModel(),
                new ZeroCostModel());
        record.operate(Trade.buyAt(0, series, numFactory.one()));
        record.operate(Trade.sellAt(2, series, numFactory.one()));
        record.operate(Trade.buyAt(3, series, numFactory.one()));
        record.operate(Trade.sellAt(4, series, numFactory.one()));
        record.operate(Trade.buyAt(5, series, numFactory.one()));
        record.operate(Trade.sellAt(6, series, numFactory.one()));
        record.operate(Trade.buyAt(7, series, numFactory.one()));
        record.operate(Trade.sellAt(9, series, numFactory.one()));
        return record;
    }

    private static final class ProjectionProbe extends AbstractAnalysisCriterion {
        private TradingRecord select(BarSeries series, TradingRecord source) {
            return boundedTradingRecord(series, source);
        }

        private TradingRecord select(BarSeries series, TradingRecord source, OpenPositionHandling handling) {
            return boundedTradingRecord(series, source, handling);
        }

        @Override
        public Num calculate(BarSeries series, TradingRecord source) {
            return new NetProfitCriterion().calculate(series, select(series, source));
        }

        @Override
        public Num calculate(BarSeries series, Position position) {
            return new NetProfitCriterion().calculate(series, position);
        }

        @Override
        public boolean betterThan(Num first, Num second) {
            return first.isGreaterThan(second);
        }
    }
}
