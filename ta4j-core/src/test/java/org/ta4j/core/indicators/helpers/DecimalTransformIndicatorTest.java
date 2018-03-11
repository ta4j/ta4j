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
package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.DecimalTransformIndicator.DecimalTransformSimpleType;
import org.ta4j.core.indicators.helpers.DecimalTransformIndicator.DecimalTransformType;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class DecimalTransformIndicatorTest extends AbstractIndicatorTest<Indicator<Num>,Num> {

    private DecimalTransformIndicator transPlus;
    private DecimalTransformIndicator transMinus;
    private DecimalTransformIndicator transMultiply;
    private DecimalTransformIndicator transDivide;
    private DecimalTransformIndicator transMax;
    private DecimalTransformIndicator transMin;
    
    private DecimalTransformIndicator transAbs;
    private DecimalTransformIndicator transSqrt;
    private DecimalTransformIndicator transLog;

    public DecimalTransformIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        TimeSeries series = new BaseTimeSeries.SeriesBuilder().withNumTypeOf(numFunction).build();
        ConstantIndicator<Num> constantIndicator = new ConstantIndicator<>(series, numOf(4));

        transPlus = new DecimalTransformIndicator(constantIndicator, 10, DecimalTransformType.plus);
        transMinus = new DecimalTransformIndicator(constantIndicator, 10, DecimalTransformType.minus);
        transMultiply = new DecimalTransformIndicator(constantIndicator, 10, DecimalTransformType.multiply);
        transDivide = new DecimalTransformIndicator(constantIndicator, 10, DecimalTransformType.divide);
        transMax = new DecimalTransformIndicator(constantIndicator, 10, DecimalTransformType.max);
        transMin = new DecimalTransformIndicator(constantIndicator, 10, DecimalTransformType.min);
        
        transAbs = new DecimalTransformIndicator(new ConstantIndicator<Num>(series, numOf(-4)), DecimalTransformSimpleType.abs);
        transSqrt = new DecimalTransformIndicator(constantIndicator, DecimalTransformSimpleType.sqrt);
        transLog = new DecimalTransformIndicator(constantIndicator, DecimalTransformSimpleType.log);
    }

    @Test
    public void getValue() {
        assertNumEquals(14, transPlus.getValue(0));
        assertNumEquals(-6, transMinus.getValue(0));
        assertNumEquals(40, transMultiply.getValue(0));
        assertNumEquals(0.4, transDivide.getValue(0));
        assertNumEquals(10, transMax.getValue(0));
        assertNumEquals(4, transMin.getValue(0));
        
        assertNumEquals(4, transAbs.getValue(0));
        assertNumEquals(2, transSqrt.getValue(0));
        assertNumEquals(1.3862943611198906, transLog.getValue(0));
    }
}
