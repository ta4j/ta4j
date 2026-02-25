/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TradeFillTest extends AbstractIndicatorTest<BarSeries, Num> {

    public TradeFillTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void storesFillAttributes() {
        TradeFill fill = new TradeFill(3, numFactory.hundred(), numFactory.two());

        assertEquals(3, fill.index());
        assertEquals(numFactory.hundred(), fill.price());
        assertEquals(numFactory.two(), fill.amount());
    }

    @Test
    public void rejectsNullPriceOrAmount() {
        assertThrows(NullPointerException.class, () -> new TradeFill(1, null, numFactory.one()));
        assertThrows(NullPointerException.class, () -> new TradeFill(1, numFactory.one(), null));
    }
}
