/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicator.IndicatorIdentity;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.elliott.EmpiricalElliottWaveForecastIndicator;
import org.ta4j.core.indicators.elliott.EmpiricalElliottWaveForecastIndicator.Settings;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.TypicalPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.statistics.SimpleLinearRegressionIndicator;
import org.ta4j.core.indicators.macd.VolatilityNormalizedMACDIndicator;
import org.ta4j.core.num.Num;

/**
 * Series-scoped home for canonical indicator construction and transparent
 * calculation-state sharing.
 *
 * <p>
 * Obtain this context from {@link BarSeries#indicators()}. Deterministic
 * indicators created normally can share calculation state as well; these
 * factories simply make common graphs concise and reuse a canonical indicator
 * instance while it remains reachable. No cache keys or lifecycle management
 * are required.
 *
 * <p>
 * Custom {@link BarSeries} implementations that want sharing should retain one
 * context and return it from {@link BarSeries#indicators()}, while also
 * providing a correct {@link BarSeries#getBarHistoryEpoch() history epoch}. A
 * series that uses the default {@code -1} epoch remains isolated.
 *
 * @since 0.23.1
 */
public final class IndicatorContext {

    private final BarSeries series;
    private final Map<IndicatorIdentity, WeakReference<CachedIndicator.SharedState<?>>> sharedStates = new WeakHashMap<>();
    private final Map<IndicatorIdentity, WeakReference<AbstractIndicator<?>>> canonicalIndicators = new WeakHashMap<>();

    /**
     * Creates a context for a custom bar-series implementation.
     *
     * <p>
     * Most clients should use {@link BarSeries#indicators()} instead. Sharing is
     * automatically available only when the series reports a non-negative history
     * epoch.
     *
     * @param series the owning series
     * @since 0.23.1
     */
    public IndicatorContext(BarSeries series) {
        this.series = AbstractIndicator.unwrapBarSeries(Objects.requireNonNull(series, "series"));
    }

    /**
     * @return the canonical close-price input
     * @since 0.23.1
     */
    public ClosePriceIndicator closePrice() {
        return canonical(new ClosePriceIndicator(series));
    }

    /**
     * @return the canonical open-price input
     * @since 0.23.1
     */
    public OpenPriceIndicator openPrice() {
        return canonical(new OpenPriceIndicator(series));
    }

    /**
     * @return the canonical high-price input
     * @since 0.23.1
     */
    public HighPriceIndicator highPrice() {
        return canonical(new HighPriceIndicator(series));
    }

    /**
     * @return the canonical low-price input
     * @since 0.23.1
     */
    public LowPriceIndicator lowPrice() {
        return canonical(new LowPriceIndicator(series));
    }

    /**
     * @return the canonical typical-price input
     * @since 0.23.1
     */
    public TypicalPriceIndicator typicalPrice() {
        return canonical(new TypicalPriceIndicator(series));
    }

    /**
     * @return the canonical per-bar volume input
     * @since 0.23.1
     */
    public VolumeIndicator volume() {
        return canonical(new VolumeIndicator(series));
    }

    /**
     * @param barCount aggregation window
     * @return the canonical rolling-volume indicator
     * @since 0.23.1
     */
    public VolumeIndicator volume(int barCount) {
        return canonical(new VolumeIndicator(series, barCount));
    }

    /**
     * @param indicator input indicator
     * @param barCount  averaging window
     * @return the canonical simple moving average
     * @since 0.23.1
     */
    public SMAIndicator sma(Indicator<Num> indicator, int barCount) {
        requireSameSeries(indicator);
        return canonical(new SMAIndicator(indicator, barCount));
    }

    /**
     * @param indicator input indicator
     * @param barCount  averaging window
     * @return the canonical exponential moving average
     * @since 0.23.1
     */
    public EMAIndicator ema(Indicator<Num> indicator, int barCount) {
        requireSameSeries(indicator);
        return canonical(new EMAIndicator(indicator, barCount));
    }

    /**
     * @param indicator input indicator
     * @param barCount  window
     * @return the canonical standard deviation
     * @since 0.23.1
     */
    public StandardDeviationIndicator standardDeviation(Indicator<Num> indicator, int barCount) {
        requireSameSeries(indicator);
        return canonical(new StandardDeviationIndicator(indicator, barCount));
    }

