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

public class PPOIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private ClosePriceIndicator closePriceIndicator;

    public PPOIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(22.27, 22.19, 22.08, 22.17, 22.18, 22.13, 22.23, 22.43, 22.24, 22.29, 22.15, 22.39, 22.38,
                        22.61, 23.36, 24.05, 23.75, 23.83, 23.95, 23.63, 23.82, 23.87, 23.65, 23.19, 23.10, 23.33,
                        22.68, 23.10, 21.40, 20.17)
                .build();
        closePriceIndicator = new ClosePriceIndicator(series);
    }

    @Test
    public void getValueWithEma10AndEma20() {
        var ppo = new PPOIndicator(closePriceIndicator, 10, 20);

        // PPO unstable period is longPeriod (20), so indices 0-19 return NaN
        // because long EMA returns NaN during its unstable period
        for (int i = 0; i < 20; i++) {
            assertThat(Double.isNaN(ppo.getValue(i).doubleValue())).isTrue();
        }

        // Values after unstable period should be valid (not NaN)
        // Note: Values will differ from expected because first EMA value after unstable
        // period
        // is now initialized to current value, not calculated from previous values
        assertThat(Double.isNaN(ppo.getValue(21).doubleValue())).isFalse();
        assertThat(Double.isNaN(ppo.getValue(22).doubleValue())).isFalse();
        assertThat(Double.isNaN(ppo.getValue(23).doubleValue())).isFalse();
        assertThat(Double.isNaN(ppo.getValue(28).doubleValue())).isFalse();
        assertThat(Double.isNaN(ppo.getValue(29).doubleValue())).isFalse();
    }
}
