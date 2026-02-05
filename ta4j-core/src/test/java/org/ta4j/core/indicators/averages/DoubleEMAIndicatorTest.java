/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DoubleEMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private ClosePriceIndicator closePrice;

    public DoubleEMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        var data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(0.73, 0.72, 0.86, 0.72, 0.62, 0.76, 0.84, 0.69, 0.65, 0.71, 0.53, 0.73, 0.77, 0.67, 0.68)
                .build();
        closePrice = new ClosePriceIndicator(data);
    }

    @Test
    public void doubleEMAUsingBarCount5UsingClosePrice() {
        var doubleEma = new DoubleEMAIndicator(closePrice, 5);

        int unstableBars = doubleEma.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(doubleEma.getValue(i))).isTrue();
        }

        for (int i = unstableBars; i < closePrice.getBarSeries().getBarCount(); i++) {
            assertThat(Num.isNaNOrNull(doubleEma.getValue(i))).isFalse();
        }
    }
}
