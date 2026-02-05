/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TRIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public TRIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void getValue() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(0).closePrice(12).highPrice(15).lowPrice(8).add();
        series.barBuilder().openPrice(0).closePrice(8).highPrice(11).lowPrice(6).add();
        series.barBuilder().openPrice(0).closePrice(15).highPrice(17).lowPrice(14).add();
        series.barBuilder().openPrice(0).closePrice(15).highPrice(17).lowPrice(14).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(0).lowPrice(2).add();

        var tr = new TRIndicator(series);

        assertNumEquals(7, tr.getValue(0));
        assertNumEquals(6, tr.getValue(1));
        assertNumEquals(9, tr.getValue(2));
        assertNumEquals(3, tr.getValue(3));
        assertNumEquals(15, tr.getValue(4));
    }

    @Test
    public void unstableBarsStartImmediately() {
        final var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(0).closePrice(12).highPrice(15).lowPrice(8).add();

        var tr = new TRIndicator(series);

        assertEquals(0, tr.getCountOfUnstableBars());
        assertFalse(Num.isNaNOrNull(tr.getValue(0)));
    }
}
