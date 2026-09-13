/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ValueAtRiskCriterionTest {

    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");

    private BarSeries series;

    private NumFactory numFactory = DoubleNumFactory.getInstance();

    private AnalysisCriterion getCriterion() {
        return new ValueAtRiskCriterion(0.95);
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
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 104d, 90d, 100d, 95d, 105d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(90d / 104), varCriterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateOnlyWithLossPositions() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 95d, 100d, 80d, 85d, 70d).build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(0.8), varCriterion.calculate(series, tradingRecord));
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
        AnalysisCriterion varCriterion = new ValueAtRiskCriterion(0.95, ReturnRepresentation.DECIMAL);
        assertNumEquals(numFactory.zero(), varCriterion.calculate(series, new BaseTradingRecord()));

        AnalysisCriterion varCriterionMultiplicative = new ValueAtRiskCriterion(0.95,
                ReturnRepresentation.MULTIPLICATIVE);
        assertNumEquals(numFactory.one(), varCriterionMultiplicative.calculate(series, new BaseTradingRecord()));

        AnalysisCriterion varCriterionPercentage = new ValueAtRiskCriterion(0.95, ReturnRepresentation.PERCENTAGE);
        assertNumEquals(numFactory.zero(), varCriterionPercentage.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithBuyAndHold() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100d, 99d).build();
        Position position = new Position(Trade.buyAt(0, series), Trade.sellAt(1, series));
        AnalysisCriterion varCriterion = getCriterion();
        assertNumEquals(numFactory.numOf(0.99), varCriterion.calculate(series, position));

        AnalysisCriterion varCriterionDecimal = new ValueAtRiskCriterion(0.95, ReturnRepresentation.DECIMAL);
        assertNumEquals(numFactory.numOf(0.99 - 1), varCriterionDecimal.calculate(series, position));

        AnalysisCriterion varCriterionPercentage = new ValueAtRiskCriterion(0.95, ReturnRepresentation.PERCENTAGE);
        assertNumEquals(numFactory.numOf((0.99 - 1) * 100), varCriterionPercentage.calculate(series, position));
    }

    @Test
    public void calculateRateOfReturnRepresentation() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100d, 104d, 90d, 100d, 95d, 105d)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));

        AnalysisCriterion varCriterion = new ValueAtRiskCriterion(0.95, ReturnRepresentation.DECIMAL);
        assertNumEquals(numFactory.numOf((90d / 104) - 1), varCriterion.calculate(series, tradingRecord));

        AnalysisCriterion varCriterionMultiplicative = new ValueAtRiskCriterion(0.95,
                ReturnRepresentation.MULTIPLICATIVE);
        assertNumEquals(numFactory.numOf(90d / 104), varCriterionMultiplicative.calculate(series, tradingRecord));

        AnalysisCriterion varCriterionPercentage = new ValueAtRiskCriterion(0.95, ReturnRepresentation.PERCENTAGE);
        assertNumEquals(numFactory.numOf(((90d / 104) - 1) * 100),
                varCriterionPercentage.calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        AnalysisCriterion criterion = getCriterion();
        assertTrue(criterion.betterThan(numFactory.numOf(-0.1), numFactory.numOf(-0.2)));
        assertFalse(criterion.betterThan(numFactory.numOf(-0.1), numFactory.numOf(0.0)));
    }

    @Test
    public void retainedFuturesEquitySeedIsNotSampledAsATailReturn() {
        for (NumFactory factory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            FuturesContract contract = FuturesContract.builder()
                    .venue("CDE")
                    .symbol("BTC-PERP")
                    .productType(FuturesContract.ProductType.PERPETUAL)
                    .settlementType(FuturesContract.SettlementType.LINEAR)
                    .baseCurrency("BTC")
                    .quoteCurrency("USD")
                    .settlementCurrency("USD")
                    .contractSize(factory.numOf(0.01))
                    .build();
            BarSeries retained = seriesWithCloses(factory, 2, 100d, 100d, 100d, 100d);
            BaseTradingRecord record = BaseTradingRecord.builder()
                    .futuresContract(contract)
                    .initialCapital(factory.numOf(1_000))
                    .build();
            record.operate(TradeFill.builder()
                    .index(0)
                    .time(T0)
                    .price(factory.numOf(100))
                    .amount(factory.numOf(1_000))
                    .side(ExecutionSide.BUY)
                    .futuresContract(contract)
                    .fees(List.of())
                    .build());
            record.operate(TradeFill.builder()
                    .index(1)
                    .time(T0.plusSeconds(1))
                    .price(factory.numOf(50))
                    .amount(factory.numOf(1_000))
                    .side(ExecutionSide.SELL)
                    .futuresContract(contract)
                    .fees(List.of())
                    .build());

            // The 500 USD loss was realized before the retained head, so the head
            // reports the cumulative account equity instead of a period return.
            // Every retained bar is flat, so no tail loss may be reported.
            assertNumEquals(factory.one(), new ValueAtRiskCriterion(0.95).calculate(retained, record));
        }
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
}
