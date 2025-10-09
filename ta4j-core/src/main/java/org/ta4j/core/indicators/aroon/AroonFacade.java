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

    private final NumericIndicator up;
    private final NumericIndicator down;

    /**
     * Create the Aroon facade.
     *
     * @param series   the bar series
     * @param barCount the number of periods used for the indicators
     */
    public AroonFacade(BarSeries series, int barCount) {
        this.up = NumericIndicator.of(new AroonUpIndicator(series, barCount));
        this.down = NumericIndicator.of(new AroonDownIndicator(series, barCount));
    }

    /**
     * A fluent AroonUp indicator.
     *
     * @return a NumericIndicator wrapped around a cached AroonUpIndicator
     */
    public NumericIndicator up() {
        return up;
    }

    /**
     * A fluent AroonDown indicator.
     *
     * @return a NumericIndicator wrapped around a cached AroonDownIndicator
     */
    public NumericIndicator down() {
        return down;
    }

    /**
     * A lightweight fluent AroonOscillator.
     *
     * @return an uncached object that calculates the difference between AoonUp and
     *         AroonDown
     */
    public NumericIndicator oscillator() {
        return up.minus(down);
    }

}