    /**
     * @param barCount averaging window
     * @return the canonical average true range
     * @since 0.23.1
     */
    public ATRIndicator atr(int barCount) {
        return canonical(new ATRIndicator(series, barCount));
    }

    /**
     * @param barCount directional and ADX averaging window
     * @return the canonical average directional index
     * @since 0.23.1
     */
    public ADXIndicator adx(int barCount) {
        return canonical(new ADXIndicator(series, barCount));
    }

    /**
     * @param diBarCount  directional-index averaging window
     * @param adxBarCount ADX averaging window
     * @return the canonical average directional index
     * @since 0.23.1
     */
    public ADXIndicator adx(int diBarCount, int adxBarCount) {
        return canonical(new ADXIndicator(series, diBarCount, adxBarCount));
    }

    /**
     * @param indicator input indicator
     * @param barCount  averaging window
     * @return the canonical relative strength index
     * @since 0.23.1
     */
    public RSIIndicator rsi(Indicator<Num> indicator, int barCount) {
        requireSameSeries(indicator);
        return canonical(new RSIIndicator(indicator, barCount));
    }

    /**
     * @param indicator input indicator
     * @param barCount  lookback window
     * @return the canonical rate of change
     * @since 0.23.1
     */
    public ROCIndicator roc(Indicator<Num> indicator, int barCount) {
        requireSameSeries(indicator);
        return canonical(new ROCIndicator(indicator, barCount));
    }

    /**
     * @param indicator input indicator
     * @return the canonical MACD with standard windows
     * @since 0.23.1
     */
    public MACDIndicator macd(Indicator<Num> indicator) {
        requireSameSeries(indicator);
        return canonical(new MACDIndicator(indicator));
    }

    /**
     * @param indicator     input indicator
     * @param shortBarCount short averaging window
     * @param longBarCount  long averaging window
     * @return the canonical MACD
     * @since 0.23.1
     */
    public MACDIndicator macd(Indicator<Num> indicator, int shortBarCount, int longBarCount) {
        requireSameSeries(indicator);
        return canonical(new MACDIndicator(indicator, shortBarCount, longBarCount));
    }

    /**
     * @param indicator input indicator
     * @param barCount  lag
     * @return the canonical previous-value indicator
     * @since 0.23.1
     */
    public PreviousValueIndicator previous(Indicator<Num> indicator, int barCount) {
        requireSameSeries(indicator);
        return canonical(new PreviousValueIndicator(indicator, barCount));
    }

    /**
     * @param indicator input indicator
     * @return the canonical one-bar previous-value indicator
     * @since 0.23.1
     */
    public PreviousValueIndicator previous(Indicator<Num> indicator) {
        return previous(indicator, 1);
    }

    /**
     * @param indicator input indicator
     * @param barCount  window
     * @return the canonical rolling highest value
     * @since 0.23.1
     */
    public HighestValueIndicator highest(Indicator<Num> indicator, int barCount) {
        requireSameSeries(indicator);
        return canonical(new HighestValueIndicator(indicator, barCount));
    }

    /**
     * @param indicator input indicator
     * @param barCount  window
     * @return the canonical rolling lowest value
     * @since 0.23.1
     */
    public LowestValueIndicator lowest(Indicator<Num> indicator, int barCount) {
        requireSameSeries(indicator);
        return canonical(new LowestValueIndicator(indicator, barCount));
    }

    /**
     * @param indicator      price input
     * @param fastBarCount   fast EMA window
     * @param slowBarCount   slow EMA and ATR window
     * @param signalBarCount signal EMA window
     * @return the canonical volatility-normalized MACD indicator
     * @since 0.23.1
     */
    public VolatilityNormalizedMACDIndicator volatilityNormalizedMacd(Indicator<Num> indicator, int fastBarCount,
            int slowBarCount, int signalBarCount) {
        requireSameSeries(indicator);
        return canonical(new VolatilityNormalizedMACDIndicator(indicator, fastBarCount, slowBarCount, signalBarCount));
    }

    /**
     * @param indicator input indicator
     * @param barCount  regression window
     * @return the canonical rolling linear regression
     * @since 0.23.1
     */
    public SimpleLinearRegressionIndicator linearRegression(Indicator<Num> indicator, int barCount) {
        requireSameSeries(indicator);
        return canonical(new SimpleLinearRegressionIndicator(indicator, barCount));
    }

