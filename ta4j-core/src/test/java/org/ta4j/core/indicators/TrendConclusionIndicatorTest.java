/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.assertIndicatorRoundTrips;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

public class TrendConclusionIndicatorTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        for (int i = 0; i < 8; i++) {
            series.barBuilder().openPrice(10).highPrice(10).lowPrice(10).closePrice(10).volume(1).add();
        }
    }

    @Test
    public void shouldIncreaseWhenTheTrendCoolsIntoARange() {
        TrendConclusionIndicator subject = new TrendConclusionIndicator(metric(1, 2, 3, 4, 5, 6, 7, 8),
                metric(20, 25, 30, 45, 60, 75, 85, 95), metric(15, 20, 25, 40, 55, 70, 82, 94),
                metric(10, 15, 20, 35, 50, 65, 80, 90), 3);

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

    @Test
    public void serializationRoundTrips() {
        for (NumFactory numFactory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            BarSeries series = serializationSeries(numFactory);
            assertIndicatorRoundTrips(series, new TrendConclusionIndicator(series, 8, 4, 9, 3, 5, 6, 8),
                    stableIndexes(series));
        }
    }

}
