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
package org.ta4j.core.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.FixedDecimalIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.helper.ChainLink;

public class ChainRuleTest {

    private ChainRule chainRule;

    @Before
    public void setUp() {
        BarSeries series = new BaseBarSeries();
        Indicator<Num> indicator = new FixedDecimalIndicator(series, 6, 5, 8, 5, 1, 10, 2, 30);
        UnderIndicatorRule underIndicatorRule = new UnderIndicatorRule(indicator, series.numOf(5));
        OverIndicatorRule overIndicatorRule = new OverIndicatorRule(indicator, 7);
        IsEqualRule isEqualRule = new IsEqualRule(indicator, 5);
        chainRule = new ChainRule(underIndicatorRule, new ChainLink(overIndicatorRule, 3),
                new ChainLink(isEqualRule, 2));
    }

    @Test
    public void isSatisfied() {
        assertFalse(chainRule.isSatisfied(0));
        assertTrue(chainRule.isSatisfied(4));
        assertTrue(chainRule.isSatisfied(6));
        assertFalse(chainRule.isSatisfied(7));
    }
}
