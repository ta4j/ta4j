/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.ExcessReturns;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.Sample;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.BarSeriesUtils;

@ParameterizedClass(name = "NumFactory: {index} (1=DoubleNum, 2=DecimalNum)")
@MethodSource("numFactories")
public class RatioSampleSupportTest {

    private final NumFactory numFactory;

    public RatioSampleSupportTest(NumFactory numFactory) {
        this.numFactory = numFactory;
    }

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
    public void futureExitPositionsRespectIgnoreAtLogicalEnd() {
        BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("future_exit_sample", numFactory, 5, 100d,
                110d, 110d, 110d, 110d, 120d, 120d, 120d, 130d);
        BaseTradingRecord tradingRecord = new BaseTradingRecord(TradeType.BUY);
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), numFactory.one());
        tradingRecord.exit(8, numFactory.numOf(130), numFactory.one());
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

        assertEquals(1, markToMarketSamples.size());
        assertEquals(0, ignoreSamples.size());
        assertNumEquals(0.2d, markToMarketSamples.get(0).value());
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

    @Test
    public void timeSamplingUsesCapturedBoundsAfterSeriesAppend() {
        BarSeries series = buildDailySeries("captured_time_sampling_series", new double[] { 100d, 110d, 121d });
        TradingRecord tradingRecord = RatioCriterionTestSupport.alwaysInvested(series);
        ExcessReturns excessReturns = new ExcessReturns(series, numFactory.zero(),
                CashReturnPolicy.CASH_EARNS_RISK_FREE, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);
        BarSeries appendedBars = buildDailySeriesAt("appended_time_sampling_bars",
                new double[] { 100d, 110d, 121d, 133.1d }, Instant.parse("2024-01-01T00:00:00Z"));
        series.addBar(appendedBars.getBar(3));

        List<Sample> samples = RatioSampleSupport
                .samples(series, tradingRecord, SamplingFrequency.DAY, ZoneOffset.UTC, excessReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();

        assertEquals(2, samples.size());
        assertNumEquals(0.1d, samples.get(0).value());
        assertNumEquals(0.1d, samples.get(1).value());
        assertNumEquals(BarSeriesUtils.deltaYears(series, 0, 1), samples.get(0).deltaYears(), 1e-12);
        assertNumEquals(BarSeriesUtils.deltaYears(series, 1, 2), samples.get(1).deltaYears(), 1e-12);
    }

    @Test
    public void tradeSamplingUsesCapturedEndTimeAfterSeriesAppend() {
        BarSeries series = buildDailySeries("captured_trade_sampling_series", new double[] { 100d, 110d, 121d });
        TradingRecord tradingRecord = buildRecordWithOneOpenPosition(series);
        ExcessReturns excessReturns = new ExcessReturns(series, numFactory.zero(),
                CashReturnPolicy.CASH_EARNS_RISK_FREE, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);
        BarSeries appendedBars = buildDailySeriesAt("appended_trade_sampling_bars",
                new double[] { 100d, 110d, 121d, 133.1d }, Instant.parse("2024-01-01T00:00:00Z"));
        series.addBar(appendedBars.getBar(3));

        List<Sample> samples = RatioSampleSupport
                .samples(series, tradingRecord, SamplingFrequency.TRADE, ZoneOffset.UTC, excessReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();

        assertEquals(3, samples.size());
        assertNumEquals(BarSeriesUtils.deltaYears(series, 2, 2), samples.get(2).deltaYears(), 0d);
    }

    @Test
    public void samplingUsesCapturedEndTimesAfterBarReplacement() {
        BarSeries series = buildDailySeries("captured_time_replacement_series", new double[] { 100d, 110d, 121d });
        TradingRecord tradingRecord = RatioCriterionTestSupport.alwaysInvested(series);
        ExcessReturns excessReturns = new ExcessReturns(series, numFactory.zero(),
                CashReturnPolicy.CASH_EARNS_RISK_FREE, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);
        Instant capturedEnd = excessReturns.getCapturedEndTime(2);
        series.addBar(series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(capturedEnd.plus(Duration.ofDays(7)))
                .openPrice(121d)
                .highPrice(121d)
                .lowPrice(121d)
                .closePrice(121d)
                .volume(1)
                .build(), true);

        List<Sample> timeSamples = RatioSampleSupport
                .samples(series, tradingRecord, SamplingFrequency.DAY, ZoneOffset.UTC, excessReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();
        List<Sample> tradeSamples = RatioSampleSupport
                .samples(series, tradingRecord, SamplingFrequency.TRADE, ZoneOffset.UTC, excessReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();
        var capturedDailyYears = BarSeriesUtils.deltaYears(excessReturns.getCapturedEndTime(1), capturedEnd,
                series.numFactory());

        assertNumEquals(capturedDailyYears, timeSamples.get(1).deltaYears(), 1e-12);
        assertNumEquals(capturedDailyYears, tradeSamples.get(1).deltaYears(), 1e-12);
    }

    private BarSeries buildDailySeriesAt(String name, double[] closes, Instant start) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).withNumFactory(numFactory).build();
        return RatioCriterionTestSupport.buildDailySeries(series, closes, start);
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
}
