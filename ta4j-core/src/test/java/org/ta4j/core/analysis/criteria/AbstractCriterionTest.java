/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core.analysis.criteria;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@RunWith(Parameterized.class)
public abstract class AbstractCriterionTest {

    private Class indicatorClass;
    protected final Function<Number, Num> numFunction;

    @Parameterized.Parameters(name = "Test Case: {index} (0=BigDecimalNum, 1=DoubleNum, 2=PrecisionNum)")
    public static List<Function<Number, Num>> function(){
        return Arrays.asList(BigDecimalNum::valueOf, DoubleNum::valueOf, PrecisionNum::valueOf);
    }

    /**
     * Constructor.
     * 
     * @param indicatorClass The indicator class to test
     */
    protected AbstractCriterionTest(Class indicatorClass, Function<Number, Num> numFunction) {
        this.indicatorClass = indicatorClass;
        this.numFunction = numFunction;
    }

    /**
     * Generates an AnalysisCriterion given criterion parameters.
     * 
     * @param params criterion parameters
     * @return AnalysisCriterion given parameters
     */
    public AnalysisCriterion getCriterion(Object... params) {
        try {
            return (AnalysisCriterion)indicatorClass.getConstructor().newInstance(params);
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate " + indicatorClass.getSimpleName()
                + " implement getCriterion in your test class instead.");
        }
    }

    public Num numOf(Number n){
        return numFunction.apply(n);
    }

}
