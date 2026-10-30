/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.portfolio.PortfolioFixtures.START;
import static org.ta4j.core.portfolio.PortfolioFixtures.assertNumClose;
import static org.ta4j.core.portfolio.PortfolioFixtures.series;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.ConcurrentBarSeriesBuilder;
import org.ta4j.core.Position;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.WeightedValue;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.backtest.PositionSizer;
import org.ta4j.core.backtest.StopLimitExecutionModel;
import org.ta4j.core.backtest.TradeExecutionModel;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.backtest.TradeOnNextOpenModel;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.portfolio.PortfolioSnapshot.RebalanceStatus;

public class PortfolioSeriesManagerTest {

    private static final double TOLERANCE = 1e-6;

    @Test
    public void defaultRunIsBuyAndHold() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 110, 120 }, new double[] { 50, 40, 60 });

        PortfolioExecutionResult result = manager.run(weights(0.6, 0.4), 1000);

        List<PortfolioSnapshot> snapshots = result.getSnapshots();
        assertEquals(List.of(RebalanceStatus.COMPLETED, RebalanceStatus.NOT_SCHEDULED, RebalanceStatus.NOT_SCHEDULED),
                snapshots.stream().map(PortfolioSnapshot::getRebalanceStatus).toList());
        assertNumEquals(6, snapshots.get(0).getHoldings().get("ALPHA"));
        assertNumEquals(8, snapshots.get(0).getHoldings().get("BETA"));
        assertNumEquals(0, snapshots.get(0).getCash());
        assertNumEquals(1, snapshots.get(0).getTurnover());
        assertNumEquals(-0.02, snapshots.get(1).getPeriodReturn());
        assertNumEquals(1200, result.getFinalValue());
        assertNumEquals(0.2, result.getTotalReturn());
        assertEquals(List.of("ALPHA", "BETA"), List.copyOf(result.getFinalWeights().keySet()));
        assertNumEquals(0.6, result.getFinalWeights().get("ALPHA"));
    }

    @Test
    public void everyBarRebalanceRestoresTargetWeightsAfterPriceDrift() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 200 }, new double[] { 100, 50 });

        PortfolioExecutionResult result = manager.run(weights(0.5, 0.5), 1000, RebalancePolicy.everyBar());

        PortfolioSnapshot last = result.getFinalSnapshot();
        assertNumEquals(3.125, last.getHoldings().get("ALPHA"));
        assertNumEquals(12.5, last.getHoldings().get("BETA"));
        assertNumEquals(1250, last.getPortfolioValue());
        assertNumEquals(750, last.getTradedNotional());
        assertNumEquals(0.6, last.getTurnover());
        assertEquals(RebalanceStatus.COMPLETED, last.getRebalanceStatus());
    }

    @Test
    public void allocationRejectsUnionSnapshotsWithMissingBars() {
        BarSeries alpha = series("ALPHA", 100);
        alpha.addBar(series("LATER", DoubleNumFactory.getInstance(), 2, "120").getBar(0));
        PortfolioSeries union = PortfolioSeries.union(Map.of("ALPHA", alpha, "BETA", series("BETA", 100, 200, 100)));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new PortfolioSeriesManager(union).run(weights(0.5, 0.5), 1000, RebalancePolicy.everyBar()));
        assertTrue(error.getMessage().contains("ALPHA"));
    }

    @Test
    public void allocationAcceptsUnionSnapshotsWithoutGaps() {
        PortfolioSeries union = PortfolioSeries
                .union(Map.of("ALPHA", series("ALPHA", 100, 110), "BETA", series("BETA", 100, 90)));
        assertEquals(2, new PortfolioSeriesManager(union).run(weights(0.5, 0.5), 1000).getSnapshots().size());
    }

    @Test
    public void retainedPolicyContextsPreserveEachBarsPreTradeState() {
        PortfolioSeries series = new PortfolioSeries(series("ALPHA", 100, 200, 50), series("BETA", 100, 50, 150));
        PortfolioSeriesManager manager = new PortfolioSeriesManager(series);
        List<RebalancePolicy.Context> contexts = new ArrayList<>();

        manager.run(weights(0.5, 0.25), 1000, context -> {
            if (context.getIndex() == 0) {
                assertNumEquals(0.75, context.getMaxDrift());
            }
            contexts.add(context);
            return true;
        });

        double[][] expected = { { 1000, 0, 0, 1, 0.75 }, { 1375, 8.0 / 11, 1.0 / 11, 2.0 / 11, 5.0 / 22 },
                { 1546.875, 1.0 / 9, 2.0 / 3, 2.0 / 9, 5.0 / 12 } };
        for (int index = 0; index < expected.length; index++) {
            RebalancePolicy.Context context = contexts.get(index);
            assertEquals(index, context.getIndex());
            assertEquals(series.getEndTimes().get(index), context.getEndTime());
            assertNumClose(expected[index][0], context.getPortfolioValue(), TOLERANCE);
            assertNumClose(expected[index][1], context.getAssetWeight("ALPHA"), TOLERANCE);
            assertNumClose(expected[index][2], context.getAssetWeight("BETA"), TOLERANCE);
            assertNumClose(expected[index][3], context.getCashWeight(), TOLERANCE);
            assertNumClose(expected[index][4], context.getMaxDrift(), TOLERANCE);
            assertNumEquals(0.5, context.getTargetWeight("ALPHA"));
            assertNumEquals(0.25, context.getTargetWeight("BETA"));
        }
    }

    @Test
    public void scheduleControlsInitialInvestment() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 100, 100, 110 }, new double[] { 50, 50, 50, 50 });

        PortfolioExecutionResult delayed = manager.run(weights(0.5, 0.5), 1000, RebalancePolicy.onIndexes(2));
        PortfolioExecutionResult investedThenRebalanced = manager.run(weights(0.5, 0.5), 1000,
                RebalancePolicy.atStart().or(RebalancePolicy.onIndexes(2)));

        assertNumEquals(1000, delayed.getSnapshots().get(1).getCash());
        assertEquals(RebalanceStatus.NOT_SCHEDULED, delayed.getSnapshots().get(1).getRebalanceStatus());
        assertEquals(RebalanceStatus.COMPLETED, delayed.getSnapshots().get(2).getRebalanceStatus());
        assertNumEquals(0, investedThenRebalanced.getSnapshots().get(1).getCash());
        assertNumEquals(0, investedThenRebalanced.getSnapshots().get(2).getTradedNotional());
        assertNumEquals(1050, delayed.getFinalValue());
    }

    @Test
    public void driftBandTradesOnlyWhenWeightsLeaveTheBand() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 101, 110, 111 },
                new double[] { 100, 100, 100, 100 });

        PortfolioExecutionResult result = manager.run(weights(0.5, 0.5), 1000, RebalancePolicy.whenDriftExceeds(0.02));

        assertEquals(
                List.of(RebalanceStatus.COMPLETED, RebalanceStatus.NOT_SCHEDULED, RebalanceStatus.COMPLETED,
                        RebalanceStatus.NOT_SCHEDULED),
                result.getSnapshots().stream().map(PortfolioSnapshot::getRebalanceStatus).toList());
        assertNumClose(0.5, result.getSnapshots().get(2).getAssetWeight("ALPHA"), TOLERANCE);
    }

    @Test
    public void driftBandAvoidsFixedFeesForNegligibleDrift() {
        CostModel fixedFee = new FixedTransactionCostModel(50);
        PortfolioSeriesManager manager = new PortfolioSeriesManager(
                new PortfolioSeries(series("ALPHA", 100, 101), series("BETA", 100, 100)), fixedFee);

        PortfolioExecutionResult everyBar = manager.run(weights(0.01, 0.99), 1000, RebalancePolicy.everyBar());
        PortfolioExecutionResult banded = manager.run(weights(0.01, 0.99), 1000,
                RebalancePolicy.everyBar().and(RebalancePolicy.whenDriftExceeds(0.05)));

        assertNumEquals(200, everyBar.getTotalTransactionCost());
        assertNumEquals(100, banded.getTotalTransactionCost());
        assertEquals(RebalanceStatus.NOT_SCHEDULED, banded.getFinalSnapshot().getRebalanceStatus());
    }

    @Test
    public void singleAssetWithCashSleeveIsABenchmark() {
        PortfolioSeriesManager manager = new PortfolioSeriesManager(new PortfolioSeries(series("SPY", 100, 110)));

        PortfolioExecutionResult buyAndHold = manager.run(new PortfolioAllocation(Map.of("SPY", 0.6)), 1000);
        PortfolioExecutionResult rebalanced = manager.run(new PortfolioAllocation(Map.of("SPY", 0.6)), 1000,
                RebalancePolicy.everyBar());

        assertNumEquals(1060, buyAndHold.getFinalValue());
        assertNumEquals(400, buyAndHold.getFinalSnapshot().getCash());
        assertNumEquals(0.4, rebalanced.getFinalSnapshot().getCashWeight());
        assertNumEquals(0.6, rebalanced.getFinalSnapshot().getAssetWeight("SPY"));
    }

    @Test
    public void proportionalCostsAreFundedFromThePostCostValue() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 100 }, new double[] { 50, 50 },
                new LinearTransactionCostModel(0.01));

        PortfolioSnapshot fullyInvested = manager.run(weights(0.6, 0.4), 1000).getSnapshots().getFirst();
        PortfolioSnapshot cashSleeve = manager.run(weights(0.6, 0.3), 1000).getSnapshots().getFirst();

        assertNumClose(0, fullyInvested.getCash(), TOLERANCE);
        assertNumClose(990.0990099, fullyInvested.getPortfolioValue(), TOLERANCE);
        assertNumClose(9.9009901, fullyInvested.getTransactionCost(), TOLERANCE);
        assertNumClose(-0.00990099, fullyInvested.getPeriodReturn(), TOLERANCE);
        assertNumClose(991.0802775, cashSleeve.getPortfolioValue(), TOLERANCE);
        assertNumClose(8.9197225, cashSleeve.getTransactionCost(), TOLERANCE);
        assertNumClose(0.6, cashSleeve.getAssetWeight("ALPHA"), TOLERANCE);
        assertNumClose(0.1, cashSleeve.getCashWeight(), TOLERANCE);
        assertEquals(RebalanceStatus.COMPLETED, cashSleeve.getRebalanceStatus());
    }

    @Test
    public void flatPricesWithFixedFeesNeverChurnAfterTheInitialInvestment() {
        for (NumFactory numFactory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            PortfolioSeries series = new PortfolioSeries(series("ALPHA", numFactory, 100, 100, 100, 100),
                    series("BETA", numFactory, 100, 100, 100, 100));
            PortfolioSeriesManager manager = new PortfolioSeriesManager(series, new FixedTransactionCostModel(5));

            PortfolioExecutionResult result = manager.run(weights(0.6, 0.3), 1000, RebalancePolicy.everyBar());

            assertNumEquals(10, result.getSnapshots().getFirst().getTransactionCost());
            for (PortfolioSnapshot snapshot : result.getSnapshots()) {
                assertNumClose(990, snapshot.getPortfolioValue(), TOLERANCE);
                assertNumClose(0.1, snapshot.getCashWeight(), TOLERANCE);
                assertEquals(RebalanceStatus.COMPLETED, snapshot.getRebalanceStatus());
            }
            for (PortfolioSnapshot snapshot : result.getSnapshots().subList(1, 4)) {
                assertNumEquals(0, snapshot.getTradedNotional());
                assertNumEquals(0, snapshot.getTransactionCost());
            }
        }
    }

    @Test
    public void executionIsIndependentOfAssetPresentationOrder() {
        CostModel fixedFee = new FixedTransactionCostModel(50);
        BarSeries alpha = series("ALPHA", 100, 101);
        BarSeries beta = series("BETA", 100, 100);
        PortfolioAllocation allocation = weights(0.01, 0.99);

        PortfolioSnapshot alphaFirst = new PortfolioSeriesManager(new PortfolioSeries(alpha, beta), fixedFee)
                .run(allocation, 1000, RebalancePolicy.everyBar())
                .getFinalSnapshot();
        PortfolioSnapshot betaFirst = new PortfolioSeriesManager(new PortfolioSeries(beta, alpha), fixedFee)
                .run(allocation, 1000, RebalancePolicy.everyBar())
                .getFinalSnapshot();

        for (String asset : List.of("ALPHA", "BETA")) {
            assertNumEquals(alphaFirst.getHoldings().get(asset), betaFirst.getHoldings().get(asset));
        }
        assertNumEquals(alphaFirst.getCash(), betaFirst.getCash());
        assertNumEquals(alphaFirst.getTransactionCost(), betaFirst.getTransactionCost());
        assertEquals(alphaFirst.getRebalanceStatus(), betaFirst.getRebalanceStatus());
        assertTrue(alphaFirst.getCash().isPositiveOrZero());
    }

    @Test
    public void unaffordableRebalanceIsReportedAsSkippedWithoutTrading() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 100 }, new double[] { 50, 50 },
                new FixedTransactionCostModel(60));

        PortfolioExecutionResult result = manager.run(weights(0.5, 0.5), 100);

        PortfolioSnapshot first = result.getSnapshots().getFirst();
        assertEquals(RebalanceStatus.SKIPPED, first.getRebalanceStatus());
        assertNumEquals(100, first.getCash());
        assertNumEquals(0, first.getTransactionCost());
        assertEquals(List.of(first), result.getSnapshots(RebalanceStatus.SKIPPED));
        assertEquals(1, result.getRebalanceCount(RebalanceStatus.SKIPPED));
    }

    @Test
    public void accountingIsInvariantToMonetaryScale() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        PortfolioSnapshot tiny = scaledRun(numFactory, "1E-14", "1E-13");
        PortfolioSnapshot ordinary = scaledRun(numFactory, "100", "1000");

        assertNumEquals(ordinary.getAssetWeight("ALPHA"), tiny.getAssetWeight("ALPHA"), 1e-12);
        assertNumEquals(ordinary.getAssetWeight("BETA"), tiny.getAssetWeight("BETA"), 1e-12);
        assertNumEquals(ordinary.getCashWeight(), tiny.getCashWeight(), 1e-12);
        // Precision is set by the solver's relative tolerance (1e-12 of portfolio
        // value).
        assertNumClose(0, tiny.getCashWeight(), 1e-11);
        assertNumEquals(ordinary.getTransactionCost().dividedBy(numFactory.numOf(1000)),
                tiny.getTransactionCost().dividedBy(numFactory.numOf("1E-13")), 1e-12);
    }

    @Test
    public void convertsInputsToThePortfolioNumFactory() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 110 }, new double[] { 50, 55 });
        PortfolioAllocation decimalWeights = new PortfolioAllocation(
                List.of(new WeightedValue<>("ALPHA", DecimalNumFactory.getInstance().numOf("0.6")),
                        new WeightedValue<>("BETA", DecimalNumFactory.getInstance().numOf("0.4"))));

        PortfolioExecutionResult result = manager.run(decimalWeights, DecimalNumFactory.getInstance().numOf(1000),
                RebalancePolicy.atStart());

        assertNumEquals(1100, result.getFinalValue());
        assertTrue(DoubleNumFactory.getInstance().produces(result.getFinalValue()));
    }

    @Test
    public void rejectsInvalidRunInputs() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 100 }, new double[] { 50, 50 });
        PortfolioAllocation allocation = weights(0.5, 0.5);

        assertThrows(IllegalArgumentException.class,
                () -> manager.run(new PortfolioAllocation(Map.of("MISSING", 0.5)), 1000));
        assertThrows(NullPointerException.class, () -> manager.run(allocation, (Num) null));
        assertThrows(IllegalArgumentException.class, () -> manager.run(allocation, 0));
        assertThrows(NullPointerException.class, () -> manager.run(allocation, 1000, null));
    }

    @Test
    public void rejectsNonPositivePricesWithAssetAndTime() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 0 }, new double[] { 50, 50 });

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> manager.run(weights(0.5, 0.5), 1000));

        assertTrue(exception.getMessage().contains("ALPHA at 2026-01-02T00:00:00Z"));
    }

    @Test
    public void strategyRunRanksAlignedBarsAndLimitsOpenAssets() {
        Map<String, BarSeries> members = new LinkedHashMap<>();
        members.put("BETA", series("BETA", 50, 60, 20, 21));
        members.put("ALPHA", series("ALPHA", 50, 40, 20, 22));
        members.put("GAMMA", series("GAMMA", 30, 30, 99, 23));
        members.put("DELTA", series("DELTA", 10, 20, 30, 24));
        members.put("EPSILON", series("EPSILON", DoubleNumFactory.getInstance(), 2, "100", "25"));
        PortfolioSeriesView view = PortfolioSeries.union(members);
        List<Integer> observedOpenCounts = new ArrayList<>();
        PortfolioRule schedule = new EndTimeRule(time -> ChronoUnit.DAYS.between(START, time) < 3);
        PortfolioRule rank = new AssetRankRule(new IndicatorAssetRanker(ClosePriceIndicator::new, true), 1);
        PortfolioRule track = (asset, index, record) -> {
            observedOpenCounts.add(record.openPositionCount());
            return true;
        };
        PortfolioStrategy strategy = new BasePortfolioStrategy("rotation",
                schedule.and(rank).and(track).and(new MaxOpenPositionsRule(2)), (asset, index, record) -> false, 0,
                TradeType.BUY);

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, view.numFactory().two());

        assertEquals(List.of(0, 1, 2), observedOpenCounts);
        Position alpha = result.record("ALPHA").getPositions().getFirst();
        Position beta = result.record("BETA").getPositions().getFirst();
        assertEquals(0, alpha.getEntry().getIndex());
        assertEquals(1, beta.getEntry().getIndex());
        assertNumEquals(50, alpha.getEntry().getPricePerAsset());
        assertNumEquals(60, beta.getEntry().getPricePerAsset());
        assertNumEquals(22, alpha.getExit().getPricePerAsset());
        assertNumEquals(21, beta.getExit().getPricePerAsset());
        assertNumEquals(2, alpha.getEntry().getAmount());
        assertTrue(result.record("GAMMA").getPositions().isEmpty());
        assertTrue(result.record("DELTA").getPositions().isEmpty());
        assertTrue(result.record("EPSILON").getPositions().isEmpty());
        assertEquals(0, result.openPositionCount());
    }

    @Test
    public void executionAndSizingCannotSeeBarsAfterIntersectionWindow() {
        BarSeries alpha = series("ALPHA", 10, 11, 1000);
        BarSeries beta = series("BETA", 20, 21);
        PortfolioSeriesView view = new PortfolioSeries(alpha, beta);
        PortfolioStrategy strategy = new BasePortfolioStrategy("window", (asset, index, record) -> index == 1,
                (asset, index, record) -> false);
        List<String> sized = new ArrayList<>();
        PositionSizer sizer = context -> {
            sized.add(context.barSeries().getName());
            assertEquals(1, context.signalIndex());
            assertEquals(1, context.barSeries().getEndIndex());
            assertEquals("window", context.strategy().getName());
            assertTrue(context.strategy().shouldEnter(1, context.tradingRecord()));
            return context.numFactory().one();
        };

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, sizer,
                new TradeOnNextOpenModel());

        assertEquals(List.of("ALPHA", "BETA"), sized);
        assertTrue(result.record("ALPHA").getPositions().isEmpty());
        assertTrue(result.record("BETA").getPositions().isEmpty());
        assertEquals(0, result.openPositionCount());
    }

    @Test
    public void strategyRunDispatchesExecutionLifecyclePerAvailableAssetBar() {
        Map<String, BarSeries> members = new LinkedHashMap<>();
        members.put("ALPHA", series("ALPHA", 10, 11, 12));
        members.put("BETA", series("BETA", DoubleNumFactory.getInstance(), 1, "20", "21"));
        PortfolioSeriesView view = PortfolioSeries.union(members);
        PortfolioStrategy strategy = new BasePortfolioStrategy("source-index",
                (asset, index, record) -> record.series().getSourceIndex(asset, index) == 0,
                (asset, index, record) -> record.series().getSourceIndex(asset, index) == 1);
        List<String> calls = new ArrayList<>();
        TradeExecutionModel immediate = new TradeOnCurrentCloseModel();
        TradeExecutionModel execution = new TradeExecutionModel() {
            @Override
            public void onBar(int index, TradingRecord record, BarSeries bars) {
                calls.add("bar:" + bars.getName() + ":" + index);
            }

            @Override
            public void execute(int index, TradingRecord record, BarSeries bars, Num amount) {
                immediate.execute(index, record, bars, amount);
            }

            @Override
            public void onRunEnd(int index, TradingRecord record, BarSeries bars) {
                calls.add("end:" + bars.getName() + ":" + index);
            }
        };

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, PositionSizer.fixed(),
                execution);

        assertEquals(List.of("bar:ALPHA:0", "bar:ALPHA:1", "bar:BETA:0", "bar:ALPHA:2", "bar:BETA:1", "end:ALPHA:2",
                "end:BETA:1"), calls);
        assertNumEquals(11, result.record("ALPHA").getPositions().getFirst().getExit().getPricePerAsset());
        assertNumEquals(21, result.record("BETA").getPositions().getFirst().getExit().getPricePerAsset());
        assertEquals(0, result.openPositionCount());
    }

    @Test
    public void removedAssetReleasesCapacityBeforeLaterDecisions() {
        Map<String, BarSeries> members = new LinkedHashMap<>();
        members.put("ALPHA", series("ALPHA", 10, 11, 12));
        members.put("BETA", series("BETA", 20, 21, 22));
        LivePortfolioSeries view = new LivePortfolioSeries(members);
        PortfolioRule limit = new MaxOpenPositionsRule(1);
        PortfolioStrategy strategy = new BasePortfolioStrategy("removal", (asset, index,
                record) -> (asset.equals("ALPHA") ? index == 0 : index == 2) && limit.isSatisfied(asset, index, record),
                (asset, index, record) -> {
                    if (asset.equals("ALPHA") && index == 1) {
                        view.removeAsset(asset);
                    }
                    return false;
                });
        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, view.numFactory().one());
        assertEquals(1, result.record("ALPHA").getPositions().getFirst().getExit().getIndex());
        assertEquals(2, result.record("BETA").getPositions().getFirst().getEntry().getIndex());
    }

    @Test
    public void removedAssetReleasesDeferredEntryCapacity() {
        Map<String, BarSeries> members = new LinkedHashMap<>();
        members.put("ALPHA", series("ALPHA", 10, 11, 12));
        members.put("BETA", series("BETA", 20, 21, 22));
        LivePortfolioSeries view = new LivePortfolioSeries(members);
        PortfolioRule limit = new MaxOpenPositionsRule(1);
        PortfolioStrategy strategy = new BasePortfolioStrategy("removal-pending", (asset, index, record) -> {
            if (asset.equals("ALPHA") && index == 0) {
                view.removeAsset(asset);
                return true;
            }
            return asset.equals("BETA") && index == 1 && limit.isSatisfied(asset, index, record);
        }, (asset, index, record) -> false);
        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, PositionSizer.fixed(),
                new TradeOnNextOpenModel());
        assertTrue(result.record("ALPHA").getPositions().isEmpty());
        assertEquals(2, result.record("BETA").getPositions().getFirst().getEntry().getIndex());
    }

    @Test
    public void reorderedLiveBarsExecuteInChronologicalOrder() {
        ConcurrentBarSeries alpha = new ConcurrentBarSeriesBuilder().withName("ALPHA")
                .withNumFactory(DoubleNumFactory.getInstance())
                .withBars(series("ALPHA", 10, 11, 12).getBarData())
                .build();
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("ALPHA", alpha));
        alpha.replaceBar(0, series("LATER", DoubleNumFactory.getInstance(), 3, "30").getBar(0));
        alpha.replaceBar(2, series("EARLIER", DoubleNumFactory.getInstance(), 1, "21").getBar(0));
        view.advance();
        PortfolioStrategy strategy = new BasePortfolioStrategy("reordered", (asset, index, record) -> {
            if (index == 1) {
                assertEquals(0, record.series().getSourceIndex(asset, index));
                assertNumEquals(21, record.series().getBarSeries(asset).getBar(0).getClosePrice());
                return true;
            }
            return false;
        }, (asset, index, record) -> false);
        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, view.numFactory().one());
        Position position = result.record("ALPHA").getPositions().getFirst();
        assertNumEquals(21, position.getEntry().getPricePerAsset());
        assertNumEquals(30, position.getExit().getPricePerAsset());
    }

    @Test
    public void removedAssetIsFinalizedAtItsLastProcessedBar() {
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("ALPHA", series("ALPHA", 10, 11, 12)));
        List<Integer> endings = new ArrayList<>();
        TradeExecutionModel immediate = new TradeOnCurrentCloseModel();
        TradeExecutionModel model = new TradeExecutionModel() {
            @Override
            public void execute(int index, TradingRecord record, BarSeries bars, Num amount) {
                immediate.execute(index, record, bars, amount);
            }

            @Override
            public void onRunEnd(int index, TradingRecord record, BarSeries bars) {
                endings.add(index);
            }
        };
        PortfolioStrategy strategy = new BasePortfolioStrategy("removed", (asset, index, record) -> index == 0,
                (asset, index, record) -> {
                    view.removeAsset(asset);
                    return false;
                });

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, PositionSizer.fixed(), model);

        assertEquals(List.of(1), endings);
        assertEquals(0, result.openPositionCount());
        assertEquals(1, result.record("ALPHA").getPositions().getFirst().getExit().getIndex());
        assertTrue(result.enter("ALPHA", 2, view.numFactory().one(), view.numFactory().one()));
        assertEquals(1, result.openPositionCount());
    }

    @Test
    public void removedAssetExpiresPendingOrder() {
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("ALPHA", liquidSeries("ALPHA", 1, 3)));
        NumFactory factory = view.numFactory();
        StopLimitExecutionModel model = new StopLimitExecutionModel(factory.zero(), factory.zero(), factory.one(), 3,
                TradeExecutionModel.PriceSource.CURRENT_CLOSE);
        PortfolioStrategy strategy = new BasePortfolioStrategy("removed-pending", (asset, index, record) -> {
            view.removeAsset(asset);
            return true;
        }, (asset, index, record) -> false);

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, PositionSizer.fixed(), model);

        assertTrue(model.getPendingOrder(result.record("ALPHA")).isEmpty());
        assertTrue(result.record("ALPHA").isClosed());
    }

    @Test
    public void removedAssetDiscardsNextOpenExitBeyondLastProcessedBar() {
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("ALPHA", series("ALPHA", 10, 11, 12)));
        PortfolioStrategy strategy = new BasePortfolioStrategy("removed-next-exit",
                (asset, index, record) -> index == 0, (asset, index, record) -> {
                    view.removeAsset(asset);
                    return true;
                });

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, PositionSizer.fixed(),
                new TradeOnNextOpenModel());

        Position position = result.record("ALPHA").getPositions().getFirst();
        assertEquals(1, position.getEntry().getIndex());
        assertEquals(1, position.getExit().getIndex());
        assertEquals(0, result.openPositionCount());
    }

    @Test
    public void removedAssetDiscardsNextOpenEntryBeyondLastProcessedBar() {
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("ALPHA", series("ALPHA", 10, 11, 12)));
        PortfolioStrategy strategy = new BasePortfolioStrategy("removed-next-entry", (asset, index, record) -> {
            view.removeAsset(asset);
            return true;
        }, (asset, index, record) -> false);

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, PositionSizer.fixed(),
                new TradeOnNextOpenModel());

        assertTrue(result.record("ALPHA").getPositions().isEmpty());
        assertEquals(0, result.openPositionCount());
        assertTrue(result.enter("ALPHA", 1, view.numFactory().one(), view.numFactory().one()));
        assertEquals(1, result.openPositionCount());
    }

    @Test
    public void emptyRunReturnsImmediateRecordsForLaterAssets() {
        BarSeries alpha = new BaseBarSeriesBuilder().withName("ALPHA")
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("ALPHA", alpha));
        PortfolioStrategy strategy = new BasePortfolioStrategy("empty", (asset, index, record) -> false,
                (asset, index, record) -> false);

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, view.numFactory().one());
        alpha.addBar(series("ALPHA", 10).getBar(0));
        view.advance();

        assertTrue(result.enter("ALPHA", 0, view.numFactory().numOf(10), view.numFactory().one()));
        assertEquals(1, result.openPositionCount());
    }

    @Test
    public void skippedAndLazyRecordsAreImmediateAfterNonemptyRun() {
        BarSeries alpha = new BaseBarSeriesBuilder().withName("ALPHA")
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        LivePortfolioSeries view = new LivePortfolioSeries(Map.of("ALPHA", alpha, "BETA", series("BETA", 10)));
        PortfolioStrategy strategy = new BasePortfolioStrategy("skipped", (asset, index, record) -> {
            record.record("ALPHA");
            return false;
        }, (asset, index, record) -> false);

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, view.numFactory().one());
        assertTrue(result.enter("ALPHA", 0, view.numFactory().numOf(10), view.numFactory().one()));
        assertTrue(result.enter("LATER", 0, view.numFactory().numOf(10), view.numFactory().one()));
        assertEquals(2, result.openPositionCount());
    }

    @Test
    public void pendingAndPartiallyFilledEntriesReservePortfolioCapacity() {
        Map<String, BarSeries> members = new LinkedHashMap<>();
        members.put("ALPHA", liquidSeries("ALPHA", 0.5, 4));
        members.put("BETA", liquidSeries("BETA", 0.5, 4));
        PortfolioSeriesView view = PortfolioSeries.union(members);
        PortfolioStrategy strategy = new BasePortfolioStrategy("limit", new MaxOpenPositionsRule(1),
                (asset, index, record) -> false);
        NumFactory factory = view.numFactory();
        StopLimitExecutionModel model = new StopLimitExecutionModel(factory.zero(), factory.zero(), factory.one(), 3,
                TradeExecutionModel.PriceSource.CURRENT_CLOSE);

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, PositionSizer.fixed(), model);

        assertTrue(result.record("BETA").getPositions().isEmpty());
        assertEquals(2, result.record("ALPHA").getPositions().size());
        assertEquals(List.of(1, 2),
                result.record("ALPHA")
                        .getPositions()
                        .stream()
                        .map(position -> position.getEntry().getIndex())
                        .toList());
    }

    @Test
    public void expiredUnfilledEntryReleasesCapacityForAnotherAsset() {
        PortfolioSeriesView view = PortfolioSeries
                .union(Map.of("ALPHA", liquidSeries("ALPHA", 0, 4), "BETA", liquidSeries("BETA", 1, 4)));
        PortfolioRule limit = new MaxOpenPositionsRule(1);
        PortfolioStrategy strategy = new BasePortfolioStrategy("expiry", (asset, index,
                record) -> (asset.equals("ALPHA") ? index == 0 : index >= 1) && limit.isSatisfied(asset, index, record),
                (asset, index, record) -> false);
        NumFactory factory = view.numFactory();
        StopLimitExecutionModel model = new StopLimitExecutionModel(factory.zero(), factory.zero(), factory.one(), 1,
                TradeExecutionModel.PriceSource.CURRENT_CLOSE);

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, PositionSizer.fixed(), model);

        assertTrue(result.record("ALPHA").getPositions().isEmpty());
        assertEquals(1, result.record("BETA").getPositions().size());
        assertEquals(2, result.record("BETA").getPositions().getFirst().getEntry().getIndex());
    }

    @Test
    public void nextOpenExitDoesNotReleaseCapacityBeforeItsAssetBar() {
        BarSeries alpha = series("ALPHA", 100, 100);
        BarSeries later = series("LATER", DoubleNumFactory.getInstance(), 5, "100", "100");
        alpha.addBar(later.getBar(0));
        alpha.addBar(later.getBar(1));
        BarSeries beta = series("BETA", 100, 100, 100, 100, 100, 100, 100);
        PortfolioSeriesView view = PortfolioSeries.union(Map.of("ALPHA", alpha, "BETA", beta));
        PortfolioRule limit = new MaxOpenPositionsRule(1);
        PortfolioStrategy strategy = new BasePortfolioStrategy("next-open",
                (asset, index, record) -> (asset.equals("ALPHA") ? index == 0 : index == 2 || index == 5)
                        && limit.isSatisfied(asset, index, record),
                (asset, index, record) -> asset.equals("ALPHA") && index == 1);

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, PositionSizer.fixed(),
                new TradeOnNextOpenModel());

        assertEquals(2, result.record("ALPHA").getPositions().getFirst().getExit().getIndex());
        assertEquals(6, result.record("BETA").getPositions().getFirst().getEntry().getIndex());
    }

    @Test
    public void terminalAssetClosesBeforeLaterAssetAdmission() {
        PortfolioSeriesView view = PortfolioSeries
                .union(Map.of("ALPHA", series("ALPHA", 100, 100), "BETA", series("BETA", 100, 100, 100, 100)));
        PortfolioRule limit = new MaxOpenPositionsRule(1);
        PortfolioStrategy strategy = new BasePortfolioStrategy("terminal", (asset, index,
                record) -> (asset.equals("ALPHA") ? index == 0 : index == 2) && limit.isSatisfied(asset, index, record),
                (asset, index, record) -> false);

        PortfolioTradingRecord result = new PortfolioSeriesManager(view).run(strategy, view.numFactory().one());

        assertEquals(1, result.record("ALPHA").getPositions().getFirst().getExit().getIndex());
        assertEquals(2, result.record("BETA").getPositions().getFirst().getEntry().getIndex());
    }

    private static BarSeries liquidSeries(String name, double volume, int bars) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).build();
        for (int i = 0; i < bars; i++) {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(START.plus(Duration.ofDays(i)))
                    .openPrice(100)
                    .highPrice(100)
                    .lowPrice(100)
                    .closePrice(100)
                    .volume(volume)
                    .add();
        }
        return series;
    }

    private static PortfolioSnapshot scaledRun(NumFactory numFactory, String price, String capital) {
        PortfolioSeries series = new PortfolioSeries(series("ALPHA", numFactory, 0, price, price),
                series("BETA", numFactory, 0, price, price));
        return new PortfolioSeriesManager(series, new LinearTransactionCostModel(0.01))
                .run(weights(0.6, 0.4), numFactory.numOf(capital), RebalancePolicy.atStart())
                .getFinalSnapshot();
    }

    private static PortfolioAllocation weights(double alpha, double beta) {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("ALPHA", alpha);
        weights.put("BETA", beta);
        return new PortfolioAllocation(weights);
    }

    private static PortfolioSeriesManager manager(double[] alphaCloses, double[] betaCloses) {
        return new PortfolioSeriesManager(
                new PortfolioSeries(series("ALPHA", alphaCloses), series("BETA", betaCloses)));
    }

    private static PortfolioSeriesManager manager(double[] alphaCloses, double[] betaCloses, CostModel costModel) {
        return new PortfolioSeriesManager(new PortfolioSeries(series("ALPHA", alphaCloses), series("BETA", betaCloses)),
                costModel);
    }
}
