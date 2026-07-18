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

public class CompressionIndicatorTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        for (int i = 0; i < 8; i++) {
            series.barBuilder().openPrice(10).highPrice(10).lowPrice(10).closePrice(10).volume(1).add();
        }
    }

    @Test
    public void shouldRiseWhenTheSeriesTightens() {
        CompressionIndicator subject = new CompressionIndicator(metric(80, 70, 60, 70, 60, 50, 40, 10),
                metric(76, 68, 62, 69, 59, 48, 39, 9), metric(74, 66, 58, 67, 57, 46, 35, 8), 3, 3);

        assertThat(subject.getValue(7).isGreaterThan(subject.getValue(3))).isTrue();
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
