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

import java.util.function.Function;
import java.util.function.Predicate;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Objects of this class act to defer the evaluation of a predicate; a
 * true/false test on an object. We test Num objects when the operand indicator
 * gets them. This class is a recent addition; its a bit rough.
 */
public class PredicateOperation implements Indicator<Boolean> {

    private Predicate<Num> predicate;
    private final Indicator<Num> operand;

    public PredicateOperation(Predicate<Num> predicate, Indicator<Num> operand) {
        this.predicate = predicate;
        this.operand = operand;
    }

    @Override
    public Boolean getValue(int index) {
        Num n = operand.getValue(index);
        return predicate.test(n);
    }

    @Override
    public BarSeries getBarSeries() {
        return operand.getBarSeries();
    }

    // make this a default method in the Indicator interface...
    @Override
    public Num numOf(Number number) {
        return operand.numOf(number);
    }

}
