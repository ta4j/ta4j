/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package ta4jexamples.indicators.numeric.facades;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AroonDownIndicator;
import org.ta4j.core.indicators.AroonUpIndicator;

import ta4jexamples.indicators.numeric.NumericIndicator;

public class Aroon {

    private final NumericIndicator up;
    private final NumericIndicator down;

    public Aroon(BarSeries bs, int n) {
        this.up = NumericIndicator.of(new AroonUpIndicator(bs, n));
        this.down = NumericIndicator.of(new AroonDownIndicator(bs, n));
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
     * @return an uncached object that calculates the difference between AoonUp and AroonDown
     */
    public NumericIndicator oscillator() {
        return up.minus(down);
    }

}