    /**
     * @param barCount normalization window
     * @return the canonical close-price stretch z-score
     * @since 0.23.1
     */
    public StretchZScoreIndicator stretchZScore(int barCount) {
        return canonical(new StretchZScoreIndicator(series, barCount));
    }

    /**
     * @param fastEmaBarCount       fast EMA window
     * @param slowEmaBarCount       slow EMA window
     * @param signalBarCount        MACD signal window
     * @param adxBarCount           ADX window
     * @param normalizationBarCount percentile normalization window
     * @return the canonical trend score
     * @since 0.23.1
     */
    public TrendScoreIndicator trendScore(int fastEmaBarCount, int slowEmaBarCount, int signalBarCount, int adxBarCount,
            int normalizationBarCount) {
        return canonical(new TrendScoreIndicator(series, fastEmaBarCount, slowEmaBarCount, signalBarCount, adxBarCount,
                normalizationBarCount));
    }

    /**
     * @param mediumEmaBarCount     medium EMA window
     * @param macdFastBarCount      fast MACD window
     * @param macdSlowBarCount      slow MACD window
     * @param macdSignalBarCount    MACD signal window
     * @param adxBarCount           ADX window
     * @param compressionBarCount   compression window
     * @param normalizationBarCount percentile normalization window
     * @return the canonical trend-conclusion score
     * @since 0.23.1
     */
    public TrendConclusionIndicator trendConclusion(int mediumEmaBarCount, int macdFastBarCount, int macdSlowBarCount,
            int macdSignalBarCount, int adxBarCount, int compressionBarCount, int normalizationBarCount) {
        return canonical(new TrendConclusionIndicator(series, mediumEmaBarCount, macdFastBarCount, macdSlowBarCount,
                macdSignalBarCount, adxBarCount, compressionBarCount, normalizationBarCount));
    }

    /**
     * @param settings empirical matching settings
     * @return the canonical empirical Elliott-wave forecast
     * @since 0.23.1
     */
    public EmpiricalElliottWaveForecastIndicator empiricalElliottWaveForecast(Settings settings) {
        return canonical(new EmpiricalElliottWaveForecastIndicator(series, settings));
    }

    synchronized <T> CachedIndicator.SharedState<T> sharedState(IndicatorIdentity identity, int cacheLimit,
            long lastBarWaitTimeoutMs) {
        if (identity == null || series.getBarHistoryEpoch() < 0) {
            return new CachedIndicator.SharedState<>(identity, cacheLimit, lastBarWaitTimeoutMs,
                    series.getBarHistoryEpoch());
        }
        WeakReference<CachedIndicator.SharedState<?>> reference = sharedStates.get(identity);
        CachedIndicator.SharedState<?> existing = reference == null ? null : reference.get();
        if (existing != null) {
            @SuppressWarnings("unchecked")
            CachedIndicator.SharedState<T> typed = (CachedIndicator.SharedState<T>) existing;
            return typed;
        }
        CachedIndicator.SharedState<T> created = new CachedIndicator.SharedState<>(identity, cacheLimit,
                lastBarWaitTimeoutMs, series.getBarHistoryEpoch());
        sharedStates.put(identity, new WeakReference<>(created));
        return created;
    }

    private synchronized <I extends AbstractIndicator<?>> I canonical(I candidate) {
        IndicatorIdentity identity = candidate.indicatorIdentity();
        if (identity == null || series.getBarHistoryEpoch() < 0) {
            return candidate;
        }
        WeakReference<AbstractIndicator<?>> reference = canonicalIndicators.get(identity);
        AbstractIndicator<?> existing = reference == null ? null : reference.get();
        if (existing != null) {
            @SuppressWarnings("unchecked")
            I typed = (I) existing;
            return typed;
        }
        canonicalIndicators.put(identity, new WeakReference<>(candidate));
        return candidate;
    }

    private void requireSameSeries(Indicator<?> indicator) {
        BarSeries indicatorSeries = AbstractIndicator
                .unwrapBarSeries(Objects.requireNonNull(indicator, "indicator").getBarSeries());
        if (indicatorSeries != series) {
            throw new IllegalArgumentException("Indicator belongs to a different bar series");
        }
    }
}
