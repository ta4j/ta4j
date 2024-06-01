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
package org.ta4j.core.indicators.aroon;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.numeric.NumericIndicator;

/**
 * A facade to create the two Aroon indicators. The Aroon Oscillator can also be
 * created on demand.
 *
 * <p>
 * This class creates lightweight "fluent" numeric indicators. These objects are
 * not cached, although they may be wrapped around cached objects.
 */
public class AroonFacade {

    private final AroonUpIndicator up;
    private final AroonDownIndicator down;

    /**
     * Create the Aroon facade.
     *
     * @param series   the bar series
     * @param barCount the number of periods used for the indicators
     */
    public AroonFacade(final BarSeries series, final int barCount) {
        this.up = new AroonUpIndicator(series, barCount);
        this.down = new AroonDownIndicator(series, barCount);
    }

    /**
     * A fluent AroonUp indicator.
     *
     * @return a NumericIndicator wrapped around a cached AroonUpIndicator
     */
    public AroonUpIndicator up() {
        return this.up;
    }

    /**
     * A fluent AroonDown indicator.
     *
     * @return a NumericIndicator wrapped around a cached AroonDownIndicator
     */
    public AroonDownIndicator down() {
        return this.down;
    }

    /**
     * A lightweight fluent AroonOscillator.
     *
     * @return an uncached object that calculates the difference between AoonUp and
     *         AroonDown
     */
    public NumericIndicator oscillator() {
        return this.up.minus(this.down);
    }

}
