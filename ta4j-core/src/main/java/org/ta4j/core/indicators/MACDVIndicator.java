/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
 * Moving Average Convergence Divergence Volume (MACD-V) indicator.
 *
 * <p>
 * MACD-V applies the standard MACD calculation to volume-weighted exponential
 * moving averages. For each look-back period, the close price is weighted by
 * volume through an EMA of the price-volume product divided by the EMA of
 * volume. The MACD-V line is the difference between the short and long
 * volume-weighted EMAs, the signal line is the EMA of that difference, and the
 * histogram is their divergence.
 *
 * @see <a href=
 *      "https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/macd-v">
 *      https://chartschool.stockcharts.com/table-of-contents/technical-indicators-and-overlays/technical-indicators/macd-v
 *      </a>
 * @since 0.19
 */
public class MACDVIndicator extends CachedIndicator<Num> {

    private final VWMAIndicator shortTermVwema;
    private final VWMAIndicator longTermVwema;

    /**
     * Constructor with:
     *
     * <ul>
     * <li>{@code shortBarCount} = 12</li>
     * <li>{@code longBarCount} = 26</li>
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
     * <li>{@code shortBarCount} = 12</li>
     * <li>{@code longBarCount} = 26</li>
     * </ul>
     *
     * @param priceIndicator the price-based {@link Indicator}
     * @since 0.19
     */
    public MACDVIndicator(Indicator<Num> priceIndicator) {
        this(priceIndicator, 12, 26);
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
        this(new ClosePriceIndicator(series), shortBarCount, longBarCount);
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
        super(priceIndicator);
        if (shortBarCount > longBarCount) {
            throw new IllegalArgumentException("Long term period count must be greater than short term period count");
        }

        var volumeIndicator = new VolumeIndicator(priceIndicator.getBarSeries());
        this.shortTermVwema = new VWMAIndicator(priceIndicator, volumeIndicator, shortBarCount, EMAIndicator::new);
        this.longTermVwema = new VWMAIndicator(priceIndicator, volumeIndicator, longBarCount, EMAIndicator::new);
    }

    /**
     * @return the short-term volume-weighted EMA indicator.
     * @since 0.19
     */
    public Indicator<Num> getShortTermVolumeWeightedEma() {
        return shortTermVwema;
    }

    /**
     * @return the long-term volume-weighted EMA indicator.
     * @since 0.19
     */
    public Indicator<Num> getLongTermVolumeWeightedEma() {
        return longTermVwema;
    }

    /**
     * @param barCount of signal line
     * @return signal line for this MACD-V indicator
     * @since 0.19
     */
    public EMAIndicator getSignalLine(int barCount) {
        return new EMAIndicator(this, barCount);
    }

    /**
     * @param barCount of signal line
     * @return histogram of this MACD-V indicator
     * @since 0.19
     */
    public NumericIndicator getHistogram(int barCount) {
        return NumericIndicator.of(this).minus(getSignalLine(barCount));
    }

    @Override
    protected Num calculate(int index) {
        Num shortValue = shortTermVwema.getValue(index);
        Num longValue = longTermVwema.getValue(index);
        if (shortValue.isNaN() || longValue.isNaN()) {
            return NaN.NaN;
        }
        return shortValue.minus(longValue);
    }

    @Override
    public int getCountOfUnstableBars() {
        return 0;
    }

}
