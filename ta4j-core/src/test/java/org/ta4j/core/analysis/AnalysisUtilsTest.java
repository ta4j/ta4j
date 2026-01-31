/*
 * SPDX-License-Identifier: MIT
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