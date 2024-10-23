/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.BooleanCombineIndicator.BooleanToNumIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

public class BooleanCombineIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private FixedIndicator<Num> fixedIndicator1;
    private FixedIndicator<Num> fixedIndicator2;

    private BarSeries barSeries;

    public BooleanCombineIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        fixedIndicator1 = new FixedIndicator<>(barSeries,
                numFactory.numOf(1),
                numFactory.numOf(2),
                numFactory.numOf(3),
                numFactory.numOf(4),
                numFactory.numOf(5));
        fixedIndicator2 = new FixedIndicator<>(barSeries,
                numFactory.numOf(0),
                numFactory.numOf(2),
                numFactory.numOf(4),
                numFactory.numOf(6),
                numFactory.numOf(8));
    }

    @Test
    public void indicatorShouldRetrieveEquals() {
        BooleanCombineIndicator equals = BooleanCombineIndicator.equals(fixedIndicator1, fixedIndicator2);

        assertFalse(equals.getValue(0));
        assertTrue(equals.getValue(1));
        assertFalse(equals.getValue(2));
        assertFalse(equals.getValue(3));
        assertFalse(equals.getValue(4));

        BooleanToNumIndicator equalsNum = equals.asNum();

        assertNumEquals(0, equalsNum.getValue(0));
        assertNumEquals(1, equalsNum.getValue(1));
        assertNumEquals(0, equalsNum.getValue(2));
        assertNumEquals(0, equalsNum.getValue(3));
        assertNumEquals(0, equalsNum.getValue(4));
    }

    @Test
    public void indicatorShouldRetrieveEqual() {
        BooleanCombineIndicator isEqual = BooleanCombineIndicator.isEqual(fixedIndicator1, fixedIndicator2);

        assertFalse(isEqual.getValue(0));
        assertTrue(isEqual.getValue(1));
        assertFalse(isEqual.getValue(2));
        assertFalse(isEqual.getValue(3));
        assertFalse(isEqual.getValue(4));

        BooleanToNumIndicator isEqualNum = isEqual.asNum();

        assertNumEquals(0, isEqualNum.getValue(0));
        assertNumEquals(1, isEqualNum.getValue(1));
        assertNumEquals(0, isEqualNum.getValue(2));
        assertNumEquals(0, isEqualNum.getValue(3));
        assertNumEquals(0, isEqualNum.getValue(4));
    }

    @Test
    public void indicatorShouldRetrieveGreater() {
        BooleanCombineIndicator isGreaterThan = BooleanCombineIndicator.isGreaterThan(fixedIndicator1, fixedIndicator2);

        assertTrue(isGreaterThan.getValue(0));
        assertFalse(isGreaterThan.getValue(1));
        assertFalse(isGreaterThan.getValue(2));
        assertFalse(isGreaterThan.getValue(3));
        assertFalse(isGreaterThan.getValue(4));

        BooleanToNumIndicator isGreaterThanNum = isGreaterThan.asNum();

        assertNumEquals(1, isGreaterThanNum.getValue(0));
        assertNumEquals(0, isGreaterThanNum.getValue(1));
        assertNumEquals(0, isGreaterThanNum.getValue(2));
        assertNumEquals(0, isGreaterThanNum.getValue(3));
        assertNumEquals(0, isGreaterThanNum.getValue(4));
    }

    @Test
    public void indicatorShouldRetrieveGreaterOrEqual() {
        BooleanCombineIndicator isGreaterThanOrEqual = BooleanCombineIndicator.isGreaterThanOrEqual(fixedIndicator1, fixedIndicator2);

        assertTrue(isGreaterThanOrEqual.getValue(0));
        assertTrue(isGreaterThanOrEqual.getValue(1));
        assertFalse(isGreaterThanOrEqual.getValue(2));
        assertFalse(isGreaterThanOrEqual.getValue(3));
        assertFalse(isGreaterThanOrEqual.getValue(4));

        BooleanToNumIndicator isGreaterThanOrEqualNum = isGreaterThanOrEqual.asNum();

        assertNumEquals(1, isGreaterThanOrEqualNum.getValue(0));
        assertNumEquals(1, isGreaterThanOrEqualNum.getValue(1));
        assertNumEquals(0, isGreaterThanOrEqualNum.getValue(2));
        assertNumEquals(0, isGreaterThanOrEqualNum.getValue(3));
        assertNumEquals(0, isGreaterThanOrEqualNum.getValue(4));
    }

    @Test
    public void indicatorShouldRetrieveLess() {
        BooleanCombineIndicator isLessThan = BooleanCombineIndicator.isLessThan(fixedIndicator1, fixedIndicator2);

        assertFalse(isLessThan.getValue(0));
        assertFalse(isLessThan.getValue(1));
        assertTrue(isLessThan.getValue(2));
        assertTrue(isLessThan.getValue(3));
        assertTrue(isLessThan.getValue(4));

        BooleanToNumIndicator isLessThanNum = isLessThan.asNum();

        assertNumEquals(0, isLessThanNum.getValue(0));
        assertNumEquals(0, isLessThanNum.getValue(1));
        assertNumEquals(1, isLessThanNum.getValue(2));
        assertNumEquals(1, isLessThanNum.getValue(3));
        assertNumEquals(1, isLessThanNum.getValue(4));
    }

    @Test
    public void indicatorShouldRetrieveLessOrEqual() {
        BooleanCombineIndicator isLessThanOrEqual = BooleanCombineIndicator.isLessThanOrEqual(fixedIndicator1, fixedIndicator2);

        assertFalse(isLessThanOrEqual.getValue(0));
        assertTrue(isLessThanOrEqual.getValue(1));
        assertTrue(isLessThanOrEqual.getValue(2));
        assertTrue(isLessThanOrEqual.getValue(3));
        assertTrue(isLessThanOrEqual.getValue(4));

        BooleanToNumIndicator isLessThanOrEqualNum = isLessThanOrEqual.asNum();

        assertNumEquals(0, isLessThanOrEqualNum.getValue(0));
        assertNumEquals(1, isLessThanOrEqualNum.getValue(1));
        assertNumEquals(1, isLessThanOrEqualNum.getValue(2));
        assertNumEquals(1, isLessThanOrEqualNum.getValue(3));
        assertNumEquals(1, isLessThanOrEqualNum.getValue(4));
    }
}
