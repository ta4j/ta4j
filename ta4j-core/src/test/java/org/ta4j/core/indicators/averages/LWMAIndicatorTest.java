/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class LWMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries data;

    public LWMAIndicatorTest(NumFactory numFunction) {
        super(numFunction);
    }

    @BeforeEach
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(37.08, 36.7, 36.11, 35.85, 35.71, 36.04, 36.41, 37.67, 38.01, 37.79, 36.83)
                .build();
    }

    @Test
    public void lwmaUsingBarCount5UsingClosePrice() {
        var lwma = new LWMAIndicator(new ClosePriceIndicator(data), 5);
        int unstableBars = lwma.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertThat(Num.isNaNOrNull(lwma.getValue(i))).isTrue();
        }
        assertNumEquals(36.0506, lwma.getValue(4));
        assertNumEquals(35.9673, lwma.getValue(5));
        assertNumEquals(36.0766, lwma.getValue(6));
        assertNumEquals(36.6253, lwma.getValue(7));
        assertNumEquals(37.1833, lwma.getValue(8));
        assertNumEquals(37.5240, lwma.getValue(9));
        assertNumEquals(37.4060, lwma.getValue(10));
    }

    @Test
    public void historicalReplacementAndExpirationUpdateWeightedAverage() {
        var series = (BaseBarSeries) new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6)
                .build();
        var average = new LWMAIndicator(new ClosePriceIndicator(series), 3);
        for (int i = 2; i <= 3; i++) {
            average.getValue(i);
        }
        var old = series.getBar(1);
        series.replaceBar(1,
                series.barBuilder()
                        .timePeriod(old.getTimePeriod())
                        .endTime(old.getEndTime())
                        .openPrice(20)
                        .highPrice(20)
                        .lowPrice(20)
                        .closePrice(20)
                        .volume(old.getVolume())
                        .build());

        assertNumEquals(38.0 / 6, average.getValue(3));
        assertNumEquals(26.0 / 6, average.getValue(4));
    }

    @Test
    public void weightedAverageRecoversAfterLargeValuesExpire() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1e100, 1e100, 1, 2, 3, 4).build();
        var average = new LWMAIndicator(new ClosePriceIndicator(series), 2);
        average.getValue(1);
        average.getValue(2);
        assertNumEquals(5.0 / 3, average.getValue(3));
        assertNumEquals(8.0 / 3, average.getValue(4));
    }
}
