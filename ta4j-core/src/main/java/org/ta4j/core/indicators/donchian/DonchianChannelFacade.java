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
package org.ta4j.core.indicators.donchian;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.numeric.NumericIndicator;

/**
 * A facade to create the 3 donchain Channel indicators.
 *
 * <p>
 * This class creates lightweight "fluent" numeric indicators. These objects are
 * not cached, although they may be wrapped around cached objects. Overall,
 * there is less caching and probably better performance.
 *
 * @since 0.22.2
 */
public class DonchianChannelFacade {
    private final NumericIndicator lower;
    private final NumericIndicator upper;
    private final NumericIndicator middle;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the time frame
     *
     * @since 0.22.2
     */
    public DonchianChannelFacade(BarSeries series, int barCount) {
        DonchianChannelLowerIndicator donchianChannelLowerIndicator = new DonchianChannelLowerIndicator(series,
                barCount);
        DonchianChannelUpperIndicator donchianChannelUpperIndicator = new DonchianChannelUpperIndicator(series,
                barCount);
        this.lower = NumericIndicator.of(donchianChannelLowerIndicator);
        this.upper = NumericIndicator.of(donchianChannelUpperIndicator);
        this.middle = NumericIndicator.of(new DonchianChannelMiddleIndicator(series, barCount,
                donchianChannelLowerIndicator, donchianChannelUpperIndicator));
    }

    /**
     * A fluent DonchianChannelLower indicator.
     *
     * @return a NumericIndicator wrapped around a cached
     *         DonchianChannelLowerIndicator
     *
     * @since 0.22.2
     */
    public NumericIndicator lower() {
        return NumericIndicator.of(lower);
    }

    /**
     * A fluent DonchianChannelUpper indicator.
     *
     * @return a NumericIndicator wrapped around a cached
     *         DonchianChannelUpperIndicator
     *
     * @since 0.22.2
     */
    public NumericIndicator upper() {
        return NumericIndicator.of(upper);
    }

    /**
     * A fluent DonchianChannelMiddle indicator.
     *
     * @return a NumericIndicator wrapped around a cached
     *         DonchianChannelMiddleIndicator
     *
     * @since 0.22.2
     */
    public NumericIndicator middle() {
        return NumericIndicator.of(middle);
    }

}
