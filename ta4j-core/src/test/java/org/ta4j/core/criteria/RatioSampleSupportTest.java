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
}
