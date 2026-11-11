/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.cost;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CumulativePnL;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
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
