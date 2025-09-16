/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.analysis;

import org.junit.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.ta4j.core.Position;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AnalysisUtilsTest extends AbstractIndicatorTest<Object, Num> {

    public AnalysisUtilsTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void determineEndIndexUsesExitIndexWhenClosed() {
        var position = new Position();
        position.operate(0);
        position.operate(5);
        var result = AnalysisUtils.determineEndIndex(position, 10, 20);
        assertEquals(5, result);
    }

    @Test
    public void determineEndIndexUsesFinalIndexBeforeExit() {
        var position = new Position();
        position.operate(0);
        position.operate(10);
        var result = AnalysisUtils.determineEndIndex(position, 8, 20);
        assertEquals(8, result);
    }

    @Test
    public void determineEndIndexForOpenPositionClampsToMax() {
        var position = new Position();
        position.operate(0);
        var result = AnalysisUtils.determineEndIndex(position, 15, 12);
        assertEquals(12, result);
    }

    @Test
    public void determineEndIndexForClosedPositionClampsToMax() {
        var position = new Position();
        position.operate(0);
        position.operate(15);
        var result = AnalysisUtils.determineEndIndex(position, 20, 10);
        assertEquals(10, result);
    }

    @Test
    public void addCostSubtractsHoldingCostForLongTrades() {
        var raw = numFactory.hundred();
        var cost = numFactory.numOf(1.5);
        var result = AnalysisUtils.addCost(raw, cost, true);
        assertNumEquals("98.5", result);
    }

    @Test
    public void addCostAddsHoldingCostForShortTrades() {
        var raw = numFactory.hundred();
        var cost = numFactory.numOf(1.5);
        var result = AnalysisUtils.addCost(raw, cost, false);
        assertNumEquals("101.5", result);
    }

}