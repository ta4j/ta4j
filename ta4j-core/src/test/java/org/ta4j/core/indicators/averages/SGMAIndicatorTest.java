/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.ta4j.core.TestUtils.*;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.CsvTestUtils;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SGMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public SGMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void sgmaIndicatorTest() {

        MockIndicator mock = CsvTestUtils.getCsvFile(SGMAIndicatorTest.class, "SGMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        SGMAIndicator ma = new SGMAIndicator(new ClosePriceIndicator(barSeries), 9, 2);

        int unstableBars = ma.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(ma.getValue(i))).isTrue();
        }

        for (int i = unstableBars; i < barSeries.getBarCount(); i++) {
            Num expected = mock.getValue(i);
            Num value = ma.getValue(i);
            assertNumEquals(expected.doubleValue(), value);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void evenBarCountThrowsException() {
        MockIndicator mock = CsvTestUtils.getCsvFile(SGMAIndicatorTest.class, "SGMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        new SGMAIndicator(new ClosePriceIndicator(barSeries), 10, 2);

        fail("Should have thrown an exception");
    }

    @Test(expected = IllegalArgumentException.class)
    public void barCountShouldBeGreaterThanPolynomialOrderThrowsException() {
        MockIndicator mock = CsvTestUtils.getCsvFile(SGMAIndicatorTest.class, "SGMA.csv", numFactory);

        BarSeries barSeries = mock.getBarSeries();

        new SGMAIndicator(new ClosePriceIndicator(barSeries), 3, 5);

        fail("Should have thrown an exception");
    }
}
