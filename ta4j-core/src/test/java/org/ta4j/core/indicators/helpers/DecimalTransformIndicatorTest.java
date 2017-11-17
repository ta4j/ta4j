/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Decimal;
import org.ta4j.core.indicators.helpers.DecimalTransformIndicator.DecimalTransformSimpleType;
import org.ta4j.core.indicators.helpers.DecimalTransformIndicator.DecimalTransformType;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;

public class DecimalTransformIndicatorTest {

    private DecimalTransformIndicator transPlus;
    private DecimalTransformIndicator transMinus;
    private DecimalTransformIndicator transMultiply;
    private DecimalTransformIndicator transDivide;
    private DecimalTransformIndicator transMax;
    private DecimalTransformIndicator transMin;
    
    private DecimalTransformIndicator transAbs;
    private DecimalTransformIndicator transSqrt;
    private DecimalTransformIndicator transLog;
    
    @Before
    public void setUp() {
        ConstantIndicator<Decimal> constantIndicator = new ConstantIndicator<Decimal>(Decimal.valueOf(4));

        transPlus = new DecimalTransformIndicator(constantIndicator, Decimal.TEN, DecimalTransformType.plus);
        transMinus = new DecimalTransformIndicator(constantIndicator, Decimal.TEN, DecimalTransformType.minus);
        transMultiply = new DecimalTransformIndicator(constantIndicator, Decimal.TEN, DecimalTransformType.multiply);
        transDivide = new DecimalTransformIndicator(constantIndicator, Decimal.TEN, DecimalTransformType.divide);
        transMax = new DecimalTransformIndicator(constantIndicator, Decimal.TEN, DecimalTransformType.max);
        transMin = new DecimalTransformIndicator(constantIndicator, Decimal.TEN, DecimalTransformType.min);
        
        transAbs = new DecimalTransformIndicator(new ConstantIndicator<Decimal>(Decimal.valueOf(-4)), DecimalTransformSimpleType.abs);
        transSqrt = new DecimalTransformIndicator(constantIndicator, DecimalTransformSimpleType.sqrt);
        transLog = new DecimalTransformIndicator(constantIndicator, DecimalTransformSimpleType.log);
    }

    @Test
    public void getValue() {
        assertDecimalEquals(transPlus.getValue(0), "14");
        assertDecimalEquals(transMinus.getValue(0), "-6");
        assertDecimalEquals(transMultiply.getValue(0), "40");
        assertDecimalEquals(transDivide.getValue(0), "0.4");
        assertDecimalEquals(transMax.getValue(0), "10");
        assertDecimalEquals(transMin.getValue(0), "4");
        
        assertDecimalEquals(transAbs.getValue(0), "4");
        assertDecimalEquals(transSqrt.getValue(0), "2");
        assertDecimalEquals(transLog.getValue(0), "1.3862943611198905724535279659904");
    }
}
