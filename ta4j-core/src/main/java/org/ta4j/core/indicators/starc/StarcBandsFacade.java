/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.indicators.starc;

import org.ta4j.core.BarSeries;

/**
 * A facade for creating the three STARC Bands indicators. A simple moving
 * average of the close price is used as the middle channel.
 *
 * <p>
 * The STARC Bands (or Stoller Average Range Channels) are a type of technical
 * indicator that consist of three bands. The middle band is a simple moving
 * average, while the upper and lower bands are calculated based on the value of
 * the Average True Range (ATR).
 *
 * @see <a href="https://www.stockmaniacs.net/starc-bands-indicator/">STARC
 *      Bands Indicator</a>
 */
public class StarcBandsFacade {

    private final StarcBandsMiddleIndicator middle;
    private final StarcBandsUpperIndicator upper;
    private final StarcBandsLowerIndicator lower;

    /**
     * Constructor.
     *
     * @param series        the bar series
     * @param smaBarCount   the bar count for the SMA calculation
     * @param atrBarCount   the bar count for the ATR calculation
     * @param atrMultiplier the multiplier for the ATR value used to calculate the
     *                      upper and lower bands
     */
    public StarcBandsFacade(BarSeries series, int smaBarCount, int atrBarCount, Number atrMultiplier) {
        this.middle = new StarcBandsMiddleIndicator(series, smaBarCount);
        this.upper = new StarcBandsUpperIndicator(this.middle, atrBarCount, atrMultiplier);
        this.lower = new StarcBandsLowerIndicator(this.middle, atrBarCount, atrMultiplier);
    }

    /**
     * @return the middle STARC Band
     */
    public StarcBandsMiddleIndicator middle() {
        return middle;
    }

    /**
     * @return the upper STARC Band
     */
    public StarcBandsUpperIndicator upper() {
        return upper;
    }

    /**
     * @return the lower STARC Band
     */
    public StarcBandsLowerIndicator lower() {
        return lower;
    }
}
