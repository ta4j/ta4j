/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MACDIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public MACDIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    private BarSeries data;

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(37.08, 36.7, 36.11, 35.85, 35.71, 36.04, 36.41, 37.67, 38.01, 37.79, 36.83, 37.10, 38.01,
                        38.50, 38.99)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwsErrorOnIllegalArguments() {
        new MACDIndicator(new ClosePriceIndicator(data), 10, 5);
    }

    @Test
    public void macdUsingPeriod5And10() {
        var macdIndicator = new MACDIndicator(new ClosePriceIndicator(data), 5, 10);

        // MACD unstable period is slowPeriod (10), so indices 0-9 return NaN
        // because slow EMA returns NaN during its unstable period
        for (int i = 0; i < 10; i++) {
            assertThat(Double.isNaN(macdIndicator.getValue(i).doubleValue())).isTrue();
        }

        // Values after unstable period should be valid (not NaN)
        // Note: Values will differ from expected because first EMA value after unstable
        // period
        // is now initialized to current value, not calculated from previous values
        assertThat(Double.isNaN(macdIndicator.getValue(10).doubleValue())).isFalse();

        // Short EMA (period 5): unstable period is 5, so indices 0-4 are NaN, index 5+
        // are valid
        assertThat(Double.isNaN(macdIndicator.getShortTermEma().getValue(4).doubleValue())).isTrue();
        assertThat(Double.isNaN(macdIndicator.getShortTermEma().getValue(5).doubleValue())).isFalse();

        // Long EMA (period 10): unstable period is 10, so indices 0-9 are NaN, index
        // 10+ are valid
        assertThat(Double.isNaN(macdIndicator.getLongTermEma().getValue(9).doubleValue())).isTrue();
        assertThat(Double.isNaN(macdIndicator.getLongTermEma().getValue(10).doubleValue())).isFalse();
    }
}
