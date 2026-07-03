/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.bollinger.BollingerBandFacade;
import org.ta4j.core.indicators.donchian.DonchianChannelFacade;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PercentRankIndicator;
import org.ta4j.core.indicators.helpers.SumIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Composite contraction score built from ATR, Bollinger bandwidth, and Donchian
 * width.
 *
 * <p>
 * Each component is converted to a rolling percentile rank, then inverted so a
 * higher score means a tighter market regime. The final indicator returns a
 * score on a {@code 0-100} scale, where values closer to {@code 100} indicate
 * stronger compression.
 * </p>
 *
 * @since 0.22.7
 */
public class CompressionIndicator extends CachedIndicator<Num> {

    private static final Number DEFAULT_BOLLINGER_K = 2;

    private final Indicator<Num> atrCompressionIndicator;
    private final Indicator<Num> bollingerCompressionIndicator;
    private final Indicator<Num> donchianCompressionIndicator;
    private final Indicator<Num> compositeIndicator;
    private final int barCount;
    private final int percentileBarCount;

    /**
     * Creates a compression score using the same bar count for the underlying
     * windows and the percentile lookback.
     *
     * @param series   the bar series
     * @param barCount the metric and percentile window size
     * @since 0.22.7
     */
    public CompressionIndicator(BarSeries series, int barCount) {
        this(validatedConfig(series, barCount, barCount));
    }

    /**
     * Creates a compression score from price-derived volatility inputs.
     *
     * @param series             the bar series
     * @param barCount           the window used for ATR/Bollinger/Donchian metrics
     * @param percentileBarCount the rolling lookback used for percentile ranking
     * @since 0.22.7
     */
    public CompressionIndicator(BarSeries series, int barCount, int percentileBarCount) {
        this(validatedConfig(series, barCount, percentileBarCount));
    }

    /**
     * Creates a compression score from explicit component indicators.
     *
     * @param atrPercentIndicator     ATR expressed as a percentage of price
     * @param bollingerWidthIndicator Bollinger bandwidth indicator
     * @param donchianWidthIndicator  Donchian width indicator
     * @param barCount                the window used by the underlying metrics
     * @param percentileBarCount      the rolling lookback used for percentile
     *                                ranking
     * @since 0.22.7
     */
    public CompressionIndicator(Indicator<Num> atrPercentIndicator, Indicator<Num> bollingerWidthIndicator,
            Indicator<Num> donchianWidthIndicator, int barCount, int percentileBarCount) {
        this(validatedConfig(atrPercentIndicator, bollingerWidthIndicator, donchianWidthIndicator, barCount,
                percentileBarCount));
    }

    private CompressionIndicator(Config config) {
        super(config.series());
        this.barCount = config.barCount();
        this.percentileBarCount = config.percentileBarCount();
        this.atrCompressionIndicator = config.atrCompressionIndicator();
        this.bollingerCompressionIndicator = config.bollingerCompressionIndicator();
        this.donchianCompressionIndicator = config.donchianCompressionIndicator();
        this.compositeIndicator = config.compositeIndicator();
    }

    private static Config validatedConfig(BarSeries series, int barCount, int percentileBarCount) {
        return validatedConfig(buildAtrPercent(series, barCount), buildBollingerWidth(series, barCount),
                buildDonchianWidth(series, barCount), barCount, percentileBarCount);
    }

    private static Config validatedConfig(Indicator<Num> atrPercentIndicator, Indicator<Num> bollingerWidthIndicator,
            Indicator<Num> donchianWidthIndicator, int barCount, int percentileBarCount) {
        BarSeries series = IndicatorUtils.requireSameSeries(atrPercentIndicator, bollingerWidthIndicator,
                donchianWidthIndicator);
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be greater than zero");
        }
        if (percentileBarCount < 1) {
            throw new IllegalArgumentException("percentileBarCount must be greater than zero");
        }
        Indicator<Num> atrCompressionIndicator = invertedPercentRank(atrPercentIndicator, percentileBarCount);
        Indicator<Num> bollingerCompressionIndicator = invertedPercentRank(bollingerWidthIndicator, percentileBarCount);
        Indicator<Num> donchianCompressionIndicator = invertedPercentRank(donchianWidthIndicator, percentileBarCount);
        Indicator<Num> compositeIndicator = NumericIndicator
                .of(new SumIndicator(atrCompressionIndicator, bollingerCompressionIndicator,
                        donchianCompressionIndicator))
                .dividedBy(3);
        return new Config(series, atrCompressionIndicator, bollingerCompressionIndicator, donchianCompressionIndicator,
                compositeIndicator, barCount, percentileBarCount);
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
     * @return the ATR-based compression sub-score
     * @since 0.22.7
     */
    public Indicator<Num> getAtrCompressionIndicator() {
        return atrCompressionIndicator;
    }

    /**
     * @return the Bollinger-bandwidth compression sub-score
     * @since 0.22.7
     */
    public Indicator<Num> getBollingerCompressionIndicator() {
        return bollingerCompressionIndicator;
    }

    /**
     * @return the Donchian-width compression sub-score
     * @since 0.22.7
     */
    public Indicator<Num> getDonchianCompressionIndicator() {
        return donchianCompressionIndicator;
    }

    /**
     * @return the metric bar count used to build the component inputs
     * @since 0.22.7
     */
    public int getBarCount() {
        return barCount;
    }

    /**
     * @return the percentile lookback used to normalize the component inputs
     * @since 0.22.7
     */
    public int getPercentileBarCount() {
        return percentileBarCount;
    }

    private static Indicator<Num> buildAtrPercent(BarSeries series, int barCount) {
        Indicator<Num> close = new ClosePriceIndicator(series);
        return NumericIndicator.of(new ATRIndicator(series, barCount)).dividedBy(close).multipliedBy(100);
    }

    private static Indicator<Num> buildBollingerWidth(BarSeries series, int barCount) {
        return new BollingerBandFacade(series, barCount, DEFAULT_BOLLINGER_K).bandwidth();
    }

    private static Indicator<Num> buildDonchianWidth(BarSeries series, int barCount) {
        DonchianChannelFacade donchian = new DonchianChannelFacade(series, barCount);
        return donchian.upper().minus(donchian.lower()).dividedBy(donchian.middle()).multipliedBy(100);
    }

    private static Indicator<Num> invertedPercentRank(Indicator<Num> indicator, int percentileBarCount) {
        return NumericIndicator.of(new PercentRankIndicator(indicator, percentileBarCount)).multipliedBy(-1).plus(100);
    }

    private record Config(BarSeries series, Indicator<Num> atrCompressionIndicator,
            Indicator<Num> bollingerCompressionIndicator, Indicator<Num> donchianCompressionIndicator,
            Indicator<Num> compositeIndicator, int barCount, int percentileBarCount) {
    }
}
