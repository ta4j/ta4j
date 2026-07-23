/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.PercentRankIndicator;
import org.ta4j.core.indicators.helpers.SumIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Composite score that estimates whether a strong trend has cooled enough for
 * mean-reversion or reset logic to resume.
 *
 * <p>
 * The score uses four one-sided sub-signals:
 * </p>
 *
 * <ul>
 * <li>falling ADX</li>
 * <li>MACD histogram reverting toward zero</li>
 * <li>price re-centering toward a medium EMA</li>
 * <li>renewed compression</li>
 * </ul>
 *
 * <p>
 * The final value is expressed on a {@code 0-100} scale, where higher values
 * indicate stronger evidence that a prior trend has likely concluded or cooled.
 * </p>
 *
 * @since 0.22.7
 */
public class TrendConclusionIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> adxFadeIndicator;
    private final Indicator<Num> histogramMeanReversionIndicator;
    private final Indicator<Num> priceRecenterIndicator;
    private final Indicator<Num> compressionComponent;
    private final transient Indicator<Num> adxFadeComponent;
    private final transient Indicator<Num> compositeIndicator;
    private final int normalizationBarCount;

    /**
     * Creates a trend-conclusion score from price and volatility defaults.
     *
     * @param series                the bar series
     * @param mediumEmaBarCount     the medium EMA used for re-centering
     * @param macdFastBarCount      the fast MACD period
     * @param macdSlowBarCount      the slow MACD period
     * @param macdSignalBarCount    the signal MACD EMA period
     * @param adxBarCount           the ADX period
     * @param compressionBarCount   the compression metric period
     * @param normalizationBarCount the lookback used for percentile normalization
     * @since 0.22.7
     */
    public TrendConclusionIndicator(BarSeries series, int mediumEmaBarCount, int macdFastBarCount, int macdSlowBarCount,
            int macdSignalBarCount, int adxBarCount, int compressionBarCount, int normalizationBarCount) {
        this(validatedConfig(series, mediumEmaBarCount, macdFastBarCount, macdSlowBarCount, macdSignalBarCount,
                adxBarCount, compressionBarCount, normalizationBarCount));
    }

    /**
     * Creates a trend-conclusion score from explicit component indicators.
     *
     * @param adxFadeIndicator                signal that increases when ADX is
     *                                        fading
     * @param histogramMeanReversionIndicator signal that increases as MACD
     *                                        histogram contracts toward zero
     * @param priceRecenterIndicator          signal that increases as price
     *                                        re-centers toward the medium trend
     *                                        reference
     * @param compressionIndicator            compression score
     * @param normalizationBarCount           the lookback used for percentile
     *                                        normalization
     * @since 0.22.7
     */
    public TrendConclusionIndicator(Indicator<Num> adxFadeIndicator, Indicator<Num> histogramMeanReversionIndicator,
            Indicator<Num> priceRecenterIndicator, Indicator<Num> compressionIndicator, int normalizationBarCount) {
        this(validatedConfig(adxFadeIndicator, histogramMeanReversionIndicator, priceRecenterIndicator,
                compressionIndicator, normalizationBarCount));
    }

    private TrendConclusionIndicator(Config config) {
        super(config.series(), identityOfExact(TrendConclusionIndicator.class, config.identityParts()));
        this.normalizationBarCount = config.normalizationBarCount();
        this.adxFadeIndicator = config.adxFadeIndicator();
        this.histogramMeanReversionIndicator = config.histogramMeanReversionIndicator();
        this.priceRecenterIndicator = config.priceRecenterIndicator();
        this.adxFadeComponent = config.adxFadeComponent();
        this.compressionComponent = config.compressionComponent();
        this.compositeIndicator = config.compositeIndicator();
    }

    private static Config validatedConfig(BarSeries series, int mediumEmaBarCount, int macdFastBarCount,
            int macdSlowBarCount, int macdSignalBarCount, int adxBarCount, int compressionBarCount,
            int normalizationBarCount) {
        return validatedConfig(buildAdxFade(series, adxBarCount),
                buildHistogramReversion(series, macdFastBarCount, macdSlowBarCount, macdSignalBarCount,
                        normalizationBarCount),
                buildPriceRecenter(series, mediumEmaBarCount, normalizationBarCount),
                new CompressionIndicator(series, compressionBarCount, normalizationBarCount), normalizationBarCount,
                mediumEmaBarCount, macdFastBarCount, macdSlowBarCount, macdSignalBarCount, adxBarCount,
                compressionBarCount, normalizationBarCount);
    }

    private static Config validatedConfig(Indicator<Num> adxFadeIndicator,
            Indicator<Num> histogramMeanReversionIndicator, Indicator<Num> priceRecenterIndicator,
            Indicator<Num> compressionIndicator, int normalizationBarCount) {
        return validatedConfig(adxFadeIndicator, histogramMeanReversionIndicator, priceRecenterIndicator,
                compressionIndicator, normalizationBarCount, adxFadeIndicator, histogramMeanReversionIndicator,
                priceRecenterIndicator, compressionIndicator, normalizationBarCount);
    }

    private static Config validatedConfig(Indicator<Num> adxFadeIndicator,
            Indicator<Num> histogramMeanReversionIndicator, Indicator<Num> priceRecenterIndicator,
            Indicator<Num> compressionIndicator, int normalizationBarCount, Object... identityParts) {
        BarSeries series = IndicatorUtils.requireSameSeries(adxFadeIndicator, histogramMeanReversionIndicator,
                priceRecenterIndicator, compressionIndicator);
        if (normalizationBarCount < 1) {
            throw new IllegalArgumentException("normalizationBarCount must be greater than zero");
        }
        Indicator<Num> adxFadeComponent = oneSidedPercentRank(adxFadeIndicator, normalizationBarCount);
        Indicator<Num> compositeIndicator = NumericIndicator.of(new SumIndicator(adxFadeComponent,
                histogramMeanReversionIndicator, priceRecenterIndicator, compressionIndicator)).dividedBy(4);
        return new Config(series, adxFadeIndicator, histogramMeanReversionIndicator, priceRecenterIndicator,
                adxFadeComponent, compressionIndicator, compositeIndicator, normalizationBarCount, identityParts);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    protected Num calculate(int index) {
        return compositeIndicator.getValue(index);
    }

    /**
     * {@inheritDoc}
     *
     * @since 0.22.7
     */
    @Override
    public int getCountOfUnstableBars() {
        return compositeIndicator.getCountOfUnstableBars();
    }

    /**
     * @return the ADX-fade contribution
     * @since 0.22.7
     */
    public Indicator<Num> getAdxFadeComponent() {
        return adxFadeComponent;
    }

    /**
     * @return the MACD-histogram mean-reversion contribution
     * @since 0.22.7
     */
    public Indicator<Num> getHistogramMeanReversionComponent() {
        return histogramMeanReversionIndicator;
    }

    /**
     * @return the price re-centering contribution
     * @since 0.22.7
     */
    public Indicator<Num> getPriceRecenterComponent() {
        return priceRecenterIndicator;
    }

    /**
     * @return the compression contribution
     * @since 0.22.7
     */
    public Indicator<Num> getCompressionComponent() {
        return compressionComponent;
    }

    /**
     * @return the percentile-normalization lookback
     * @since 0.22.7
     */
    public int getNormalizationBarCount() {
        return normalizationBarCount;
    }

    private static Indicator<Num> buildAdxFade(BarSeries series, int adxBarCount) {
        ADXIndicator adx = new ADXIndicator(series, adxBarCount);
        return NumericIndicator.of(new DifferenceIndicator(adx)).multipliedBy(-1);
    }

    private static Indicator<Num> buildHistogramReversion(BarSeries series, int fastBarCount, int slowBarCount,
            int signalBarCount, int normalizationBarCount) {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        MACDIndicator macdLine = new MACDIndicator(closePrice, fastBarCount, slowBarCount);
        Indicator<Num> signalLine = NumericIndicator.of(macdLine).ema(signalBarCount);
        Indicator<Num> histogram = NumericIndicator.of(macdLine).minus(signalLine).abs();
        return invertedPercentRank(histogram, normalizationBarCount);
    }

    private static Indicator<Num> buildPriceRecenter(BarSeries series, int mediumEmaBarCount,
            int normalizationBarCount) {
        NumericIndicator close = NumericIndicator.closePrice(series);
        Indicator<Num> mediumEma = close.ema(mediumEmaBarCount);
        Indicator<Num> stretch = NumericIndicator.of(new StretchZScoreIndicator(close, mediumEma, mediumEmaBarCount))
                .abs();
        return invertedPercentRank(stretch, normalizationBarCount);
    }

    private static Indicator<Num> oneSidedPercentRank(Indicator<Num> indicator, int normalizationBarCount) {
        return NumericIndicator.of(new PercentRankIndicator(indicator, normalizationBarCount));
    }

    private static Indicator<Num> invertedPercentRank(Indicator<Num> indicator, int normalizationBarCount) {
        return NumericIndicator.of(new PercentRankIndicator(indicator, normalizationBarCount))
                .multipliedBy(-1)
                .plus(100);
    }

    private record Config(BarSeries series, Indicator<Num> adxFadeIndicator,
            Indicator<Num> histogramMeanReversionIndicator, Indicator<Num> priceRecenterIndicator,
            Indicator<Num> adxFadeComponent, Indicator<Num> compressionComponent, Indicator<Num> compositeIndicator,
            int normalizationBarCount, Object[] identityParts) {

        private Config {
            identityParts = identityParts.clone();
        }

        @Override
        public Object[] identityParts() {
            return identityParts.clone();
        }
    }
}
