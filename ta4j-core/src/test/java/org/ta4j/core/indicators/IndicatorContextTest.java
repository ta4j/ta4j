/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.elliott.EmpiricalElliottWaveForecastIndicator;
import org.ta4j.core.indicators.elliott.EmpiricalElliottWaveForecastIndicator.Settings;
import org.ta4j.core.indicators.macd.VolatilityNormalizedMACDIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class IndicatorContextTest {

    @Test
    public void standardSeriesRetainsContextAndCanonicalFactories() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).build();
        IndicatorContext context = series.indicators();

        assertSame(context, series.indicators());
        assertSame(context.closePrice(), context.closePrice());
        assertSame(context.openPrice(), context.openPrice());
        assertSame(context.highPrice(), context.highPrice());
        assertSame(context.lowPrice(), context.lowPrice());
        assertSame(context.typicalPrice(), context.typicalPrice());
        assertSame(context.volume(), context.volume());
        assertSame(context.sma(context.closePrice(), 3), context.sma(context.closePrice(), 3));
        assertSame(context.ema(context.closePrice(), 3), context.ema(context.closePrice(), 3));
        assertSame(context.atr(3), context.atr(3));
        assertSame(context.adx(3), context.adx(3));
        assertSame(context.rsi(context.closePrice(), 3), context.rsi(context.closePrice(), 3));
        assertSame(context.roc(context.closePrice(), 3), context.roc(context.closePrice(), 3));
        assertSame(context.macd(context.closePrice(), 3, 5), context.macd(context.closePrice(), 3, 5));
        assertSame(context.previous(context.closePrice(), 2), context.previous(context.closePrice(), 2));
        assertSame(context.highest(context.closePrice(), 3), context.highest(context.closePrice(), 3));
        assertSame(context.lowest(context.closePrice(), 3), context.lowest(context.closePrice(), 3));
        assertSame(context.standardDeviation(context.closePrice(), 3),
                context.standardDeviation(context.closePrice(), 3));
        assertSame(context.volatilityNormalizedMacd(context.closePrice(), 3, 5, 2),
                context.volatilityNormalizedMacd(context.closePrice(), 3, 5, 2));
        assertSame(context.linearRegression(context.closePrice(), 3),
                context.linearRegression(context.closePrice(), 3));
        assertSame(context.stretchZScore(4), context.stretchZScore(4));
        assertSame(context.trendScore(3, 5, 2, 3, 5), context.trendScore(3, 5, 2, 3, 5));
        assertSame(context.trendConclusion(4, 3, 5, 2, 3, 4, 5), context.trendConclusion(4, 3, 5, 2, 3, 4, 5));
        Settings settings = Settings.intradayDefaults();
        assertSame(context.empiricalElliottWaveForecast(settings),
                context.empiricalElliottWaveForecast(Settings.intradayDefaults()));
    }

    @Test
    public void readOnlyViewsDelegateWhileSubseriesOwnIndependentContexts() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        assertSame(series.indicators(), close.getBarSeries().indicators());
        assertEquals(series.getBarHistoryEpoch(), close.getBarSeries().getBarHistoryEpoch());
        assertNotSame(series.indicators(), series.getSubSeries(1, 4).indicators());
    }

    @Test
    public void unsupportedEpochKeepsOtherwiseEquivalentIndicatorsIsolated() {
        BarSeries series = new UnsupportedEpochSeries();
        series.addBar(new MockBarSeriesBuilder().withData(1).build().getBar(0));
        SMAIndicator first = new SMAIndicator(new ClosePriceIndicator(series), 2);
        SMAIndicator second = new SMAIndicator(new ClosePriceIndicator(series), 2);

        assertNotSame(((CachedIndicator<?>) first).sharedStateIdentity(),
                ((CachedIndicator<?>) second).sharedStateIdentity());
    }

    @Test
    public void ordinaryConstructionSharesAuditedComplexIndicatorStates() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).build();

        assertEquivalentState(NetMomentumIndicator.forRsi(new RSIIndicator(new ClosePriceIndicator(series), 3), 5),
                NetMomentumIndicator.forRsi(new RSIIndicator(new ClosePriceIndicator(series), 3), 5));
        assertEquivalentState(new VolatilityNormalizedMACDIndicator(new ClosePriceIndicator(series), 3, 5, 2),
                new VolatilityNormalizedMACDIndicator(new ClosePriceIndicator(series), 3, 5, 2));
        assertEquivalentState(new SimpleLinearRegressionIndicator(new ClosePriceIndicator(series), 4),
                new SimpleLinearRegressionIndicator(new ClosePriceIndicator(series), 4));
        assertEquivalentState(new StretchZScoreIndicator(series, 4), new StretchZScoreIndicator(series, 4));
        assertEquivalentState(new TrendScoreIndicator(series, 3, 5, 2, 3, 5),
                new TrendScoreIndicator(series, 3, 5, 2, 3, 5));
        assertEquivalentState(new TrendConclusionIndicator(series, 4, 3, 5, 2, 3, 4, 5),
                new TrendConclusionIndicator(series, 4, 3, 5, 2, 3, 4, 5));
        assertEquivalentState(new EmpiricalElliottWaveForecastIndicator(series),
                new EmpiricalElliottWaveForecastIndicator(series));
    }

    @Test
    public void sharedStatePinsItsWeakMapKeyWhileIndicatorsRemainReachable() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        SMAIndicator first = new SMAIndicator(new ClosePriceIndicator(series), 2);
        SMAIndicator second = new SMAIndicator(new ClosePriceIndicator(series), 2);
        CachedIndicator<?> firstCached = first;
        CachedIndicator<?> secondCached = second;
        CachedIndicator.SharedState<?> state = (CachedIndicator.SharedState<?>) firstCached.sharedStateIdentity();

        assertSame(firstCached.indicatorIdentity(), state.identity);
        assertSame(state, secondCached.sharedStateIdentity());
    }

    @Test
    public void canonicalStateTracksMaximumBarCountChangesAtConstruction() {
        BaseBarSeries series = (BaseBarSeries) new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        IndicatorContext context = series.indicators();
        SMAIndicator unbounded = context.sma(context.closePrice(), 2);

        series.setMaximumBarCount(3);
        SMAIndicator bounded = context.sma(context.closePrice(), 2);

        assertNotSame(unbounded, bounded);
        assertNotSame(((CachedIndicator<?>) unbounded).sharedStateIdentity(),
                ((CachedIndicator<?>) bounded).sharedStateIdentity());
        assertSame(bounded, context.sma(context.closePrice(), 2));
    }

    private static void assertEquivalentState(CachedIndicator<?> first, CachedIndicator<?> second) {
        assertNotSame(first, second);
        assertSame(first.sharedStateIdentity(), second.sharedStateIdentity());
    }

    @Test
    public void deserializedSeriesCreatesFreshRuntimeContext() throws Exception {
        BaseBarSeries series = (BaseBarSeries) new MockBarSeriesBuilder().withData(1, 2, 3).build();
        IndicatorContext originalContext = series.indicators();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(series);
        }

        BaseBarSeries restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (BaseBarSeries) input.readObject();
        }

        assertNotSame(originalContext, restored.indicators());
        assertSame(restored.indicators(), restored.indicators());
    }

    private static final class UnsupportedEpochSeries extends BaseBarSeries {

        private UnsupportedEpochSeries() {
            super("unsupported", java.util.List.of());
        }

        @Override
        public long getBarHistoryEpoch() {
            return -1L;
        }
    }
}
