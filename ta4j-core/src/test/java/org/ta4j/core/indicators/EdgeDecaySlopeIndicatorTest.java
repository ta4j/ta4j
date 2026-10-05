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

public class EdgeDecaySlopeIndicatorTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        for (int i = 0; i < 8; i++) {
            series.barBuilder().openPrice(10).highPrice(10).lowPrice(10).closePrice(10).volume(1).add();
        }
    }

    @Test
    public void shouldBePositiveForAnImprovingEdgeSeries() {
        Indicator<Num> edge = new CachedIndicator<>(series) {
            @Override
            protected Num calculate(int index) {
                return series.numFactory().numOf(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        EdgeDecaySlopeIndicator subject = new EdgeDecaySlopeIndicator(edge, 4);

        assertThat(subject.getValue(7).isPositive()).isTrue();
    }
}
