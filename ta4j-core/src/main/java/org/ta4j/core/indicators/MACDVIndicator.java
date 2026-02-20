/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.VWMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Moving average convergence divergence volume (MACD-V) indicator.
 *
 * <p>
 * This implementation is a volume/ATR-weighted MACD variant. It computes short
 * and long variable-weighted EMAs (VWEMAs), where each weight is
 * {@code volume / ATR(period)}, and returns their difference.
 *
 * <p>
 * Unlike volatility-normalized MACD-V formulations that divide the EMA spread
 * by ATR, this indicator uses ATR only inside the weighting term.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/macd-v">
 *      https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/macd-v
 *      </a>
 * @since 0.19
 */
public class MACDVIndicator extends CachedIndicator<Num> {

    private static final int DEFAULT_SHORT_BAR_COUNT = 12;
    private static final int DEFAULT_LONG_BAR_COUNT = 26;
    private static final int DEFAULT_SIGNAL_BAR_COUNT = 9;

    private final Indicator<Num> priceIndicator;
    private final int shortBarCount;
    private final int longBarCount;
    private final int defaultSignalBarCount;

    private transient VWMAIndicator shortTermVwema;
    private transient VWMAIndicator longTermVwema;

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code shortBarCount} = 12
     * <li>{@code longBarCount} = 26
     * <li>{@code signalBarCount} = 9
     * </ul>
     *
     * @param series the bar series {@link BarSeries}
     * @since 0.19
     */
    public MACDVIndicator(BarSeries series) {
        this(new ClosePriceIndicator(series));
    }

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code shortBarCount} = 12
     * <li>{@code longBarCount} = 26
     * <li>{@code signalBarCount} = 9
     * </ul>
     *
     * @param priceIndicator the price-based {@link Indicator}
     * @since 0.19
     */
    public MACDVIndicator(Indicator<Num> priceIndicator) {
        this(priceIndicator, DEFAULT_SHORT_BAR_COUNT, DEFAULT_LONG_BAR_COUNT, DEFAULT_SIGNAL_BAR_COUNT);
    }

    /**
     * Constructor.
     *
     * @param series        the bar series {@link BarSeries}
     * @param shortBarCount the short time frame (normally 12)
     * @param longBarCount  the long time frame (normally 26)
     * @since 0.19
     */
    public MACDVIndicator(BarSeries series, int shortBarCount, int longBarCount) {
        this(new ClosePriceIndicator(series), shortBarCount, longBarCount, DEFAULT_SIGNAL_BAR_COUNT);
    }

    /**
     * Constructor.
     *
     * @param series         the bar series {@link BarSeries}
     * @param shortBarCount  the short time frame (normally 12)
     * @param longBarCount   the long time frame (normally 26)
     * @param signalBarCount the default signal time frame (normally 9)
     * @since 0.22.2
     */
    public MACDVIndicator(BarSeries series, int shortBarCount, int longBarCount, int signalBarCount) {
        this(new ClosePriceIndicator(series), shortBarCount, longBarCount, signalBarCount);
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price-based {@link Indicator}
     * @param shortBarCount  the short time frame (normally 12)
     * @param longBarCount   the long time frame (normally 26)
     * @since 0.19
     */
    public MACDVIndicator(Indicator<Num> priceIndicator, int shortBarCount, int longBarCount) {
        this(priceIndicator, shortBarCount, longBarCount, DEFAULT_SIGNAL_BAR_COUNT);
    }

    /**
     * Constructor.
     *
     * @param priceIndicator the price-based {@link Indicator}
     * @param shortBarCount  the short time frame (normally 12)
     * @param longBarCount   the long time frame (normally 26)
     * @param signalBarCount the default signal time frame (normally 9)
     * @since 0.22.2
     */
    public MACDVIndicator(Indicator<Num> priceIndicator, int shortBarCount, int longBarCount, int signalBarCount) {
        super(priceIndicator);
        validateBarCounts(shortBarCount, longBarCount);
        validateSignalBarCount(signalBarCount);
        this.priceIndicator = priceIndicator;
        this.shortBarCount = shortBarCount;
        this.longBarCount = longBarCount;
        this.defaultSignalBarCount = signalBarCount;
        ensureSubIndicatorsInitialized();
    }

    private static void validateBarCounts(int shortBarCount, int longBarCount) {
        if (shortBarCount < 1) {
            throw new IllegalArgumentException("Short term period count must be greater than 0");
        }
        if (longBarCount < 1) {
            throw new IllegalArgumentException("Long term period count must be greater than 0");
        }
        if (shortBarCount > longBarCount) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count");
        }
    }

    private static void validateSignalBarCount(int signalBarCount) {
        if (signalBarCount < 1) {
            throw new IllegalArgumentException("Signal bar count must be greater than 0");
        }
    }

    /**
     * @return the short-term volume-weighted EMA indicator.
     * @since 0.19
     */
    public Indicator<Num> getShortTermVolumeWeightedEma() {
        return getShortTermVwema();
    }

    /**
     * @return the long-term volume-weighted EMA indicator.
     * @since 0.19
     */
    public Indicator<Num> getLongTermVolumeWeightedEma() {
        return getLongTermVwema();
    }

    /**
     * @return signal line for this MACD-V indicator using the configured default
     *         signal bar count
     * @since 0.22.2
     */
    public EMAIndicator getSignalLine() {
        return getSignalLine(defaultSignalBarCount);
    }

    /**
     * @param barCount of signal line
     * @return signal line for this MACD-V indicator
     * @since 0.19
     */
    public EMAIndicator getSignalLine(int barCount) {
        validateSignalBarCount(barCount);
        return new EMAIndicator(this, barCount);
    }

    /**
     * @return histogram of this MACD-V indicator using the configured default
     *         signal bar count
     * @since 0.22.2
     */
    public NumericIndicator getHistogram() {
        return getHistogram(defaultSignalBarCount);
    }

    /**
     * @param barCount of signal line
     * @return histogram of this MACD-V indicator
     * @since 0.19
     */
    public NumericIndicator getHistogram(int barCount) {
        validateSignalBarCount(barCount);
        return NumericIndicator.of(this).minus(getSignalLine(barCount));
    }

    /**
     * @return this MACD line
     * @since 0.22.2
     */
    public MACDVIndicator getMacd() {
        return this;
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN.NaN;
        }
        Num shortValue = getShortTermVwema().getValue(index);
        Num longValue = getLongTermVwema().getValue(index);
        if (Num.isNaNOrNull(shortValue) || Num.isNaNOrNull(longValue)) {
            return NaN.NaN;
        }
        Num macdValue = shortValue.minus(longValue);
        if (Num.isNaNOrNull(macdValue)) {
            return NaN.NaN;
        }
        return macdValue;
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(getShortTermVwema().getCountOfUnstableBars(), getLongTermVwema().getCountOfUnstableBars());
    }

    private VWMAIndicator getShortTermVwema() {
        ensureSubIndicatorsInitialized();
        return shortTermVwema;
    }

    private VWMAIndicator getLongTermVwema() {
        ensureSubIndicatorsInitialized();
        return longTermVwema;
    }

    private void ensureSubIndicatorsInitialized() {
        if (shortTermVwema != null && longTermVwema != null) {
            return;
        }
        Indicator<Num> shortVolumeWeights = buildVolumeWeights(shortBarCount);
        shortTermVwema = new VWMAIndicator(priceIndicator, shortVolumeWeights, shortBarCount, EMAIndicator::new);

        if (shortBarCount == longBarCount) {
            longTermVwema = new VWMAIndicator(priceIndicator, shortVolumeWeights, longBarCount, EMAIndicator::new);
            return;
        }
        Indicator<Num> longVolumeWeights = buildVolumeWeights(longBarCount);
        longTermVwema = new VWMAIndicator(priceIndicator, longVolumeWeights, longBarCount, EMAIndicator::new);
    }

    private Indicator<Num> buildVolumeWeights(int atrBarCount) {
        BarSeries series = priceIndicator.getBarSeries();
        VolumeIndicator volumeIndicator = new VolumeIndicator(series);
        ATRIndicator atrIndicator = new ATRIndicator(series, atrBarCount);
        return NumericIndicator.of(volumeIndicator).dividedBy(atrIndicator);
    }

}
