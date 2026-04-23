/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.util.Objects;

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
        this(series, barCount, barCount);
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
        this(buildAtrPercent(series, barCount), buildBollingerWidth(series, barCount),
                buildDonchianWidth(series, barCount), barCount, percentileBarCount);
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
        super(requireSameSeries(atrPercentIndicator, bollingerWidthIndicator, donchianWidthIndicator));
        if (barCount < 1) {
            throw new IllegalArgumentException("barCount must be greater than zero");
        }
        if (percentileBarCount < 1) {
            throw new IllegalArgumentException("percentileBarCount must be greater than zero");
        }
        this.barCount = barCount;
        this.percentileBarCount = percentileBarCount;
        this.atrCompressionIndicator = invertedPercentRank(atrPercentIndicator, percentileBarCount);
        this.bollingerCompressionIndicator = invertedPercentRank(bollingerWidthIndicator, percentileBarCount);
        this.donchianCompressionIndicator = invertedPercentRank(donchianWidthIndicator, percentileBarCount);
        this.compositeIndicator = NumericIndicator.of(new SumIndicator(this.atrCompressionIndicator,
                this.bollingerCompressionIndicator, this.donchianCompressionIndicator)).dividedBy(3);
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
