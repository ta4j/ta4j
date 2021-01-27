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
package org.ta4j.core.trading.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.FixedDecimalIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

public class InSlopeRuleTest {

    private InSlopeRule rulePositiveSlope;
    private InSlopeRule ruleNegativeSlope;
    private InSlopeRule ruleSlopeNaN;
    private InSlopeRule rulePositiveSlopeNaN;
    private InSlopeRule ruleNegativeSlopeNaN;

    @Before
    public void setUp() {
        BarSeries series = new BaseBarSeries();
        Indicator<Num> indicator = new FixedDecimalIndicator(series, 50, 70, 80, 90, 99, 60, 30, 20, 10, 0);
        rulePositiveSlope = new InSlopeRule(indicator, series.numOf(20), series.numOf(30));
        ruleNegativeSlope = new InSlopeRule(indicator, series.numOf(-40), series.numOf(-20));
        
        ruleSlopeNaN = new InSlopeRule(indicator, NaN.NaN, NaN.NaN);
        rulePositiveSlopeNaN = new InSlopeRule(indicator, NaN.NaN, series.numOf(30));
        ruleNegativeSlopeNaN = new InSlopeRule(indicator, series.numOf(-40), NaN.NaN);
    }

    @Test
    public void isSatisfied() {
        assertFalse(rulePositiveSlope.isSatisfied(0));
        assertTrue(rulePositiveSlope.isSatisfied(1));
        assertFalse(rulePositiveSlope.isSatisfied(2));
        assertFalse(rulePositiveSlope.isSatisfied(9));

        assertFalse(ruleNegativeSlope.isSatisfied(0));
        assertFalse(ruleNegativeSlope.isSatisfied(1));
        assertTrue(ruleNegativeSlope.isSatisfied(5));
        assertFalse(ruleNegativeSlope.isSatisfied(9));
        
        assertFalse(ruleSlopeNaN.isSatisfied(0));
        assertFalse(ruleSlopeNaN.isSatisfied(1));
        assertFalse(ruleSlopeNaN.isSatisfied(5));
        assertFalse(ruleSlopeNaN.isSatisfied(9));
        
        assertTrue(rulePositiveSlopeNaN.isSatisfied(0));
        assertTrue(rulePositiveSlopeNaN.isSatisfied(1));
        assertTrue(rulePositiveSlopeNaN.isSatisfied(2));
        assertTrue(rulePositiveSlopeNaN.isSatisfied(9));
        
        assertTrue(ruleNegativeSlopeNaN.isSatisfied(0));
        assertTrue(ruleNegativeSlopeNaN.isSatisfied(1));
        assertTrue(ruleNegativeSlopeNaN.isSatisfied(5));
        assertTrue(ruleNegativeSlopeNaN.isSatisfied(9));
    }
}
