/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradeFee;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.ExcessReturns;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.cost.LinearBorrowingCostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.analysis.frequency.Sample;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.BarSeriesUtils;

@RunWith(Parameterized.class)
public class RatioSampleSupportTest {

    private final NumFactory numFactory;

    public RatioSampleSupportTest(NumFactory numFactory) {
        this.numFactory = numFactory;
    }

    @Parameterized.Parameters(name = "NumFactory: {index} (0=DoubleNum, 1=DecimalNum)")
    public static List<NumFactory> numFactories() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    @Test
    public void barSamplingReturnsExpectedValuesAndDeltaYears() {
        BarSeries series = buildDailySeries("bar_sampling_series", new double[] { 100d, 110d, 99d, 108.9d });
        TradingRecord tradingRecord = RatioCriterionTestSupport.alwaysInvested(series);
        ExcessReturns excessReturns = new ExcessReturns(series, numFactory.zero(),
                CashReturnPolicy.CASH_EARNS_RISK_FREE, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);

        List<Sample> samples = RatioSampleSupport
                .samples(series, tradingRecord, SamplingFrequency.BAR, ZoneOffset.UTC, excessReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();

        assertEquals(3, samples.size());
        assertNumEquals(0.1d, samples.get(0).value());
        assertNumEquals(-0.1d, samples.get(1).value());
        assertNumEquals(0.1d, samples.get(2).value());

        assertNumEquals(BarSeriesUtils.deltaYears(series, 0, 1), samples.get(0).deltaYears(), 1e-12);
        assertNumEquals(BarSeriesUtils.deltaYears(series, 1, 2), samples.get(1).deltaYears(), 1e-12);
        assertNumEquals(BarSeriesUtils.deltaYears(series, 2, 3), samples.get(2).deltaYears(), 1e-12);
    }

    @Test
    public void tradeSamplingIncludesOpenPositionForMarkToMarketAndExcludesForIgnore() {
        BarSeries series = buildDailySeries("trade_sampling_series", new double[] { 100d, 110d, 99d, 120d });
        TradingRecord tradingRecord = buildRecordWithOneOpenPosition(series);
        ExcessReturns markToMarketReturns = new ExcessReturns(series, numFactory.zero(),
                CashReturnPolicy.CASH_EARNS_RISK_FREE, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);
        ExcessReturns ignoreReturns = new ExcessReturns(series, numFactory.zero(),
                CashReturnPolicy.CASH_EARNS_RISK_FREE, tradingRecord, OpenPositionHandling.IGNORE);

        List<Sample> markToMarketSamples = RatioSampleSupport
                .samples(series, tradingRecord, SamplingFrequency.TRADE, ZoneOffset.UTC, markToMarketReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();
        List<Sample> ignoreSamples = RatioSampleSupport
                .samples(series, tradingRecord, SamplingFrequency.TRADE, ZoneOffset.UTC, ignoreReturns,
                        OpenPositionHandling.IGNORE)
                .toList();

        assertEquals(3, markToMarketSamples.size());
        assertEquals(2, ignoreSamples.size());

        assertNumEquals(0.1d, markToMarketSamples.get(0).value());
        assertNumEquals(-0.1d, markToMarketSamples.get(1).value());
        assertNumEquals((120d / 99d) - 1d, markToMarketSamples.get(2).value());
        assertNumEquals(0.1d, ignoreSamples.get(0).value());
        assertNumEquals(-0.1d, ignoreSamples.get(1).value());
    }

    @Test
    public void tradeSamplingSupportsShortEntries() {
        BarSeries series = buildDailySeries("short_trade_sampling_series", new double[] { 100d, 90d, 99d });
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.SELL);
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(1, series.getBar(1).getClosePrice(), numFactory.one());
        tradingRecord.enter(1, series.getBar(1).getClosePrice(), numFactory.one());
        tradingRecord.exit(2, series.getBar(2).getClosePrice(), numFactory.one());
        ExcessReturns excessReturns = new ExcessReturns(series, numFactory.zero(),
                CashReturnPolicy.CASH_EARNS_RISK_FREE, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);

        List<Sample> samples = RatioSampleSupport
                .samples(series, tradingRecord, SamplingFrequency.TRADE, ZoneOffset.UTC, excessReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();

        assertEquals(2, samples.size());
        assertNumEquals(numFactory.numOf(0.1d), samples.get(0).value(), 1e-12);
        assertNumEquals(numFactory.numOf(-0.1d), samples.get(1).value(), 1e-12);
    }

    @Test
    public void tradeSamplingKeepsSameBarEntryExitAsZeroLengthSample() {
        BarSeries series = buildDailySeries("same_bar_sampling_series", new double[] { 100d, 110d });
        BaseTradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(1, series.getBar(1).getClosePrice(), numFactory.one());
        ExcessReturns excessReturns = new ExcessReturns(series, numFactory.zero(),
                CashReturnPolicy.CASH_EARNS_RISK_FREE, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);

        List<Sample> samples = RatioSampleSupport
                .samples(series, tradingRecord, SamplingFrequency.TRADE, ZoneOffset.UTC, excessReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();

        assertEquals(2, samples.size());
        assertNumEquals(numFactory.zero(), samples.get(0).value(), 0d);
        assertNumEquals(numFactory.zero(), samples.get(0).deltaYears(), 0d);
        assertNumEquals(numFactory.numOf(0.1d), samples.get(1).value(), 1e-12);
    }

    private TradingRecord buildRecordWithOneOpenPosition(BarSeries series) {
        BaseTradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        tradingRecord.exit(1, series.getBar(1).getClosePrice(), series.numFactory().one());
        tradingRecord.enter(1, series.getBar(1).getClosePrice(), series.numFactory().one());
        tradingRecord.exit(2, series.getBar(2).getClosePrice(), series.numFactory().one());
        tradingRecord.enter(2, series.getBar(2).getClosePrice(), series.numFactory().one());
        return tradingRecord;
    }

    private BarSeries buildDailySeries(String name, double[] closes) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).withNumFactory(numFactory).build();
        return RatioCriterionTestSupport.buildDailySeries(series, closes, Instant.parse("2024-01-01T00:00:00Z"));
    }

    @Test
    public void tradeSamplingExtendsAggregateFuturesResidualThroughFinalBar() {
        BarSeries series = buildDailySeries("aggregate_futures_trade_sampling_series",
                new double[] { 100d, 110d, 99d, 120d });
        FuturesContract contract = FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.numOf(0.01d))
                .build();
        Trade entry = Trade.fromFills(TradeType.BUY, List.of(fill(contract, 0, ExecutionSide.BUY, 2d, 100d),
                fill(contract, -1, ExecutionSide.BUY, 1d, 100d)), RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFills(TradeType.SELL, List.of(fill(contract, 1, ExecutionSide.SELL, 1d, 110d),
                fill(contract, -1, ExecutionSide.SELL, 1d, 110d)), RecordedTradeCostModel.INSTANCE);
        Position aggregatePosition = new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        TradingRecord aggregateRecord = new AggregatePositionTradingRecord(aggregatePosition);
        TradingRecord spotRecord = RatioCriterionTestSupport.alwaysInvested(series);
        ExcessReturns excessReturns = new ExcessReturns(series, numFactory.zero(),
                CashReturnPolicy.CASH_EARNS_RISK_FREE, spotRecord, OpenPositionHandling.MARK_TO_MARKET);

        List<Sample> samples = RatioSampleSupport
                .samples(series, aggregateRecord, SamplingFrequency.TRADE, ZoneOffset.UTC, excessReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();

        assertEquals(1, samples.size());
        assertNumEquals(BarSeriesUtils.deltaYears(series, 0, 3), samples.get(0).deltaYears(), 1e-12);
    }

    @Test
    public void tradeSamplingExtendsAggregateSpotResidualThroughFinalBar() {
        BarSeries series = buildDailySeries("aggregate_spot_trade_sampling_series",
                new double[] { 100d, 110d, 99d, 120d });
        Instant fillTime = Instant.parse("2024-01-01T00:00:00Z");
        Trade entry = Trade.fromFills(TradeType.BUY, List.of(new TradeFill(0, fillTime, numFactory.numOf(100),
                numFactory.numOf(2), numFactory.zero(), ExecutionSide.BUY, null, null)),
                RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFills(TradeType.SELL,
                List.of(new TradeFill(1, fillTime.plusSeconds(1), numFactory.numOf(110), numFactory.one(),
                        numFactory.zero(), ExecutionSide.SELL, null, null),
                        new TradeFill(-1, fillTime.plusSeconds(2), numFactory.numOf(120), numFactory.one(),
                                numFactory.zero(), ExecutionSide.SELL, null, null)),
                RecordedTradeCostModel.INSTANCE);
        Position aggregatePosition = new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        TradingRecord aggregateRecord = new AggregatePositionTradingRecord(aggregatePosition);
        TradingRecord spotRecord = RatioCriterionTestSupport.alwaysInvested(series);
        ExcessReturns excessReturns = new ExcessReturns(series, numFactory.zero(),
                CashReturnPolicy.CASH_EARNS_RISK_FREE, spotRecord, OpenPositionHandling.MARK_TO_MARKET);

        List<Sample> samples = RatioSampleSupport
                .samples(series, aggregateRecord, SamplingFrequency.TRADE, ZoneOffset.UTC, excessReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();

        assertEquals(1, samples.size());
        assertNumEquals(BarSeriesUtils.deltaYears(series, 0, 3), samples.get(0).deltaYears(), 1e-12);
    }

    @Test
    public void tradeSamplingUsesLastExecutedExitFillForFullyExitedAggregatePosition() {
        BarSeries series = buildDailySeries("aggregate_futures_exit_sampling_series",
                new double[] { 100d, 110d, 120d, 130d, 140d, 150d, 160d });
        FuturesContract contract = FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.numOf(0.01d))
                .build();
        Trade entry = Trade.fromFills(TradeType.BUY,
                List.of(fill(contract, 0, ExecutionSide.BUY, 2d, 100d), fill(contract, 2, ExecutionSide.BUY, 1d, 100d)),
                RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFills(TradeType.SELL, List.of(fill(contract, 3, ExecutionSide.SELL, 1d, 130d),
                fill(contract, 5, ExecutionSide.SELL, 1d, 150d), fill(contract, 7, ExecutionSide.SELL, 1d, 170d)),
                RecordedTradeCostModel.INSTANCE);
        Position aggregatePosition = new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        TradingRecord aggregateRecord = new AggregatePositionTradingRecord(aggregatePosition);
        TradingRecord spotRecord = RatioCriterionTestSupport.alwaysInvested(series);
        ExcessReturns excessReturns = new ExcessReturns(series, numFactory.zero(),
                CashReturnPolicy.CASH_EARNS_RISK_FREE, spotRecord, OpenPositionHandling.IGNORE);

        List<Sample> samples = RatioSampleSupport
                .samples(series, aggregateRecord, SamplingFrequency.TRADE, ZoneOffset.UTC, excessReturns,
                        OpenPositionHandling.IGNORE)
                .toList();

        assertEquals(1, samples.size());
        assertNumEquals(BarSeriesUtils.deltaYears(series, 0, 5), samples.get(0).deltaYears(), 1e-12);
    }

    private TradeFill fill(FuturesContract contract, int index, ExecutionSide side, double amount, double price) {
        return TradeFill.builder()
                .index(index)
                .time(Instant.parse("2024-01-01T00:00:00Z").plusSeconds(index))
                .price(numFactory.numOf(price))
                .amount(numFactory.numOf(amount))
                .side(side)
                .futuresContract(contract)
                .fees(List.of())
                .build();
    }

    private static final class AggregatePositionTradingRecord extends BaseTradingRecord {
        private final List<Position> positions;
        private final FuturesContract futuresContract;
        private final Num initialCapital;

        private AggregatePositionTradingRecord(Position position) {
            this(List.of(position), null, null);
        }

        private AggregatePositionTradingRecord(List<Position> positions, FuturesContract futuresContract,
                Num initialCapital) {
            this.positions = positions;
            this.futuresContract = futuresContract;
            this.initialCapital = initialCapital;
        }

        @Override
        public List<Position> getPositions() {
            return positions;
        }

        @Override
        public Position getCurrentPosition() {
            return positions.getFirst();
        }

        @Override
        public List<Position> getOpenPositions() {
            return List.of();
        }

        @Override
        public FuturesContract getFuturesContract() {
            return futuresContract;
        }

        @Override
        public Num getInitialCapital() {
            return initialCapital;
        }
    }

    @Test
    public void timeSamplingIncludesInitialFuturesFeeButNotPreWindowSeed() {
        BarSeries series = buildDailySeries("initial_futures_fee", new double[] { 100d, 100d, 100d, 100d });
        FuturesContract contract = FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.one())
                .build();
        Trade entry = Trade.fromFill(fill(contract, 0, ExecutionSide.BUY, 1d, 100d).toBuilder()
                .fees(List.of(TradeFee.builder()
                        .type(TradeFee.Type.COMMISSION)
                        .amount(numFactory.one())
                        .currency("USD")
                        .build()))
                .build(), RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFill(fill(contract, 0, ExecutionSide.SELL, 1d, 100d), RecordedTradeCostModel.INSTANCE);
        Position position = new Position(entry, exit, RecordedTradeCostModel.INSTANCE,
                new LinearBorrowingCostModel(0d));
        BaseTradingRecord record = new BaseTradingRecord(position);
        ExcessReturns returns = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO, position,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        List<Sample> samples = RatioSampleSupport
                .samples(series, record, SamplingFrequency.BAR, ZoneOffset.UTC, returns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();
        assertEquals(4, samples.size());
        assertNumEquals(-0.01d, samples.getFirst().value());
        assertNumEquals(BarSeriesUtils.deltaYears(series, 0, 1), samples.getFirst().deltaYears(), 1e-12);
        assertNumEquals(0d, samples.get(1).value());
        List<Sample> monthly = RatioSampleSupport
                .samples(series, record, SamplingFrequency.MONTH, ZoneOffset.UTC, returns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();
        assertEquals(1, monthly.size());
        assertNumEquals(-0.01d, monthly.getFirst().value());
        series.setMaximumBarCount(3);
        ExcessReturns retained = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO,
                position, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        List<Sample> retainedSamples = RatioSampleSupport
                .samples(series, record, SamplingFrequency.BAR, ZoneOffset.UTC, retained,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();
        assertEquals(2, retainedSamples.size());
        for (Sample sample : retainedSamples) {
            assertNumEquals(0d, sample.value());
        }
    }

    @Test
    public void singleRetainedBarSamplesFreshMarkedEquityWithRiskFreeDuration() {
        BarSeries source = buildDailySeries("single_futures_mark", new double[] { 110d });
        BarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(7)
                .build();
        FuturesContract contract = FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.one())
                .build();
        Position position = new Position(
                Trade.fromFill(fill(contract, 7, ExecutionSide.BUY, 1d, 100d), RecordedTradeCostModel.INSTANCE),
                RecordedTradeCostModel.INSTANCE, new LinearBorrowingCostModel(0d));
        ExcessReturns returns = new ExcessReturns(series, numFactory.numOf(0.05d), CashReturnPolicy.CASH_EARNS_ZERO,
                position, EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);
        List<Sample> samples = RatioSampleSupport
                .samples(series, new BaseTradingRecord(position), SamplingFrequency.BAR, ZoneOffset.UTC, returns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();
        double years = 86400d / org.ta4j.core.utils.TimeConstants.SECONDS_PER_YEAR;
        assertEquals(1, samples.size());
        assertNumEquals(years, samples.getFirst().deltaYears());
        assertNumEquals(1.1d / Math.pow(1.05d, years) - 1d, samples.getFirst().value());
    }

    @Test
    public void tradeSamplingMarksSpotStructuralExitBeyondHorizonAndIgnoresIt() {
        BarSeries series = buildDailySeries("spot_exit_beyond_horizon", new double[] { 100d, 105d, 110d });
        Trade entry = Trade.fromFill(spotFill(0, ExecutionSide.BUY, 1d, 100d), RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFill(spotFill(3, ExecutionSide.SELL, 1d, 120d), RecordedTradeCostModel.INSTANCE);
        Position position = new Position(entry, exit, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        BaseTradingRecord record = new BaseTradingRecord(position);
        ExcessReturns markReturns = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO,
                record, OpenPositionHandling.MARK_TO_MARKET);
        ExcessReturns ignoreReturns = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO,
                record, OpenPositionHandling.IGNORE);

        List<Sample> markSamples = RatioSampleSupport
                .samples(series, record, SamplingFrequency.TRADE, ZoneOffset.UTC, markReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();
        List<Sample> ignoreSamples = RatioSampleSupport
                .samples(series, record, SamplingFrequency.TRADE, ZoneOffset.UTC, ignoreReturns,
                        OpenPositionHandling.IGNORE)
                .toList();

        assertEquals(1, markSamples.size());
        assertNumEquals(0.1d, markSamples.getFirst().value());
        assertEquals(0, ignoreSamples.size());
    }

    @Test
    public void tradeSamplingIncludesSameBarFuturesEntryFeeInInitialReturn() {
        BarSeries series = buildDailySeries("same_bar_futures_fee", new double[] { 100d, 100d });
        FuturesContract contract = futuresContract();
        Trade entry = Trade.fromFill(
                fill(contract, 0, ExecutionSide.BUY, 1d, 100d).toBuilder().fees(List.of(commissionFee())).build(),
                RecordedTradeCostModel.INSTANCE);
        Trade exit = Trade.fromFill(fill(contract, 0, ExecutionSide.SELL, 1d, 100d), RecordedTradeCostModel.INSTANCE);
        Position position = new Position(entry, exit, RecordedTradeCostModel.INSTANCE,
                new LinearBorrowingCostModel(0d));
        BaseTradingRecord record = new BaseTradingRecord(position);
        ExcessReturns returns = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO, position,
                EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.MARK_TO_MARKET);

        List<Sample> samples = RatioSampleSupport
                .samples(series, record, SamplingFrequency.TRADE, ZoneOffset.UTC, returns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();

        assertEquals(1, samples.size());
        assertNumEquals(-0.01d, samples.getFirst().value());
    }

    @Test
    public void tradeSamplingAnchorsLaterFuturesEntryAtRetainedHeadAfterPreWindowSeed() {
        BarSeries source = buildDailySeries("retained_futures_trade_anchor",
                new double[] { 100d, 100d, 100d, 100d, 110d });
        source.setMaximumBarCount(3);
        FuturesContract contract = futuresContract();
        Position preWindow = new Position(
                Trade.fromFill(fill(contract, 0, ExecutionSide.BUY, 1d, 100d), RecordedTradeCostModel.INSTANCE),
                Trade.fromFill(fill(contract, 2, ExecutionSide.SELL, 1d, 100d), RecordedTradeCostModel.INSTANCE),
                RecordedTradeCostModel.INSTANCE, new LinearBorrowingCostModel(0d));
        Position later = new Position(Trade.fromFill(
                fill(contract, 3, ExecutionSide.BUY, 1d, 100d).toBuilder().fees(List.of(commissionFee())).build(),
                RecordedTradeCostModel.INSTANCE),
                Trade.fromFill(fill(contract, 4, ExecutionSide.SELL, 1d, 110d), RecordedTradeCostModel.INSTANCE),
                RecordedTradeCostModel.INSTANCE, new LinearBorrowingCostModel(0d));
        TradingRecord record = new AggregatePositionTradingRecord(List.of(preWindow, later), contract,
                numFactory.numOf(100d));
        ExcessReturns returns = new ExcessReturns(source, numFactory.zero(), CashReturnPolicy.CASH_EARNS_ZERO, record,
                OpenPositionHandling.MARK_TO_MARKET);

        List<Sample> samples = RatioSampleSupport
                .samples(source, record, SamplingFrequency.TRADE, ZoneOffset.UTC, returns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();

        assertEquals(2, samples.size());
        assertNumEquals(0d, samples.get(0).value());
        assertNumEquals(0.09d, samples.get(1).value());
    }

    private TradeFee commissionFee() {
        return TradeFee.builder().type(TradeFee.Type.COMMISSION).amount(numFactory.one()).currency("USD").build();
    }

    private FuturesContract futuresContract() {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.one())
                .build();
    }

    private TradeFill spotFill(int index, ExecutionSide side, double amount, double price) {
        return TradeFill.builder()
                .index(index)
                .time(Instant.parse("2024-01-01T00:00:00Z").plusSeconds(index))
                .price(numFactory.numOf(price))
                .amount(numFactory.numOf(amount))
                .side(side)
                .build();
    }
}
