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
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

class CompositeSwingDetectorTest {

    @Test
    void andPolicyRetainsOnlySharedPivots() {
        BarSeries series = singleSeries();
        NumFactory factory = series.numFactory();
        List<SwingPivot> pivotsA = List.of(new SwingPivot(1, factory.hundred(), SwingPivotType.LOW),
                new SwingPivot(2, factory.numOf(120), SwingPivotType.HIGH),
                new SwingPivot(3, factory.numOf(110), SwingPivotType.LOW));
        List<SwingPivot> pivotsB = List.of(new SwingPivot(1, factory.hundred().plus(factory.one()), SwingPivotType.LOW),
                new SwingPivot(2, factory.numOf(121), SwingPivotType.HIGH),
                new SwingPivot(4, factory.numOf(105), SwingPivotType.LOW));

        SwingDetector detectorA = (s, index, degree) -> SwingDetectorResult.fromPivots(pivotsA, degree);
        SwingDetector detectorB = (s, index, degree) -> SwingDetectorResult.fromPivots(pivotsB, degree);

        CompositeSwingDetector composite = new CompositeSwingDetector(CompositeSwingDetector.Policy.AND,
                List.of(detectorA, detectorB));
        SwingDetectorResult result = composite.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);

        assertThat(result.pivots()).extracting(SwingPivot::index).containsExactly(1, 2);
        assertThat(result.swings()).hasSize(1);
    }

    @Test
    void orPolicyMergesDistinctPivots() {
        BarSeries series = singleSeries();
        NumFactory factory = series.numFactory();
        List<SwingPivot> pivotsA = List.of(new SwingPivot(1, factory.hundred(), SwingPivotType.LOW),
                new SwingPivot(2, factory.numOf(120), SwingPivotType.HIGH),
                new SwingPivot(3, factory.numOf(110), SwingPivotType.LOW));
        List<SwingPivot> pivotsB = List.of(new SwingPivot(1, factory.hundred().plus(factory.one()), SwingPivotType.LOW),
                new SwingPivot(2, factory.numOf(121), SwingPivotType.HIGH),
                new SwingPivot(4, factory.numOf(105), SwingPivotType.LOW));

        SwingDetector detectorA = (s, index, degree) -> SwingDetectorResult.fromPivots(pivotsA, degree);
        SwingDetector detectorB = (s, index, degree) -> SwingDetectorResult.fromPivots(pivotsB, degree);

        CompositeSwingDetector composite = new CompositeSwingDetector(CompositeSwingDetector.Policy.OR,
                List.of(detectorA, detectorB));
        SwingDetectorResult result = composite.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);

        assertThat(result.pivots()).extracting(SwingPivot::index).containsExactly(1, 2, 4);
        assertThat(result.swings()).hasSize(2);
    }

    private BarSeries singleSeries() {
        BarSeries series = new MockBarSeriesBuilder().withName("CompositeTest").build();
        Duration period = Duration.ofDays(1);
        Instant time = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < 6; i++) {
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
