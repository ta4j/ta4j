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
package ta4jexamples.indicators.numeric;

import org.ta4j.core.BarSeries;

/**
 * Objects of this class act to defer evaluation of the Java ternary operator.
 * 
 * A boolean indicator is used as a test to determine which of 2 numbers is returned.
 * 
 * This is a recent addition.  It seems to work for the lightweight ADX calculation in the DMS facade.
 */
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

public class TernaryOperation implements Indicator<Num> {

    private final Indicator<Boolean> test;
    private final Indicator<Num> whenTrue;
    private final Indicator<Num> whenFalse;

    public TernaryOperation(Indicator<Boolean> test, Indicator<Num> whenTrue, Indicator<Num> whenFalse) {
        this.test = test;
        this.whenTrue = whenTrue;
        this.whenFalse = whenFalse;
    }

    @Override
    public Num getValue(int index) {
        Boolean b = test.getValue(index);
        Num trueValue = whenTrue.getValue(index);
        Num falseValue = whenFalse.getValue(index);
        return b ? trueValue : falseValue;
    }

    @Override
    public BarSeries getBarSeries() {
        return test.getBarSeries();
    }

    @Override
    public Num numOf(Number number) {
        return test.numOf(number);
    }

}
