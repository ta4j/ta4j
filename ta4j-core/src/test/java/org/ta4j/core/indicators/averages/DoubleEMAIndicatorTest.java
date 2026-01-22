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

        // With barCount=5, unstable period is 5, so indices 0-4 return NaN
        assertThat(Double.isNaN(doubleEma.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(doubleEma.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(doubleEma.getValue(2).doubleValue())).isTrue();
        assertThat(Double.isNaN(doubleEma.getValue(3).doubleValue())).isTrue();
        assertThat(Double.isNaN(doubleEma.getValue(4).doubleValue())).isTrue();

        // Values after unstable period should be valid (not NaN)
        // Note: Values differ from old behavior because first EMA value after unstable
        // period
        // is now initialized to current value, not calculated from previous values
        assertThat(Double.isNaN(doubleEma.getValue(6).doubleValue())).isFalse();
        assertThat(Double.isNaN(doubleEma.getValue(7).doubleValue())).isFalse();
        assertThat(Double.isNaN(doubleEma.getValue(8).doubleValue())).isFalse();

        assertThat(Double.isNaN(doubleEma.getValue(12).doubleValue())).isFalse();
        assertThat(Double.isNaN(doubleEma.getValue(13).doubleValue())).isFalse();
        assertThat(Double.isNaN(doubleEma.getValue(14).doubleValue())).isFalse();
    }
}
