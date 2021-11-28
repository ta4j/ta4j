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
import org.ta4j.core.indicators.ATRIndicator;
import org.ta4j.core.indicators.MMAIndicator;
import org.ta4j.core.indicators.adx.MinusDMIndicator;
import org.ta4j.core.indicators.adx.PlusDMIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;

import ta4jexamples.indicators.numeric.NumericIndicator;
import ta4jexamples.indicators.numeric.TernaryOperation;

/**
 * A facade for the 3 ADX indicators.
 * 
 * This class shows an example of how the ternary operation can be used Here it
 * guards against division by zero
 *
 */
public class DirectionalMovementSystem {

    private final NumericIndicator adx;
    private final NumericIndicator plusDI;
    private final NumericIndicator minusDI;

    public DirectionalMovementSystem(BarSeries bs, int diCount, int adxCount) {

        NumericIndicator atr = NumericIndicator.of(new ATRIndicator(bs, diCount));
        NumericIndicator mmaPlusDM = NumericIndicator.of(new MMAIndicator(new PlusDMIndicator(bs), diCount));
        NumericIndicator mmaMinusDM = NumericIndicator.of(new MMAIndicator(new MinusDMIndicator(bs), diCount));
        this.plusDI = mmaPlusDM.dividedBy(atr).multipliedBy(100);
        this.minusDI = mmaMinusDM.dividedBy(atr).multipliedBy(100);
        NumericIndicator dividend = plusDI.minus(minusDI).abs();
        NumericIndicator divisor = plusDI.plus(minusDI);
        NumericIndicator dx = NumericIndicator.of(new TernaryOperation(divisor.isZero(),
                new ConstantIndicator<>(bs, bs.numOf(0)), dividend.dividedBy(divisor).multipliedBy(100)));
        this.adx = NumericIndicator.of(new MMAIndicator(dx, adxCount));

    }

    public NumericIndicator adx() {
        return adx;
    }

    public NumericIndicator plusDI() {
        return plusDI;
    }

    public NumericIndicator minusDI() {
        return minusDI;
    }

}
