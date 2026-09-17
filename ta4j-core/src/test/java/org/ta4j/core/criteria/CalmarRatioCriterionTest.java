/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.alwaysInvested;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.Test;
import java.util.List;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradeFee;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.TimeConstants;

public class CalmarRatioCriterionTest extends AbstractCriterionTest {

    public CalmarRatioCriterionTest(NumFactory numFactory) {
        super(params -> new CalmarRatioCriterion(), numFactory);
    }

    @Test
    public void calculatesExpectedValueForMixedReturnsTradingRecord() {
        double[] closes = new double[] { 100d, 80d, 120d };
        BarSeries series = buildYearlySeries("calmar_mixed", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void returnsPercentageRepresentation() {
        double[] closes = new double[] { 100d, 80d, 120d };
        BarSeries series = buildYearlySeries("calmar_percentage", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = new CalmarRatioCriterion(ReturnRepresentation.PERCENTAGE);
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected * 100d), actual, 1e-12);
    }

    @Test
    public void returnsMultiplicativeRepresentation() {
        double[] closes = new double[] { 100d, 80d, 120d };
        BarSeries series = buildYearlySeries("calmar_multiplicative", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = new CalmarRatioCriterion(ReturnRepresentation.MULTIPLICATIVE);
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(1d + expected), actual, 1e-12);
    }

    @Test
    public void calculatesExpectedValueForPositiveReturnsTradingRecordWithNoDrawdown() {
        double[] closes = new double[] { 100d, 110d, 121d };
        BarSeries series = buildYearlySeries("calmar_positive", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void calculatesExpectedValueForNegativeReturnsTradingRecord() {
        double[] closes = new double[] { 100d, 70d, 80d };
        BarSeries series = buildYearlySeries("calmar_negative", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void returnsZeroWhenThereAreNoReturnObservations() {
        BarSeries series = buildYearlySeries("calmar_one_bar", new double[] { 100d });
        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();

        Num actual = criterion.calculate(series, new BaseTradingRecord());

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void returnsZeroWhenTradingRecordIsNull() {
        BarSeries series = buildYearlySeries("calmar_null_record", new double[] { 100d, 120d });
        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();

        Num actual = criterion.calculate(series, (TradingRecord) null);

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void calculatesExpectedValueForClosedPosition() {
        double[] closes = new double[] { 100d, 80d, 120d };
        BarSeries series = buildYearlySeries("calmar_closed_position", closes);

        BaseTradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(),
                numFactory.one());
        tradingRecord.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), numFactory.one());
        Position position = tradingRecord.getPositions().getFirst();

        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();
        Num actual = criterion.calculate(series, position);
        double expected = referenceCalmar(series, closes);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void openPositionHandlingIgnoreReturnsZeroForOpenPosition() {
        BarSeries series = buildYearlySeries("calmar_open_position", new double[] { 100d, 120d, 80d });
        BaseTradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(),
                numFactory.one());

        CalmarRatioCriterion markToMarket = new CalmarRatioCriterion(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        CalmarRatioCriterion ignoreOpen = new CalmarRatioCriterion(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.IGNORE);
        CalmarRatioCriterion realized = new CalmarRatioCriterion(EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);

        Num markToMarketValue = markToMarket.calculate(series, tradingRecord);
        Num ignoreOpenValue = ignoreOpen.calculate(series, tradingRecord);
        Num realizedValue = realized.calculate(series, tradingRecord);

        assertTrue(markToMarketValue.isNegative());
        assertNumEquals(numFactory.zero(), ignoreOpenValue, 0d);
        assertNumEquals(numFactory.zero(), realizedValue, 0d);
    }

    @Test
    public void returnsNaNWhenBeginValueCannotBeUsed() {
        BarSeries series = buildYearlySeries("calmar_zero_begin", new double[] { 0d, 120d, 80d });
        CalmarRatioCriterion criterion = new CalmarRatioCriterion(ReturnRepresentation.DECIMAL);

        Num actual = criterion.calculate(series, alwaysInvested(series));

        assertTrue(actual.isNaN());
    }

    @Test
    public void exposesReturnRepresentation() {
        CalmarRatioCriterion criterion = new CalmarRatioCriterion(ReturnRepresentation.PERCENTAGE);

        assertEquals(Optional.of(ReturnRepresentation.PERCENTAGE), criterion.getReturnRepresentation());
    }

    @Test
    public void betterThanUsesHigherValuesAsBetter() {
        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();

        assertTrue(criterion.betterThan(numFactory.one(), numFactory.zero()));
        assertFalse(criterion.betterThan(numFactory.zero(), numFactory.one()));
    }

    private BarSeries buildYearlySeries(String name, double[] closes) {
        BarSeries series = getBarSeries(name);
        Instant start = Instant.parse("2020-01-01T00:00:00Z");

        for (int i = 0; i < closes.length; i++) {
            Instant endTime = start.plus(Duration.ofDays(365L * i));
            double close = closes[i];
            series.addBar(series.barBuilder()
                    .timePeriod(Duration.ofDays(365))
                    .endTime(endTime)
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .build());
        }
        return series;
    }

    private double referenceCalmar(BarSeries series, double[] closes) {
        double annualizedReturn = referenceAnnualizedReturn(series, closes);
        double maximumDrawdown = referenceMaximumDrawdown(closes);
        if (maximumDrawdown == 0d) {
            return annualizedReturn;
        }
        return annualizedReturn / maximumDrawdown;
    }

    @Test
    public void returnsFuturesEconomics_whenPositionIsEvaluatedDirectly() {
        BarSeries series = RatioCriterionTestSupport.buildDailySeries(getBarSeries("futures_calmar"),
                new double[] { 100d, 100.05d, 100.1d }, Instant.parse("2024-01-01T00:00:00Z"));
        Position position = futuresPosition(series, 100d, 100.1d);
        Num actual = ((CalmarRatioCriterion) getCriterion()).calculate(series, position);
        double expected = Math.pow(1.001d, TimeConstants.SECONDS_PER_YEAR / (2d * 86_400d)) - 1d;
        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void futuresCalmarSeedsNormalizedInitialCapital() {
        BarSeries series = buildYearlySeries("calmar_futures_initial_fee", new double[] { 100d, 100d, 100d });
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
        TradeFill entryFill = fill(contract, series.getBeginIndex(), ExecutionSide.BUY, 100d).toBuilder()
                .fees(List.of(TradeFee.builder()
                        .type(TradeFee.Type.COMMISSION)
                        .amount(numFactory.numOf(0.1))
                        .currency("USD")
                        .build()))
                .build();
        Trade entry = Trade.fromFill(entryFill, RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFill(fill(contract, series.getEndIndex(), ExecutionSide.SELL, 100d),
                RecordedTradeCostModel.INSTANCE);
        Position position = new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());

        Num actual = ((CalmarRatioCriterion) getCriterion()).calculate(series, position);
        double years = Duration
                .between(series.getBar(series.getBeginIndex()).getEndTime(),
                        series.getBar(series.getEndIndex()).getEndTime())
                .getSeconds() / (double) TimeConstants.SECONDS_PER_YEAR;
        double expected = (Math.pow(0.8d, 1d / years) - 1d) / 0.2d;

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void explicitMarkPriceValuesFuturesForPositionAndRecord() {
        BarSeries series = buildYearlySeries("calmar_explicit_mark", new double[] { 100d, 100d });
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
        TradeFill positionEntryFill = fill(contract, series.getBeginIndex(), ExecutionSide.BUY, 100d);
        Position position = new Position(Trade.fromFill(positionEntryFill, RecordedTradeCostModel.INSTANCE),
                RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        BaseTradingRecord tradingRecord = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.one())
                .build();
        tradingRecord.operate(fill(contract, series.getBeginIndex(), ExecutionSide.BUY, 100d));
        ConstantIndicator<Num> markPrice = new ConstantIndicator<>(series, numFactory.numOf(90d));
        CalmarRatioCriterion criterion = (CalmarRatioCriterion) getCriterion();
        double years = Duration
                .between(series.getBar(series.getBeginIndex()).getEndTime(),
                        series.getBar(series.getEndIndex()).getEndTime())
                .getSeconds() / (double) TimeConstants.SECONDS_PER_YEAR;
        Num expectedMarked = numFactory.numOf((Math.pow(0.9d, 1d / years) - 1d) / 0.1d);

        assertNumEquals(numFactory.zero(), criterion.calculate(series, position), 0d);
        assertNumEquals(expectedMarked, criterion.calculate(series, position, markPrice), 1e-12);
        assertNumEquals(numFactory.zero(), criterion.calculate(series, tradingRecord), 0d);
        assertNumEquals(expectedMarked, criterion.calculate(series, tradingRecord, markPrice), 1e-12);
    }

    private Position futuresPosition(BarSeries series, double entryPrice, double exitPrice) {
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
        Trade entry = Trade.fromFill(fill(contract, series.getBeginIndex(), ExecutionSide.BUY, entryPrice),
                RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFill(fill(contract, series.getEndIndex(), ExecutionSide.SELL, exitPrice),
                RecordedTradeCostModel.INSTANCE);
        return new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
    }

    private TradeFill fill(FuturesContract contract, int index, ExecutionSide side, double price) {
        return TradeFill.builder()
                .index(index)
                .time(Instant.parse("2024-01-01T00:00:00Z").plusSeconds(index * 86_400L))
                .price(numFactory.numOf(price))
                .amount(numFactory.one())
                .side(side)
                .futuresContract(contract)
                .fees(List.of())
                .build();
    }

    private double referenceAnnualizedReturn(BarSeries series, double[] closes) {
        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();
        double elapsedSeconds = Duration
                .between(series.getBar(beginIndex).getEndTime(), series.getBar(endIndex).getEndTime())
                .getSeconds();
        if (elapsedSeconds <= 0d) {
            return 0d;
        }
        double years = elapsedSeconds / TimeConstants.SECONDS_PER_YEAR;
        double totalReturn = closes[closes.length - 1] / closes[0];
        return Math.pow(totalReturn, 1d / years) - 1d;
    }

    private double referenceMaximumDrawdown(double[] closes) {
        double peak = closes[0];
        double maximumDrawdown = 0d;
        for (double close : closes) {
            if (close > peak) {
                peak = close;
            }
            double drawdown = (peak - close) / peak;
            maximumDrawdown = Math.max(maximumDrawdown, drawdown);
        }
        return maximumDrawdown;
    }
}
