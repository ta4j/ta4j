/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;

/**
 * Bill Williams Market Facilitation Index (BW MFI).
 * <p>
 * The Bill Williams Market Facilitation Index measures price movement per unit
 * of volume:
 *
 * <pre>
 * BW MFI = (high - low) / volume
 * </pre>
 *
 * This is different from the Money Flow Index ("MFI") oscillator.
 * <p>
 * Undefined inputs (NaN) and zero volume produce {@code NaN}.
 * <p>
 * Interaction note: Bill Williams often interprets BW MFI together with bar
 * volume change classes ("green", "fade", "fake", "squat") while analyzing
 * alligator/fractal setups.
 *
 * @see org.ta4j.core.indicators.volume.MoneyFlowIndexIndicator
 * @see <a href=
 *      "https://www.investopedia.com/terms/m/marketfacilitationindex.asp">Investopedia:
 *      Market Facilitation Index</a>
 * @since 0.22.3
 */
public class MarketFacilitationIndexIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> highPrice;
    private final Indicator<Num> lowPrice;
    private final Indicator<Num> volume;

    /**
     * Constructor.
     *
     * @param highPrice high-price indicator
     * @param lowPrice  low-price indicator
     * @param volume    volume indicator
     * @throws IllegalArgumentException when any indicator is null or when
     *                                  indicators do not share the same series
     * @since 0.22.3
     */
    public MarketFacilitationIndexIndicator(Indicator<Num> highPrice, Indicator<Num> lowPrice, Indicator<Num> volume) {
        super(IndicatorUtils.requireIndicator(highPrice, "highPrice indicator"));
        IndicatorUtils.requireIndicator(lowPrice, "lowPrice indicator");
        IndicatorUtils.requireIndicator(volume, "volume indicator");
        IndicatorUtils.requireSameSeries(highPrice, lowPrice, volume);
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.volume = volume;
    }

    /**
     * Constructor using bar high/low/volume values.
     *
     * @param series the series
     * @since 0.22.3
     */
    public MarketFacilitationIndexIndicator(BarSeries series) {
        this(new HighPriceIndicator(series), new LowPriceIndicator(series), new VolumeIndicator(series));
    }

    @Override
    protected Num calculate(int index) {
        if (index < getCountOfUnstableBars()) {
            return NaN;
        }
        final Num high = highPrice.getValue(index);
        final Num low = lowPrice.getValue(index);
        final Num currentVolume = volume.getValue(index);
        if (IndicatorUtils.isInvalid(high) || IndicatorUtils.isInvalid(low) || IndicatorUtils.isInvalid(currentVolume)
                || currentVolume.isZero()) {
            return NaN;
        }
        return high.minus(low).dividedBy(currentVolume);
    }

    @Override
    public int getCountOfUnstableBars() {
        return Math.max(Math.max(highPrice.getCountOfUnstableBars(), lowPrice.getCountOfUnstableBars()),
                volume.getCountOfUnstableBars());
    }

}
