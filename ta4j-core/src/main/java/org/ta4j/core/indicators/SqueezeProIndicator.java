/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
import org.ta4j.core.indicators.bollinger.BollingerBandFacade;
import org.ta4j.core.indicators.keltner.KeltnerChannelLowerIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelMiddleIndicator;
import org.ta4j.core.indicators.keltner.KeltnerChannelUpperIndicator;
import org.ta4j.core.num.Num;

/**
 * Mobius Squeeze Pro Indicator.
 *
 * This indicator combines Bollinger Bands and Keltner Channels to identify
 * potential market squeezes. A squeeze occurs when volatility decreases and the
 * Bollinger Bands move inside the Keltner Channels.
 *
 * The indicator returns true when a squeeze is detected, which can signal a
 * potential breakout or significant price move.
 */
public class SqueezeProIndicator extends CachedIndicator<Boolean> {

    private final Indicator<Num> keltnerChannelUpperBandHigh;
    private final Indicator<Num> keltnerChannelLowerBandHigh;
    private final Indicator<Num> keltnerChannelUpperBandLow;
    private final Indicator<Num> keltnerChannelLowerBandLow;
    private final Indicator<Num> keltnerChannelUpperBandMid;
    private final Indicator<Num> keltnerChannelLowerBandMid;

    private final Indicator<Num> bollingerBandUpperLine;
    private final Indicator<Num> bollingerBandLowerLine;

    private final int barCount;

    /**
     * Constructor with default parameters.
     *
     * @param series   the bar series
     * @param barCount the time frame
     */
    public SqueezeProIndicator(BarSeries series, int barCount) {
        this(series, barCount, 2, 1, 1.5, 2);
    }

    /**
     * Constructor with custom parameters.
     *
     * @param series                 the bar series
     * @param barCount               the time frame
     * @param bollingerBandK         the Bollinger Band multiplier
     * @param keltnerShiftFactorHigh the Keltner Channel high band multiplier
     * @param keltnerShiftFactorMid  the Keltner Channel middle band multiplier
     * @param keltnerShiftFactorLow  the Keltner Channel low band multiplier
     */
    public SqueezeProIndicator(BarSeries series, int barCount, double bollingerBandK, double keltnerShiftFactorHigh,
            double keltnerShiftFactorMid, double keltnerShiftFactorLow) {
        super(series);

        BollingerBandFacade bollingerBand = new BollingerBandFacade(series, barCount, bollingerBandK);
        this.bollingerBandUpperLine = bollingerBand.upper();
        this.bollingerBandLowerLine = bollingerBand.lower();

        KeltnerChannelMiddleIndicator keltnerChannelMidLine = new KeltnerChannelMiddleIndicator(series, barCount);
        ATRIndicator averageTrueRange = new ATRIndicator(series, barCount);

        this.keltnerChannelUpperBandLow = new KeltnerChannelUpperIndicator(keltnerChannelMidLine, averageTrueRange,
                keltnerShiftFactorLow);
        this.keltnerChannelLowerBandLow = new KeltnerChannelLowerIndicator(keltnerChannelMidLine, averageTrueRange,
                keltnerShiftFactorLow);
        this.keltnerChannelUpperBandMid = new KeltnerChannelUpperIndicator(keltnerChannelMidLine, averageTrueRange,
                keltnerShiftFactorMid);
        this.keltnerChannelLowerBandMid = new KeltnerChannelLowerIndicator(keltnerChannelMidLine, averageTrueRange,
                keltnerShiftFactorMid);
        this.keltnerChannelUpperBandHigh = new KeltnerChannelUpperIndicator(keltnerChannelMidLine, averageTrueRange,
                keltnerShiftFactorHigh);
        this.keltnerChannelLowerBandHigh = new KeltnerChannelLowerIndicator(keltnerChannelMidLine, averageTrueRange,
                keltnerShiftFactorHigh);

        this.barCount = barCount;
    }

    /**
     * Calculates the Mobius Squeeze Pro indicator value for a specific index.
     *
     * @param index the index
     * @return true if a squeeze is detected, false otherwise
     */
    @Override
    protected Boolean calculate(int index) {
        if (index < getUnstableBars()) {
            return false;
        }

        Num bbLower = bollingerBandLowerLine.getValue(index);
        Num bbUpper = bollingerBandUpperLine.getValue(index);

        return isSqueezeCondition(bbLower, bbUpper, keltnerChannelLowerBandLow, keltnerChannelUpperBandLow, index)
                || isSqueezeCondition(bbLower, bbUpper, keltnerChannelLowerBandMid, keltnerChannelUpperBandMid, index)
                || isSqueezeCondition(bbLower, bbUpper, keltnerChannelLowerBandHigh, keltnerChannelUpperBandHigh,
                        index);
    }

    /**
     * Checks if the squeeze condition is met for a given set of indicators.
     *
     * @param bbLower Bollinger Band lower value
     * @param bbUpper Bollinger Band upper value
     * @param kcLower Keltner Channel lower indicator
     * @param kcUpper Keltner Channel upper indicator
     * @param index   the index to check
     * @return true if the squeeze condition is met, false otherwise
     */
    private boolean isSqueezeCondition(Num bbLower, Num bbUpper, Indicator<Num> kcLower, Indicator<Num> kcUpper,
            int index) {
        return bbLower.isGreaterThan(kcLower.getValue(index)) && bbUpper.isLessThan(kcUpper.getValue(index));
    }

    @Override
    public int getUnstableBars() {
        return this.barCount;
    }
}