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
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.LiveTrade;
import org.ta4j.core.LiveTradingRecord;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.ExcessReturns;
import org.ta4j.core.analysis.ExcessReturns.CashReturnPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.BarSeriesUtils;

@RunWith(Parameterized.class)
public class RatioSampleSupportTest {

    private final NumFactory numFactory;

    public RatioSampleSupportTest(NumFactory numFactory) {
        this.numFactory = numFactory;
    }

    @Parameterized.Parameters(name = "NumFactory: {index} (0=DoubleNum, 1=DecimalNum)")
    public static List<NumFactory> function() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    @Test
    public void barSamplingReturnsExpectedValuesAndDeltaYears() {
        var series = buildDailySeries("bar_sampling_series", new double[] { 100d, 110d, 99d, 108.9d });
        var tradingRecord = RatioCriterionTestSupport.alwaysInvested(series);
        var excessReturns = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_RISK_FREE,
                tradingRecord, OpenPositionHandling.MARK_TO_MARKET);

        var samples = RatioSampleSupport
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
        var series = buildDailySeries("trade_sampling_series", new double[] { 100d, 110d, 99d, 120d });

        var tradingRecord = buildRecordWithOneOpenPosition(series);
        var markToMarketReturns = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_RISK_FREE,
                tradingRecord, OpenPositionHandling.MARK_TO_MARKET);
        var ignoreReturns = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_RISK_FREE,
                tradingRecord, OpenPositionHandling.IGNORE);

        var markToMarketSamples = RatioSampleSupport
                .samples(series, tradingRecord, SamplingFrequency.TRADE, ZoneOffset.UTC, markToMarketReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();
        var ignoreSamples = RatioSampleSupport
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

        assertNumEquals(BarSeriesUtils.deltaYears(series, 0, 1), markToMarketSamples.get(0).deltaYears(), 1e-12);
        assertNumEquals(BarSeriesUtils.deltaYears(series, 1, 2), markToMarketSamples.get(1).deltaYears(), 1e-12);
        assertNumEquals(BarSeriesUtils.deltaYears(series, 2, 3), markToMarketSamples.get(2).deltaYears(), 1e-12);
    }

    @Test
    public void tradeSamplingExpandsLiveTradingRecordOpenLots() {
        var series = buildDailySeries("trade_sampling_live_series", new double[] { 10d, 12d, 14d });

        var liveTradingRecord = new LiveTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        liveTradingRecord.recordFill(0, new LiveTrade(0, Instant.EPOCH, series.getBar(0).getClosePrice(),
                numFactory.one(), null, ExecutionSide.BUY, null, null));
        liveTradingRecord.recordFill(1, new LiveTrade(1, Instant.EPOCH, series.getBar(1).getClosePrice(),
                numFactory.one(), null, ExecutionSide.BUY, null, null));

        var markToMarketReturns = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_RISK_FREE,
                liveTradingRecord, OpenPositionHandling.MARK_TO_MARKET);
        var ignoreReturns = new ExcessReturns(series, numFactory.zero(), CashReturnPolicy.CASH_EARNS_RISK_FREE,
                liveTradingRecord, OpenPositionHandling.IGNORE);

        var markToMarketSamples = RatioSampleSupport
                .samples(series, liveTradingRecord, SamplingFrequency.TRADE, ZoneOffset.UTC, markToMarketReturns,
                        OpenPositionHandling.MARK_TO_MARKET)
                .toList();
        var ignoreSamples = RatioSampleSupport
                .samples(series, liveTradingRecord, SamplingFrequency.TRADE, ZoneOffset.UTC, ignoreReturns,
                        OpenPositionHandling.IGNORE)
                .toList();

        assertEquals(2, markToMarketSamples.size());
        assertEquals(0, ignoreSamples.size());
        assertNumEquals(markToMarketReturns.excessReturn(0, 2), markToMarketSamples.get(0).value(), 1e-12);
        assertNumEquals(markToMarketReturns.excessReturn(1, 2), markToMarketSamples.get(1).value(), 1e-12);
        assertNumEquals(BarSeriesUtils.deltaYears(series, 0, 2), markToMarketSamples.get(0).deltaYears(), 1e-12);
        assertNumEquals(BarSeriesUtils.deltaYears(series, 1, 2), markToMarketSamples.get(1).deltaYears(), 1e-12);
    }

    private TradingRecord buildRecordWithOneOpenPosition(BarSeries series) {
        var tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(0, series.getBar(0).getClosePrice(), series.numFactory().one());
        tradingRecord.exit(1, series.getBar(1).getClosePrice(), series.numFactory().one());
        tradingRecord.enter(1, series.getBar(1).getClosePrice(), series.numFactory().one());
        tradingRecord.exit(2, series.getBar(2).getClosePrice(), series.numFactory().one());
        tradingRecord.enter(2, series.getBar(2).getClosePrice(), series.numFactory().one());
        return tradingRecord;
    }

    private BarSeries buildDailySeries(String name, double[] closes) {
        var series = new BaseBarSeriesBuilder().withName(name).withNumFactory(numFactory).build();
        return RatioCriterionTestSupport.buildDailySeries(series, closes, Instant.parse("2024-01-01T00:00:00Z"));
    }
}
