/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

class MinMagnitudeSwingFilterTest {

    @Test
    void filtersBelowRelativeThreshold() {
        BarSeries series = buildSeries();
        NumFactory factory = series.numFactory();
        ElliottSwing large = new ElliottSwing(0, 5, factory.hundred(), factory.numOf(150), ElliottDegree.PRIMARY);
        ElliottSwing medium = new ElliottSwing(5, 8, factory.numOf(150), factory.numOf(160), ElliottDegree.PRIMARY);
        ElliottSwing small = new ElliottSwing(8, 10, factory.numOf(160), factory.numOf(162), ElliottDegree.PRIMARY);

        MinMagnitudeSwingFilter filter = new MinMagnitudeSwingFilter(0.2);
        List<ElliottSwing> filtered = filter.filter(List.of(large, medium, small));

        assertThat(filtered).containsExactly(large, medium);
    }

    private BarSeries buildSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("FilterTest").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < 12; i++) {
            series.barBuilder()
                    .timePeriod(period)
                    .endTime(time.plus(period.multipliedBy(i)))
                    .openPrice(100 + i)
                    .highPrice(110 + i)
                    .lowPrice(90 + i)
                    .closePrice(100 + i)
                    .volume(1000)
                    .add();
        }
        return series;
    }

}
