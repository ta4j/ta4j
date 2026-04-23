/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.PercentRankIndicator;
import org.ta4j.core.indicators.helpers.SumIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Directional composite score for trend and regime assessment.
 *
 * <p>
 * The score combines four normalized components:
 * </p>
 *
 * <ul>
 * <li>EMA alignment</li>
 * <li>MACD histogram state</li>
 * <li>ADX trend strength, signed by the current directional bias</li>
 * <li>ADX change, also signed by the current directional bias</li>
 * </ul>
 *
 * <p>
 * The final value is expressed on a roughly {@code -100} to {@code +100} scale,
 * where positive values indicate bullish trend pressure, negative values
 * indicate bearish trend pressure, and values near zero indicate mixed or weak
 * regime conditions.
 * </p>
 *
 * @since 0.22.7
 */
public class TrendScoreIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> emaAlignmentComponent;
    private final Indicator<Num> macdHistogramComponent;
    private final Indicator<Num> adxStrengthComponent;
    private final Indicator<Num> adxChangeComponent;
    private final Indicator<Num> compositeIndicator;
    private final int normalizationBarCount;

    /**
     * Creates a trend score with standard MACD-style defaults.
     *
     * @param series                the bar series
     * @param fastEmaBarCount       the fast EMA period
     * @param slowEmaBarCount       the slow EMA period
     * @param signalBarCount        the MACD signal EMA period
     * @param adxBarCount           the ADX period
     * @param normalizationBarCount the lookback used for percentile normalization
     * @since 0.22.7
     */
    public TrendScoreIndicator(BarSeries series, int fastEmaBarCount, int slowEmaBarCount, int signalBarCount,
            int adxBarCount, int normalizationBarCount) {
        this(buildEmaAlignment(series, fastEmaBarCount, slowEmaBarCount),
                buildMacdHistogram(series, fastEmaBarCount, slowEmaBarCount, signalBarCount),
                new ADXIndicator(series, adxBarCount), normalizationBarCount);
    }

    /**
     * Creates a trend score from explicit directional and ADX inputs.
     *
     * @param emaAlignmentIndicator  directional EMA-alignment indicator
     * @param macdHistogramIndicator directional MACD histogram indicator
     * @param adxIndicator           ADX strength indicator
     * @param normalizationBarCount  the lookback used for percentile normalization
     * @since 0.22.7
     */
    public TrendScoreIndicator(Indicator<Num> emaAlignmentIndicator, Indicator<Num> macdHistogramIndicator,
            Indicator<Num> adxIndicator, int normalizationBarCount) {
        super(requireSameSeries(emaAlignmentIndicator, macdHistogramIndicator, adxIndicator));
        if (normalizationBarCount < 1) {
            throw new IllegalArgumentException("normalizationBarCount must be greater than zero");
        }
        this.normalizationBarCount = normalizationBarCount;
        this.emaAlignmentComponent = centeredPercentRank(emaAlignmentIndicator, normalizationBarCount);
        this.macdHistogramComponent = centeredPercentRank(macdHistogramIndicator, normalizationBarCount);
        Indicator<Num> directionBias = NumericIndicator.of(this.emaAlignmentComponent)
                .plus(this.macdHistogramComponent)
                .dividedBy(2);
        this.adxStrengthComponent = signedMagnitude(centeredPercentRank(adxIndicator, normalizationBarCount),
                directionBias);
        this.adxChangeComponent = signedMagnitude(
                centeredPercentRank(new DifferenceIndicator(adxIndicator), normalizationBarCount), directionBias);
        this.compositeIndicator = NumericIndicator
                .of(new SumIndicator(this.emaAlignmentComponent, this.macdHistogramComponent, this.adxStrengthComponent,
                        this.adxChangeComponent))
                .dividedBy(4)
                .multipliedBy(100);
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
     * @return the normalized EMA-alignment contribution
     * @since 0.22.7
     */
    public Indicator<Num> getEmaAlignmentComponent() {
        return emaAlignmentComponent;
    }

    /**
     * @return the normalized MACD-histogram contribution
     * @since 0.22.7
     */
    public Indicator<Num> getMacdHistogramComponent() {
        return macdHistogramComponent;
    }

    /**
     * @return the directional ADX-strength contribution
     * @since 0.22.7
     */
    public Indicator<Num> getAdxStrengthComponent() {
        return adxStrengthComponent;
    }

    /**
     * @return the directional ADX-change contribution
     * @since 0.22.7
     */
    public Indicator<Num> getAdxChangeComponent() {
        return adxChangeComponent;
    }

    /**
     * @return the percentile-normalization lookback
     * @since 0.22.7
     */
    public int getNormalizationBarCount() {
        return normalizationBarCount;
    }

    private static Indicator<Num> buildEmaAlignment(BarSeries series, int fastEmaBarCount, int slowEmaBarCount) {
        NumericIndicator close = NumericIndicator.closePrice(series);
        return close.ema(fastEmaBarCount).minus(close.ema(slowEmaBarCount)).dividedBy(close).multipliedBy(100);
    }

    private static Indicator<Num> buildMacdHistogram(BarSeries series, int fastEmaBarCount, int slowEmaBarCount,
            int signalBarCount) {
        Indicator<Num> closePrice = new ClosePriceIndicator(series);
        MACDIndicator macdLine = new MACDIndicator(closePrice, fastEmaBarCount, slowEmaBarCount);
        Indicator<Num> signalLine = NumericIndicator.of(macdLine).ema(signalBarCount);
        return NumericIndicator.of(macdLine).minus(signalLine);
    }

    private static Indicator<Num> centeredPercentRank(Indicator<Num> indicator, int normalizationBarCount) {
        return NumericIndicator.of(new PercentRankIndicator(indicator, normalizationBarCount)).minus(50).dividedBy(50);
    }

    private static Indicator<Num> signedMagnitude(Indicator<Num> magnitudeIndicator,
            Indicator<Num> directionIndicator) {
        return new CachedIndicator<>(requireSameSeries(magnitudeIndicator, directionIndicator)) {
            @Override
            protected Num calculate(int index) {
                Num magnitude = magnitudeIndicator.getValue(index);
                Num direction = directionIndicator.getValue(index);
                if (Num.isNaNOrNull(magnitude) || Num.isNaNOrNull(direction) || direction.isZero()) {
                    return NaN.NaN;
                }
                return direction.isNegative() ? magnitude.abs().multipliedBy(magnitude.getNumFactory().minusOne())
                        : magnitude.abs();
            }

            @Override
            public int getCountOfUnstableBars() {
                return Math.max(magnitudeIndicator.getCountOfUnstableBars(),
                        directionIndicator.getCountOfUnstableBars());
            }
        };
    }

    private static BarSeries requireSameSeries(Indicator<Num>... indicators) {
        Indicator<Num> first = Objects.requireNonNull(indicators[0], "indicators[0]");
        BarSeries series = Objects.requireNonNull(first.getBarSeries(), "indicator must reference a bar series");
        for (Indicator<Num> indicator : indicators) {
            Indicator<Num> resolved = Objects.requireNonNull(indicator, "indicator");
            if (!Objects.equals(series, resolved.getBarSeries())) {
                throw new IllegalArgumentException("Indicators must share the same bar series");
            }
        }
        return series;
    }
}
