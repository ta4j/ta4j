/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.TradeFill;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.num.Num;
import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

public class ExpectedShortfallCriterionTest {
    private BarSeries series;

    private NumFactory numFactory = DoubleNumFactory.getInstance();

    private ExpectedShortfallCriterion getCriterion() {
        return new ExpectedShortfallCriterion(0.95);
    }

    @Test
    public void calculateOnlyWithGainPositions() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 105d, 106d, 107d, 108d, 115d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series),
                Trade.buyAt(3, series), Trade.sellAt(5, series));
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numFactory.one(), varCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithASimplePosition() {
        // if only one position in tail, VaR = ES
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 104d, 90d, 100d, 95d, 105d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        AnalysisCriterion esCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(90d / 104), esCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossPosition() {
        // regularly decreasing prices
        List<Double> prices = IntStream.rangeClosed(1, 100)
                .asDoubleStream()
                .boxed()
                .sorted(Collections.reverseOrder())
                .collect(Collectors.toList());
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(prices).build();
        Position position = new Position(Trade.buyAt(series.getBeginIndex(), series),
                Trade.sellAt(series.getEndIndex(), series));
        AnalysisCriterion esCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(0.6988271187715792), esCriterion.calculate(series, position));
    }

    @Test
    public void calculateWithNoBarsShouldReturn0() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 95d, 100d, 80d, 85d, 70d).build();
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(1), varCriterion.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithNoBarsShouldReturnZeroRateOfReturn() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 95d, 100d, 80d, 85d, 70d).build();
        AnalysisCriterion varCriterion = new ExpectedShortfallCriterion(0.95, ReturnRepresentation.DECIMAL);
        assertNumEquals(numFactory.zero(), varCriterion.calculate(series, new BaseTradingRecord()));

        AnalysisCriterion esCriterionMultiplicative = new ExpectedShortfallCriterion(0.95,
                ReturnRepresentation.MULTIPLICATIVE);
        assertNumEquals(numFactory.one(), esCriterionMultiplicative.calculate(series, new BaseTradingRecord()));

        AnalysisCriterion esCriterionPercentage = new ExpectedShortfallCriterion(0.95, ReturnRepresentation.PERCENTAGE);
        assertNumEquals(numFactory.zero(), esCriterionPercentage.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithBuyAndHold() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 99d).build();
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(0.99), varCriterion.calculate(series, position));

        AnalysisCriterion esCriterionDecimal = new ExpectedShortfallCriterion(0.95, ReturnRepresentation.DECIMAL);
        assertNumEquals(numFactory.numOf(0.99 - 1), esCriterionDecimal.calculate(series, position));

        AnalysisCriterion esCriterionPercentage = new ExpectedShortfallCriterion(0.95, ReturnRepresentation.PERCENTAGE);
        assertNumEquals(numFactory.numOf((0.99 - 1) * 100), esCriterionPercentage.calculate(series, position));
    }

    @Test
    public void calculateRateOfReturnRepresentation() {
        // if only one position in tail, VaR = ES
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 104d, 90d, 100d, 95d, 105d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        AnalysisCriterion esCriterion = new ExpectedShortfallCriterion(0.95, ReturnRepresentation.DECIMAL);
        assertNumEquals(numFactory.numOf((90d / 104) - 1), esCriterion.calculate(series, tradingRecord));

        AnalysisCriterion esCriterionMultiplicative = new ExpectedShortfallCriterion(0.95,
                ReturnRepresentation.MULTIPLICATIVE);
        assertNumEquals(numFactory.numOf(90d / 104), esCriterionMultiplicative.calculate(series, tradingRecord));

        AnalysisCriterion esCriterionPercentage = new ExpectedShortfallCriterion(0.95, ReturnRepresentation.PERCENTAGE);
        assertNumEquals(numFactory.numOf(((90d / 104) - 1) * 100),
                esCriterionPercentage.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithUndefinedFirstRetainedReturnDoesNotSlicePastRawValues() {
        // The pre-window entry is valued at the first retained close, 0, and
        // exits there at 0: the undefined 0/0 return occupies the first raw
        // slot and must not shift the slice past the raw values.
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.setMaximumBarCount(2);
        series.barBuilder().closePrice(10d).add();
        Trade entry = Trade.buyAt(0, series);
        series.barBuilder().closePrice(0d).add();
        Trade exit = Trade.sellAt(1, series);
        series.barBuilder().closePrice(30d).add();
        TradingRecord tradingRecord = new BaseTradingRecord(entry, exit);

        Num result = getCriterion().calculate(series, tradingRecord);

        assertTrue(result.isNaN());
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numFactory.numOf(-0.1), numFactory.numOf(-0.2)));
        assertFalse(criterion.betterThan(numFactory.numOf(-0.1), numFactory.numOf(0.0)));
    }

    @Test
    public void shortfallIgnoresAnExitAfterTheWindow() {
        // The exit lands on a bar after the window; its -50% return must not
        // join the tail distribution.
        BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("tail", numFactory, 1, 100d, 110d, 55d);
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        BarSeries truncated = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 110d).build();
        TradingRecord openAtWindowEnd = new BaseTradingRecord(Trade.buyAt(0, truncated));

        Num es = getCriterion().calculate(series, tradingRecord);

        assertNumEquals(getCriterion().calculate(truncated, openAtWindowEnd), es);
        assertNotEquals(numFactory.numOf(55d / 110d), es);
    }

    @Test
    public void shortfallIncludesFirstRetainedExitLoss() {
        // The entry predates the retained window and exits on its first bar at
        // 25, half the 50 close it is valued at: that -50% loss is a real
        // observation that must join the tail distribution instead of being
        // dropped by the unconditional placeholder trim. A re-entry at 50 then
        // earns +140%.
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        rolling.setMaximumBarCount(2);
        rolling.barBuilder().closePrice(100d).add();
        Trade entry = Trade.buyAt(0, rolling);
        rolling.barBuilder().closePrice(50d).add();
        rolling.barBuilder().closePrice(120d).add();
        TradingRecord tradingRecord = new BaseTradingRecord(entry,
                Trade.sellAt(1, numFactory.numOf(25d), numFactory.one()), Trade.buyAt(1, rolling),
                Trade.sellAt(2, rolling));

        Num es = getCriterion().calculate(rolling, tradingRecord);

        // Rates: -50% on the first retained bar and +140%; the .95 tail keeps
        // the single worst rate, whose log mean converts back to 25/50.
        assertNumEquals(numFactory.numOf(0.5d), es);
    }

    @Test
    public void matchesFreshSeriesAcrossWindowShapesAndPositionBoundaries() {
        for (ConstrainedSeriesSupport.CriterionWindowFixture fixture : ConstrainedSeriesSupport
                .criterionWindowFixtures(numFactory)) {
            for (ReturnRepresentation representation : ReturnRepresentation.values()) {
                ExpectedShortfallCriterion criterion = new ExpectedShortfallCriterion(0.95, representation);
                Num actual = criterion.calculate(fixture.series(), fixture.tradingRecord());
                Num expected = criterion.calculate(fixture.equivalentSeries(), fixture.markedEquivalentRecord());
                assertEquals(fixture.name() + ": trading-record return window", expected.doubleValue(),
                        actual.doubleValue(), 1e-10);
                if (fixture.position() != null && fixture.markedEquivalentPosition() != null) {
                    Num actualPosition = criterion.calculate(fixture.series(), fixture.position());
                    Num expectedPosition = criterion.calculate(fixture.equivalentSeries(),
                            fixture.markedEquivalentPosition());
                    assertEquals(fixture.name() + ": position return window", expectedPosition.doubleValue(),
                            actualPosition.doubleValue(), 1e-10);
                }
            }
        }
    }

    @Test
    public void windowedSpotReturnsAreSampledWithoutThePlaceholder() {
        double[] closes = { 100d, 97d, 98.01d, 99.99d, 104d };
        BarSeries full = seriesWithCloses(numFactory, 0, closes);
        BarSeries windowed = seriesWithCloses(numFactory, 2, closes);
        Position fullPosition = new Position(Trade.buyAt(0, full), Trade.sellAt(4, full));
        Position windowedPosition = new Position(Trade.buyAt(2, windowed), Trade.sellAt(6, windowed));

        ExpectedShortfallCriterion criterion = new ExpectedShortfallCriterion(0.5);
        assertNumEquals(criterion.calculate(full, fullPosition), criterion.calculate(windowed, windowedPosition));
    }

    private static BarSeries seriesWithCloses(NumFactory numFactory, int beginIndex, double... closes) {
        List<Bar> bars = new ArrayList<>();
        Instant endTime = Instant.parse("2025-01-01T00:00:00Z");
        for (double close : closes) {
            Num price = numFactory.numOf(close);
            bars.add(new BaseBar(Duration.ofMinutes(1), endTime.minus(Duration.ofMinutes(1)), endTime, price, price,
                    price, price, numFactory.zero(), numFactory.zero(), 0));
            endTime = endTime.plus(Duration.ofMinutes(1));
        }
        return new BaseBarSeriesBuilder().withNumFactory(numFactory).withBeginIndex(beginIndex).withBars(bars).build();
    }

    @Test
    public void unavailableFuturesMarkPropagatesThroughExpectedShortfall() {
        FuturesContract contract = FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.numOf(0.01))
                .build();
        BarSeries bars = seriesWithCloses(numFactory, 0, 100, 100, Double.NaN, 100);
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(1_000))
                .build();
        record.operate(TradeFill.builder()
                .index(1)
                .time(Instant.parse("2025-01-01T00:00:01Z"))
                .price(numFactory.numOf(100))
                .amount(numFactory.numOf(1_000))
                .side(org.ta4j.core.ExecutionSide.BUY)
                .futuresContract(contract)
                .fees(List.of())
                .build());
        record.operate(TradeFill.builder()
                .index(3)
                .time(Instant.parse("2025-01-01T00:00:03Z"))
                .price(numFactory.numOf(100))
                .amount(numFactory.numOf(1_000))
                .side(org.ta4j.core.ExecutionSide.SELL)
                .futuresContract(contract)
                .fees(List.of())
                .build());

        assertTrue(getCriterion().calculate(bars, record).isNaN());
    }
}
