/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class LogReturnIndicatorTest extends AbstractIndicatorTest<LogReturnIndicator, Num> {

    public LogReturnIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void matchesKnownPrices() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 121).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator oneBar = new LogReturnIndicator(close);
        LogReturnIndicator twoBar = new LogReturnIndicator(close, 2);

        assertEquals(ReturnRepresentation.LOG, oneBar.getReturnRepresentation());
        assertSame(close, oneBar.getSourceIndicator());
        assertEquals(1, oneBar.getCountOfUnstableBars());
        assertTrue(oneBar.getValue(0).isNaN());
        assertNumEquals(Math.log(1.1), oneBar.getValue(1));
        assertNumEquals(Math.log(1.1), oneBar.getValue(2));
        assertNumEquals(Math.log(1.21), twoBar.getValue(2));
    }

    @Test
    public void rejectsInvalidAndNonPositiveInputs() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 0, -1, 110).build();
        LogReturnIndicator returns = new LogReturnIndicator(series);

        assertTrue(returns.getValue(1).isNaN());
        assertTrue(returns.getValue(2).isNaN());
        assertTrue(returns.getValue(3).isNaN());
        assertThrows(IllegalArgumentException.class, () -> new LogReturnIndicator(new ClosePriceIndicator(series), 0));
    }
}
