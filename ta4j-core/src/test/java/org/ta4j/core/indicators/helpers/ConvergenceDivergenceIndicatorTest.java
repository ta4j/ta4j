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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ConvergenceDivergenceIndicator.ConvergenceDivergenceStrictType;
import org.ta4j.core.indicators.helpers.ConvergenceDivergenceIndicator.ConvergenceDivergenceType;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class ConvergenceDivergenceIndicatorTest {

    private Indicator<Num> refPosCon;
    private Indicator<Num> otherPosCon;

    private Indicator<Num> refNegCon;
    private Indicator<Num> otherNegCon;

    private Indicator<Num> refPosDiv;
    private Indicator<Num> otherNegDiv;

    private Indicator<Num> refNegDig;
    private Indicator<Num> otherPosDiv;

    private ConvergenceDivergenceIndicator isPosCon;
    private ConvergenceDivergenceIndicator isNegCon;

    private ConvergenceDivergenceIndicator isPosDiv;
    private ConvergenceDivergenceIndicator isNegDiv;

    private ConvergenceDivergenceIndicator isPosConStrict;
    private ConvergenceDivergenceIndicator isNegConStrict;

    private ConvergenceDivergenceIndicator isPosDivStrict;
    private ConvergenceDivergenceIndicator isNegDivStrict;

    @Before
    public void setUp() {
        BarSeries series = new MockBarSeriesBuilder().build();
        refPosCon = new FixedNumIndicator(series, 1, 2, 3, 4, 5, 8, 3, 2, -2, 1);
        otherPosCon = new FixedNumIndicator(series, 10, 20, 30, 40, 50, 60, 7, 5, 3, 2);

        refNegCon = new FixedNumIndicator(series, 150, 60, 20, 10, -20, -60, -200, -1, -200, 100);
        otherNegCon = new FixedNumIndicator(series, 80, 50, 40, 20, 10, 0, -30, -50, -150, 7);

        refPosDiv = new FixedNumIndicator(series, 1, 4, 8, 12, 15, 20, 3, 2, -2, 1);
        otherNegDiv = new FixedNumIndicator(series, 80, 50, 20, -10, 0, -100, -200, -2, 5, 7);

        refNegDig = new FixedNumIndicator(series, 100, 30, 15, 4, 2, -10, -3, -100, -2, 100);
        otherPosDiv = new FixedNumIndicator(series, 20, 40, 70, 80, 90, 100, 200, 220, -50, 7);

        // for convergence and divergence
        isPosCon = new ConvergenceDivergenceIndicator(refPosCon, otherPosCon, 3,
                ConvergenceDivergenceType.positiveConvergent);

        isNegCon = new ConvergenceDivergenceIndicator(refNegCon, otherNegCon, 3,
                ConvergenceDivergenceType.negativeConvergent);

        isPosDiv = new ConvergenceDivergenceIndicator(refPosDiv, otherNegDiv, 3,
                ConvergenceDivergenceType.positiveDivergent);

        isNegDiv = new ConvergenceDivergenceIndicator(refNegDig, otherPosDiv, 3,
                ConvergenceDivergenceType.negativeDivergent);

        // for strict convergence and divergence
        isPosConStrict = new ConvergenceDivergenceIndicator(refPosCon, otherPosDiv, 3,
                ConvergenceDivergenceStrictType.positiveConvergentStrict);

        isNegConStrict = new ConvergenceDivergenceIndicator(refNegDig, otherNegCon, 3,
                ConvergenceDivergenceStrictType.negativeConvergentStrict);

        isPosDivStrict = new ConvergenceDivergenceIndicator(otherPosDiv, refNegDig, 3,
                ConvergenceDivergenceStrictType.positiveDivergentStrict);

        isNegDivStrict = new ConvergenceDivergenceIndicator(refNegDig, otherPosDiv, 3,
                ConvergenceDivergenceStrictType.negativeDivergentStrict);

    }

    @Test
    public void isSatisfied() {

        testPositiveConvergent();
        testNegativeConvergent();
        testPositiveDivergent();
        testNegativeDivergent();

        // testPositiveConvergentStrict();
        // testNegativeConvergentStrict();
        // testPositiveDivergentStrict();
        // testNegativeDivergentStrict();

    }

    public void testPositiveConvergent() {
        assertFalse(isPosCon.getValue(0));
        assertFalse(isPosCon.getValue(1));
        assertFalse(isPosCon.getValue(2));
        assertTrue(isPosCon.getValue(3));
        assertTrue(isPosCon.getValue(4));
        assertTrue(isPosCon.getValue(5));
        assertFalse(isPosCon.getValue(6));
        assertFalse(isPosCon.getValue(7));
        assertTrue(isPosCon.getValue(8));
        assertFalse(isPosCon.getValue(9));
    }

    public void testNegativeConvergent() {
        assertFalse(isNegCon.getValue(0));
        assertFalse(isNegCon.getValue(1));
        assertFalse(isNegCon.getValue(2));
        assertTrue(isNegCon.getValue(3));
        assertFalse(isNegCon.getValue(4));
        assertFalse(isNegCon.getValue(5));
        assertFalse(isNegCon.getValue(6));
        assertFalse(isNegCon.getValue(7));
        assertFalse(isNegCon.getValue(8));
        assertFalse(isNegCon.getValue(9));
    }

    public void testPositiveDivergent() {
        assertFalse(isPosDiv.getValue(0));
        assertFalse(isPosDiv.getValue(1));
        assertFalse(isPosDiv.getValue(2));
        assertTrue(isPosDiv.getValue(3));
        assertFalse(isPosDiv.getValue(4));
        assertTrue(isPosDiv.getValue(5));
        assertFalse(isPosDiv.getValue(6));
        assertFalse(isPosDiv.getValue(7));
        assertFalse(isPosDiv.getValue(8));
        assertFalse(isPosDiv.getValue(9));
    }

    public void testNegativeDivergent() {
        assertFalse(isNegDiv.getValue(0));
        assertFalse(isNegDiv.getValue(1));
        assertFalse(isNegDiv.getValue(2));
        assertTrue(isNegDiv.getValue(3));
        assertTrue(isNegDiv.getValue(4));
        assertFalse(isNegDiv.getValue(5));
        assertFalse(isNegDiv.getValue(6));
        assertFalse(isNegDiv.getValue(7));
        assertFalse(isNegDiv.getValue(8));
        assertFalse(isNegDiv.getValue(9));
    }

    public void testPositiveConvergentStrict() {
        assertFalse(isPosConStrict.getValue(0));
        assertFalse(isPosConStrict.getValue(1));
        assertFalse(isPosConStrict.getValue(2));
        assertTrue(isPosConStrict.getValue(3));
        assertTrue(isPosConStrict.getValue(4));
        assertTrue(isPosConStrict.getValue(5));
        assertFalse(isPosConStrict.getValue(6));
        assertFalse(isPosConStrict.getValue(7));
        assertTrue(isPosConStrict.getValue(8));
        assertFalse(isPosConStrict.getValue(9));
    }

    public void testNegativeConvergentStrict() {
        assertFalse(isNegConStrict.getValue(0));
        assertFalse(isNegConStrict.getValue(1));
        assertFalse(isNegConStrict.getValue(2));
        assertTrue(isNegConStrict.getValue(3));
        assertFalse(isNegConStrict.getValue(4));
        assertFalse(isNegConStrict.getValue(5));
        assertFalse(isNegConStrict.getValue(6));
        assertFalse(isNegConStrict.getValue(7));
        assertFalse(isNegConStrict.getValue(8));
        assertFalse(isNegConStrict.getValue(9));
    }

    public void testPositiveDivergentStrict() {
        assertFalse(isPosDivStrict.getValue(0));
        assertFalse(isPosDivStrict.getValue(1));
        assertFalse(isPosDivStrict.getValue(2));
        assertTrue(isPosDivStrict.getValue(3));
        assertFalse(isPosDivStrict.getValue(4));
        assertTrue(isPosDivStrict.getValue(5));
        assertFalse(isPosDivStrict.getValue(6));
        assertFalse(isPosDivStrict.getValue(7));
        assertFalse(isPosDivStrict.getValue(8));
        assertFalse(isPosDivStrict.getValue(9));
    }

    public void testNegativeDivergentStrict() {
        assertFalse(isNegDivStrict.getValue(0));
        assertFalse(isNegDivStrict.getValue(1));
        assertFalse(isNegDivStrict.getValue(2));
        assertTrue(isNegDivStrict.getValue(3));
        assertTrue(isNegDivStrict.getValue(4));
        assertFalse(isNegDivStrict.getValue(5));
        assertFalse(isNegDivStrict.getValue(6));
        assertFalse(isNegDivStrict.getValue(7));
        assertFalse(isNegDivStrict.getValue(8));
        assertFalse(isNegDivStrict.getValue(9));
    }

}
