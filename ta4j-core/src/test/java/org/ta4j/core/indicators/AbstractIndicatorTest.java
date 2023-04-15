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
package org.ta4j.core.indicators;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.Indicator;
import org.ta4j.core.IndicatorFactory;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

/**
 * Abstract test class to extend BarSeries, Indicator an other test cases. The
 * extending class will be called twice. First time with
 * {@link DecimalNum#valueOf}, second time with {@link DoubleNum#valueOf} as
 * <code>Function<Number, Num></></code> parameter. This should ensure that the
 * defined test case is valid for both data types.
 *
 * @param <D> Data source of test object, needed for Excel-Sheet validation
 *            (could be <code>Indicator<Num></code> or <code>BarSeries</code>,
 *            ...)
 * @param <I> The generic class of the test indicator (could be
 *            <code>Num</code>, <code>Boolean</code>, ...)
 */
@RunWith(Parameterized.class)
public abstract class AbstractIndicatorTest<D, I> {

    public final Function<Number, Num> numFunction;

    @Parameterized.Parameters(name = "Test Case: {index} (0=DoubleNum, 1=DecimalNum)")
    public static List<Function<Number, Num>> function() {
        return Arrays.asList(DoubleNum::valueOf, DecimalNum::valueOf);
    }

    private final IndicatorFactory<D, I> factory;

    /**
     * Constructor.
     * 
     * @param factory     IndicatorFactory for building an Indicator given data and
     *                    parameters.
     * @param numFunction the function to convert a Number into a Num implementation
     *                    (automatically inserted by Junit)
     */
    public AbstractIndicatorTest(IndicatorFactory<D, I> factory, Function<Number, Num> numFunction) {
        this.numFunction = numFunction;
        this.factory = factory;
    }

    /**
     * Constructor
     *
     * @param numFunction the function to convert a Number into a Num implementation
     *                    (automatically inserted by Junit)
     */
    public AbstractIndicatorTest(Function<Number, Num> numFunction) {
        this.numFunction = numFunction;
        this.factory = null;
    }

    /**
     * Generates an Indicator from data and parameters.
     * 
     * @param data   indicator data
     * @param params indicator parameters
     * @return Indicator<I> from data given parameters
     */
    public Indicator<I> getIndicator(D data, Object... params) {
        assert factory != null;
        return factory.getIndicator(data, params);
    }

    protected Num numOf(Number n) {
        return numFunction.apply(n);
    }

    public Num numOf(String string, int precision) {
        MathContext mathContext = new MathContext(precision, RoundingMode.HALF_UP);
        return this.numOf(new BigDecimal(string, mathContext));
    }

}
