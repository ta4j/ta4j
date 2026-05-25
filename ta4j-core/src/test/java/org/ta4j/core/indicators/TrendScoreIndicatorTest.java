/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class TrendScoreIndicatorTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        for (int i = 0; i < 8; i++) {
            series.barBuilder().openPrice(10).highPrice(10).lowPrice(10).closePrice(10).volume(1).add();
        }
    }

    @Test
    public void shouldBePositiveForAnUptrend() {
        TrendScoreIndicator subject = new TrendScoreIndicator(metric(1, 2, 3, 4, 5, 6, 7, 8),
                metric(0.5, 1, 1.5, 2, 2.5, 3, 3.5, 4), metric(10, 15, 20, 25, 30, 35, 40, 45), 3);

        assertThat(subject.getValue(7).isPositive()).isTrue();
    }

    @Test
    public void shouldBeNegativeForADowntrend() {
        TrendScoreIndicator subject = new TrendScoreIndicator(metric(8, 7, 6, 5, 4, 3, 2, 1),
                metric(4, 3.5, 3, 2.5, 2, 1.5, 1, 0.5), metric(10, 15, 20, 25, 30, 35, 40, 45), 3);

        assertThat(subject.getValue(7).isNegative()).isTrue();
    }

    private Indicator<Num> metric(double... values) {
        return new CachedIndicator<>(series) {
            @Override
            protected Num calculate(int index) {
                return series.numFactory().numOf(values[index]);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
    }
}
