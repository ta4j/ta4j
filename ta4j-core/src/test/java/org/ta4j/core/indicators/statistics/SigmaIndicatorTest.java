/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class SigmaIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public SigmaIndicatorTest(NumFactory numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5, 6).build();
    }

    @Test
    public void test() {

        var zScore = new SigmaIndicator(new ClosePriceIndicator(data), 5);

        assertNumEquals(1.0, zScore.getValue(1));
        assertNumEquals(1.224744871391589, zScore.getValue(2));
        assertNumEquals(1.34164078649987387, zScore.getValue(3));
        assertNumEquals(1.414213562373095, zScore.getValue(4));
        assertNumEquals(1.414213562373095, zScore.getValue(5));
    }
}
