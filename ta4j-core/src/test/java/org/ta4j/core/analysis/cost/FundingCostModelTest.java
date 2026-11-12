/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ConcurrentBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CumulativePnL;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

class FundingCostModelTest {

    static Stream<NumFactory> factories() {
        return Stream.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void rejectsIncompatibleRateAndMarkNumericTypes(NumFactory factory) {
        NumFactory otherFactory = factory.produces(DoubleNumFactory.getInstance().one())
                ? DecimalNumFactory.getInstance()
                : DoubleNumFactory.getInstance();
        BarSeries rates = new MockBarSeriesBuilder().withNumFactory(factory).withData(100, 110).build();
        BarSeries marks = new MockBarSeriesBuilder().withNumFactory(otherFactory).withData(100, 110).build();
        assertThrows(IllegalArgumentException.class,
                () -> new FundingCostModel(new FixedNumIndicator(rates, 0, 0.001), new ClosePriceIndicator(marks)));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void chargesBothSidesAtSettlementMarksWithinTheHoldingWindow(NumFactory factory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(100, 110, 120, 130, 140).build();
        FundingCostModel model = new FundingCostModel(new FixedNumIndicator(series, 0, 0.001, 0, 0.002, 0));
        Position longPosition = position(factory, model, 0, 4, false);
        Position shortPosition = position(factory, model, 0, 4, true);
        assertNumEquals(0.74, model.calculate(longPosition));
        assertNumEquals(-0.74, model.calculate(shortPosition));
        assertNumEquals(0.22, model.calculate(longPosition, 2));
        assertNumEquals(0.74, model.calculate(longPosition, 99));
        assertNumEquals(0.52, model.calculate(position(factory, model, 1, 4, false)));
        assertNumEquals(0, model.calculate(longPosition, 0));
        assertNumEquals(0, model.calculate(factory.one(), factory.two()));
        Position open = new Position(Trade.buyAt(0, factory.numOf(100), factory.two()), new ZeroCostModel(), model);
        assertNumEquals(0.22, model.calculate(open, 2));
        assertThrows(IllegalArgumentException.class, () -> model.calculate(open));
        series.setMaximumBarCount(2);
        assertNumEquals(0.52, model.calculate(longPosition));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void cumulativeRangesMatchScalarCostsForLongShortAndOpenPositions(NumFactory factory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory)
                .withData(100, 110, 120, 130, 140, 150)
                .build();
        Indicator<Num> rates = new FixedNumIndicator(series, 0, 0.001, 0, 0.002, -0.001, 0.01);
        Indicator<Num> marks = new ClosePriceIndicator(series);
        FundingCostModel model = new FundingCostModel(rates, marks);
        double[] expected = { 0, 0, 0, 0.52, 0.24, 0.24, 0.24, 0.24 };
        for (boolean shortSide : new boolean[] { false, true }) {
            Position closed = position(factory, model, 1, 4, shortSide);
            Num[] costs = model.calculate(closed, 0, 7);
            assertEquals(expected.length, costs.length);
            for (int index = 0; index < costs.length; index++) {
                assertNumEquals(shortSide ? -expected[index] : expected[index], costs[index]);
            }
            Position open = new Position(closed.getEntry(), new ZeroCostModel(), model);
            Num[] openCosts = model.calculate(open, 3, 7);
            double[] openExpected = { 0.52, 0.24, 3.24, 3.24, 3.24 };
            for (int offset = 0; offset < openCosts.length; offset++) {
                assertNumEquals(shortSide ? -openExpected[offset] : openExpected[offset], openCosts[offset]);
            }
        }

        AtomicInteger rateReads = new AtomicInteger();
        AtomicInteger markReads = new AtomicInteger();
        FundingCostModel counted = new FundingCostModel(countedIndicator(rates, rateReads, 3),
                countedIndicator(marks, markReads, 2));
        Num[] later = counted.calculate(position(factory, counted, 1, 4, false), 4, 6);
        assertNumEquals(0.24, later[0]);
        assertNumEquals(0.24, later[1]);
        assertNumEquals(0.24, later[2]);
        assertEquals(3, rateReads.get());
        assertEquals(2, markReads.get());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void cumulativeRangesRespectBothIndicatorsRetainedBounds(NumFactory factory) {
        BarSeries rates = new MockBarSeriesBuilder().withNumFactory(factory)
                .withData(100, 110, 120, 130, 140, 150)
                .build();
        BarSeries marks = new MockBarSeriesBuilder().withNumFactory(factory)
                .withData(100, 110, 120, 130, 140, 150)
                .build();
        rates.setMaximumBarCount(4);
        marks.setMaximumBarCount(3);
        AtomicInteger rateReads = new AtomicInteger();
        AtomicInteger markReads = new AtomicInteger();
        FundingCostModel model = new FundingCostModel(
                countedIndicator(new FixedNumIndicator(rates, 0, 0.001, 0, 0.002, -0.001, 0.01), rateReads, 3),
                countedIndicator(new ClosePriceIndicator(marks), markReads, 3));
        Num[] costs = model.calculate(position(factory, model, 0, 5, false), 0, 7);
        double[] expected = { 0, 0, 0, 0.52, 0.24, 3.24, 3.24, 3.24 };
        for (int index = 0; index < costs.length; index++) {
            assertNumEquals(expected[index], costs[index]);
        }
        assertEquals(3, rateReads.get());
        assertEquals(3, markReads.get());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void cumulativeRangesSkipMissingAndZeroRatesButPropagateMissingMarks(NumFactory factory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory)
                .withData(100, 110, 120, 130, 140, 150)
                .build();
        Indicator<Num> rates = new FixedIndicator<>(series, factory.zero(), NaN.NaN, factory.zero(),
                factory.numOf(-0.001), factory.numOf(0.001), factory.zero());
        AtomicInteger markReads = new AtomicInteger();
        Indicator<Num> marks = countedIndicator(new FixedIndicator<>(series, factory.numOf(100), NaN.NaN, NaN.NaN,
                factory.numOf(130), NaN.NaN, factory.numOf(150)), markReads, 2);
        FundingCostModel model = new FundingCostModel(rates, marks);
        Num[] costs = model.calculate(position(factory, model, 0, 5, false), 0, 5);
        assertNumEquals(0, costs[0]);
        assertNumEquals(0, costs[1]);
        assertNumEquals(0, costs[2]);
        assertNumEquals(-0.26, costs[3]);
        assertTrue(costs[4].isNaN());
        assertTrue(costs[5].isNaN());
        assertEquals(2, markReads.get());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void validatesCumulativeRangesAndHandlesEmptyPositions(NumFactory factory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(100, 110).build();
        FundingCostModel model = new FundingCostModel(new FixedNumIndicator(series, 0, 0.001));
        Position empty = new Position(Trade.TradeType.BUY, new ZeroCostModel(), model);
        Num[] emptyCosts = model.calculate(empty, 0, 2);
        assertEquals(3, emptyCosts.length);
        for (Num cost : emptyCosts) {
            assertNumEquals(0, cost);
        }
        Position position = position(factory, model, 0, 1, false);
        assertThrows(NullPointerException.class, () -> model.calculate(null, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> model.calculate(position, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> model.calculate(position, 2, 1));
        assertThrows(IllegalArgumentException.class, () -> model.calculate(position, 0, Integer.MAX_VALUE));
        Position atMaximumIndex = new Position(Trade.buyAt(Integer.MAX_VALUE, factory.hundred(), factory.two()),
                new ZeroCostModel(), model);
        assertNumEquals(0, model.calculate(atMaximumIndex, Integer.MAX_VALUE, Integer.MAX_VALUE)[0]);
        NumFactory other = factory.produces(DoubleNumFactory.getInstance().one()) ? DecimalNumFactory.getInstance()
                : DoubleNumFactory.getInstance();
        Position laterEntry = new Position(Trade.buyAt(1, other.hundred(), other.two()), new ZeroCostModel(), model);
        assertNumEquals(other.zero(), model.calculate(laterEntry, 0, 0)[0]);
    }

    @ParameterizedTest
    @MethodSource("factories")
    void distinguishesMissingSettlementFromMissingMarkAndPreservesCredits(NumFactory factory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(100, 110, 120, 130).build();
        FundingCostModel missing = new FundingCostModel(
                new FixedIndicator<>(series, factory.zero(), NaN.NaN, factory.zero(), factory.numOf(-0.001)),
                new FixedIndicator<>(series, factory.numOf(100), NaN.NaN, NaN.NaN, factory.numOf(130)));
        assertNumEquals(-0.26, missing.calculate(position(factory, missing, 0, 3, false)));
        FundingCostModel invalid = new FundingCostModel(new FixedNumIndicator(series, 0, 0.001, 0, 0),
                new FixedIndicator<>(series, factory.numOf(100), NaN.NaN, factory.numOf(120), factory.numOf(130)));
        assertTrue(invalid.calculate(position(factory, invalid, 0, 3, false)).isNaN());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void fixedScheduleIsUtcAlignedAcrossHourlyDailyAndMissingBars(NumFactory factory) {
        BarSeries hourly = new BaseBarSeriesBuilder().withNumFactory(factory).build();
        Instant start = Instant.parse("2026-11-10T23:00:00Z");
        for (int hour = 0; hour < 25; hour++) {
            addBar(hourly, start.plus(Duration.ofHours(hour)), Duration.ofHours(1));
        }
        FundingCostModel model = FundingCostModel.fixedRate(hourly, 0.0001, Duration.ofHours(8));
        for (int index = 1; index < 25; index++) {
            double expected = index == 1 || index == 9 || index == 17 ? 0.02 : 0;
            assertNumEquals(expected, model.calculate(position(factory, model, index - 1, index, false)));
        }
        BarSeries daily = new BaseBarSeriesBuilder().withNumFactory(factory).build();
        addBar(daily, Instant.EPOCH, Duration.ofDays(1));
        FundingCostModel dailyModel = FundingCostModel.fixedRate(daily, 0.0001, Duration.ofHours(8));
        assertNumEquals(0.06, dailyModel.calculate(position(factory, dailyModel, -1, 0, false)));
        BarSeries gap = new BaseBarSeriesBuilder().withNumFactory(factory).build();
        addBar(gap, Instant.parse("2026-11-11T07:00:00Z"), Duration.ofHours(1));
        addBar(gap, Instant.parse("2026-11-11T09:00:00Z"), Duration.ofHours(1));
        FundingCostModel gapModel = FundingCostModel.fixedRate(gap, -0.0001, Duration.ofHours(8));
        assertNumEquals(-0.02, gapModel.calculate(position(factory, gapModel, 0, 1, false)));
    }

    @Test
    void subsecondBoundariesUseFloorBeforeTheEpoch() {
        NumFactory factory = DecimalNumFactory.getInstance();
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(factory).build();
        addBar(series, Instant.EPOCH.minusNanos(1), Duration.ofNanos(500_000_000));
        addBar(series, Instant.EPOCH, Duration.ofNanos(1));
        FundingCostModel model = FundingCostModel.fixedRate(series, 0.01, Duration.ofNanos(500_000_000));
        assertNumEquals(2, model.calculate(position(factory, model, -1, 0, false)));
        assertNumEquals(2, model.calculate(position(factory, model, 0, 1, false)));
    }

    @Test
    void validatesInputsAndComparesIndicatorIdentity() {
        BarSeries series = new MockBarSeriesBuilder().withData(100, 110).build();
        Indicator<Num> rates = new FixedNumIndicator(series, 0, 0.001);
        Indicator<Num> marks = new FixedNumIndicator(series, 100, 110);
        FundingCostModel model = new FundingCostModel(rates, marks);
        assertTrue(model.equals(new FundingCostModel(rates, marks)));
        assertFalse(model.equals(new FundingCostModel(new FixedNumIndicator(series, 0, 0.001), marks)));
        assertFalse(model.equals(new ZeroCostModel()));
        assertFalse(model.equals((CostModel) null));
        assertThrows(NullPointerException.class, () -> new FundingCostModel(null));
        assertThrows(NullPointerException.class, () -> new FundingCostModel(rates, null));
        assertThrows(NullPointerException.class, () -> model.calculate((Position) null));
        assertThrows(NullPointerException.class, () -> FundingCostModel.fixedRate(null, 0.01, Duration.ofHours(8)));
        assertThrows(NullPointerException.class, () -> FundingCostModel.fixedRate(series, null, Duration.ofHours(8)));
        assertThrows(NullPointerException.class, () -> FundingCostModel.fixedRate(series, 0.01, null));
        assertThrows(IllegalArgumentException.class, () -> FundingCostModel.fixedRate(series, 0.01, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> FundingCostModel.fixedRate(series, 0.01, Duration.ofSeconds(-1)));
        for (double invalid : new double[] { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY }) {
            assertThrows(IllegalArgumentException.class,
                    () -> FundingCostModel.fixedRate(series, invalid, Duration.ofHours(8)));
        }
    }

    @ParameterizedTest
    @MethodSource("factories")
    void fundingFlowsThroughManagerProfitAndCumulativePnl(NumFactory factory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(100, 110, 120, 130, 140).build();
        FundingCostModel funding = new FundingCostModel(new FixedNumIndicator(series, 0, 0.001, 0, 0.002, 0));
        BaseStrategy strategy = new BaseStrategy(new FixedRule(0), new FixedRule(4));
        TradingRecord funded = new BarSeriesManager(series, new ZeroCostModel(), funding,
                new TradeOnCurrentCloseModel()).run(strategy, Trade.TradeType.BUY, factory.two());
        TradingRecord free = new BarSeriesManager(series, new TradeOnCurrentCloseModel()).run(strategy,
                Trade.TradeType.BUY, factory.two());
        assertNumEquals(79.26, funded.getPositions().getFirst().getProfit());
        assertNumEquals(0.74,
                free.getPositions().getFirst().getProfit().minus(funded.getPositions().getFirst().getProfit()));
        assertNumEquals(0.74,
                new CumulativePnL(series, free).getValue(4).minus(new CumulativePnL(series, funded).getValue(4)));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void cumulativePnlReadsEachFundingBarOnceWhileProducingEachMark(NumFactory factory) {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory)
                .withData(100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100)
                .build();
        AtomicInteger rateReads = new AtomicInteger();
        AtomicInteger markReads = new AtomicInteger();
        Indicator<Num> rates = new FixedNumIndicator(series, 0, 0, 0.01, 0, 0.01, 0, 0.01, 0, 0.01, 0, 0.01, 0, 0.01, 0,
                0.01, 0);
        FundingCostModel funding = new FundingCostModel(countedIndicator(rates, rateReads, 13),
                countedIndicator(new ClosePriceIndicator(series), markReads, 7));
        BaseTradingRecord record = new BaseTradingRecord(Trade.TradeType.BUY, new ZeroCostModel(), funding);
        record.enter(1, factory.hundred(), factory.two());
        record.exit(14, factory.hundred(), factory.two());
        rateReads.set(0);
        markReads.set(0);

        CumulativePnL curve = new CumulativePnL(series, record, 15);
        for (int index = 0; index <= 15; index++) {
            int settlements = Math.min(index, 14) / 2;
            assertNumEquals(-2 * settlements, curve.getValue(index));
        }
        assertEquals(13, rateReads.get());
        assertEquals(7, markReads.get());
    }

    @ParameterizedTest
    @MethodSource("factories")
    void rejectsEvictionOfEitherInputWithoutLockingIndicatorEvaluation(NumFactory factory) {
        for (boolean separateMarks : new boolean[] { false, true }) {
            BarSeries rates = concurrentSeries(factory);
            BarSeries marks = separateMarks ? concurrentSeries(factory) : rates;
            marks.setMaximumBarCount(3);
            AtomicBoolean mutate = new AtomicBoolean(true);
            Indicator<Num> rate = mutatingRate(rates, mutate, () -> {
                addBar(marks, Instant.EPOCH.plusSeconds(4), Duration.ofSeconds(1));
                addBar(marks, Instant.EPOCH.plusSeconds(5), Duration.ofSeconds(1));
            });
            FundingCostModel model = new FundingCostModel(rate, new ClosePriceIndicator(marks));
            Position position = position(factory, model, 0, 2, false);

            assertThrows(ConcurrentModificationException.class, () -> position.getHoldingCost(2));
        }
    }

    @ParameterizedTest
    @MethodSource("factories")
    void rejectsPublishedCorrectionButAllowsAppendWithoutEviction(NumFactory factory) {
        BarSeries corrected = concurrentSeries(factory);
        FundingCostModel correction = new FundingCostModel(
                mutatingRate(corrected, new AtomicBoolean(true), () -> corrected.addPrice(200)));
        assertThrows(ConcurrentModificationException.class,
                () -> position(factory, correction, 0, 2, false).getHoldingCost(2));

        BarSeries appended = concurrentSeries(factory);
        FundingCostModel append = new FundingCostModel(mutatingRate(appended, new AtomicBoolean(true),
                () -> addBar(appended, Instant.EPOCH.plusSeconds(4), Duration.ofSeconds(1))));
        assertNumEquals(0.4, position(factory, append, 0, 2, false).getHoldingCost(2));
    }

    @ParameterizedTest
    @MethodSource("factories")
    void cumulativeRangesRejectEvictionAndCorrectionWithoutHoldingSeriesLocks(NumFactory factory) {
        for (boolean sharedSeries : new boolean[] { false, true }) {
            for (boolean mutateRates : new boolean[] { false, true }) {
                for (boolean eviction : new boolean[] { false, true }) {
                    BarSeries rates = concurrentSeries(factory);
                    BarSeries marks = sharedSeries ? rates : concurrentSeries(factory);
                    BarSeries changed = mutateRates ? rates : marks;
                    if (eviction) {
                        changed.setMaximumBarCount(3);
                    }
                    Indicator<Num> rate = mutatingRate(rates, new AtomicBoolean(true), () -> {
                        if (eviction) {
                            addBar(changed, Instant.EPOCH.plusSeconds(4), Duration.ofSeconds(1));
                        } else {
                            changed.addPrice(200);
                        }
                    });
                    FundingCostModel model = new FundingCostModel(rate, new ClosePriceIndicator(marks));
                    Position position = position(factory, model, 0, 2, false);
                    assertThrows(ConcurrentModificationException.class, () -> model.calculate(position, 0, 2));
                }
            }
        }
    }

    @ParameterizedTest
    @MethodSource("factories")
    void cumulativeRangesReflectLaterCorrectionsAndAllowAppendWithoutEviction(NumFactory factory) {
        BarSeries corrected = concurrentSeries(factory);
        FundingCostModel model = new FundingCostModel(new FixedNumIndicator(corrected, 0, 0.001, 0.001));
        Position position = position(factory, model, 0, 2, false);
        Num[] before = model.calculate(position, 1, 2);
        assertNumEquals(0.2, before[0]);
        assertNumEquals(0.4, before[1]);
        corrected.addPrice(200);
        Num[] after = model.calculate(position, 1, 2);
        assertNumEquals(0.2, after[0]);
        assertNumEquals(0.6, after[1]);

        BarSeries appended = concurrentSeries(factory);
        FundingCostModel append = new FundingCostModel(mutatingRate(appended, new AtomicBoolean(true),
                () -> addBar(appended, Instant.EPOCH.plusSeconds(4), Duration.ofSeconds(1))));
        Num[] appendCosts = append.calculate(position(factory, append, 0, 2, false), 1, 2);
        assertNumEquals(0.2, appendCosts[0]);
        assertNumEquals(0.4, appendCosts[1]);
    }

    private static Indicator<Num> countedIndicator(Indicator<Num> delegate, AtomicInteger reads, int budget) {
        return new AbstractIndicator<>(delegate.getBarSeries()) {
            @Override
            public Num getValue(int index) {
                assertTrue(reads.incrementAndGet() <= budget, "Indicator read budget exceeded");
                return delegate.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return delegate.getCountOfUnstableBars();
            }
        };
    }

    private static BarSeries concurrentSeries(NumFactory factory) {
        BarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(factory).build();
        for (int index = 1; index <= 3; index++) {
            addBar(series, Instant.EPOCH.plusSeconds(index), Duration.ofSeconds(1));
        }
        return series;
    }

    private static Indicator<Num> mutatingRate(BarSeries series, AtomicBoolean mutate, Runnable mutation) {
        return new AbstractIndicator<>(series) {
            @Override
            public Num getValue(int index) {
                if (mutate.getAndSet(false)) {
                    CompletableFuture.runAsync(mutation).orTimeout(5, TimeUnit.SECONDS).join();
                }
                return series.numFactory().numOf(0.001);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
    }

    private static Position position(NumFactory factory, CostModel model, int entry, int exit, boolean shortSide) {
        Trade entryTrade = shortSide ? Trade.sellAt(entry, factory.numOf(100), factory.two())
                : Trade.buyAt(entry, factory.numOf(100), factory.two());
        Trade exitTrade = shortSide ? Trade.buyAt(exit, factory.numOf(140), factory.two())
                : Trade.sellAt(exit, factory.numOf(140), factory.two());
        return new Position(entryTrade, exitTrade, new ZeroCostModel(), model);
    }

    private static void addBar(BarSeries series, Instant end, Duration period) {
        series.barBuilder()
                .timePeriod(period)
                .endTime(end)
                .openPrice(100)
                .highPrice(100)
                .lowPrice(100)
                .closePrice(100)
                .volume(0)
                .add();
    }
}
