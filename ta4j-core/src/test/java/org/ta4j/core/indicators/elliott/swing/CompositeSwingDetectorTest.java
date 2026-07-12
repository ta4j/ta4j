/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.swing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.groups.Tuple.tuple;
import static org.ta4j.core.num.NaN.NaN;

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

    @Test
    void tolerantConsensusClustersNearbyPivotsAndCountsDistinctDetectors() {
        BarSeries series = singleSeries();
        NumFactory factory = series.numFactory();
        SwingDetector detectorA = (s, index, degree) -> SwingDetectorResult
                .fromPivots(List.of(new SwingPivot(1, factory.hundred(), SwingPivotType.LOW),
                        new SwingPivot(3, factory.numOf(120), SwingPivotType.HIGH)), degree);
        SwingDetector detectorB = (s, index, degree) -> SwingDetectorResult
                .fromPivots(List.of(new SwingPivot(2, factory.numOf(99), SwingPivotType.LOW),
                        new SwingPivot(4, factory.numOf(122), SwingPivotType.HIGH)), degree);

        CompositeSwingDetector detector = new CompositeSwingDetector(List.of(detectorA, detectorB), 1, 2);
        SwingDetectorResult result = detector.detect(series, series.getEndIndex(), ElliottDegree.MINOR);

        assertThat(result.pivots()).extracting(SwingPivot::index).containsExactly(2, 4);
        CompositeSwingDetector reversed = new CompositeSwingDetector(List.of(detectorB, detectorA), 1, 2);
        assertThat(reversed.detect(series, series.getEndIndex(), ElliottDegree.MINOR).pivots())
                .isEqualTo(result.pivots());
        assertThat(detector.getIndexTolerance()).isEqualTo(1);
        assertThat(detector.getRequiredVotes()).isEqualTo(2);

        SwingDetector factoryDetector = SwingDetectors.consensus(1, 2, detectorA, detectorB);
        assertThat(factoryDetector.detect(series, series.getEndIndex(), ElliottDegree.MINOR).pivots())
                .isEqualTo(result.pivots());
    }

    @Test
    void tolerantConsensusValidatesToleranceAndQuorum() {
        SwingDetector detector = (series, index, degree) -> new SwingDetectorResult(List.of(), List.of());

        assertThrows(IllegalArgumentException.class, () -> new CompositeSwingDetector(List.of(detector), -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CompositeSwingDetector(List.of(detector), 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new CompositeSwingDetector(List.of(detector), 0, 2));
        assertThrows(IllegalArgumentException.class, () -> SwingDetectors.consensus(0, 1));
    }

    @Test
    void tolerantConsensusPrefersFiniteRepresentative() {
        BarSeries series = singleSeries();
        NumFactory factory = series.numFactory();
        SwingDetector detectorA = (s, index,
                degree) -> new SwingDetectorResult(List.of(new SwingPivot(1, NaN, SwingPivotType.LOW)), List.of());
        SwingDetector detectorB = (s, index, degree) -> SwingDetectorResult
                .fromPivots(List.of(new SwingPivot(2, factory.hundred(), SwingPivotType.LOW)), degree);

        CompositeSwingDetector detector = new CompositeSwingDetector(List.of(detectorA, detectorB), 1, 2);

        assertThat(detector.detect(series, series.getEndIndex(), ElliottDegree.MINOR).pivots()).singleElement()
                .satisfies(pivot -> {
                    assertThat(pivot.index()).isEqualTo(2);
                    assertThat(pivot.price()).isEqualByComparingTo(factory.hundred());
                });
    }

    @Test
    void orPolicyCollapsesSharedIndexOppositePivotsIntoStrictlyIncreasingSwings() {
        BarSeries series = singleSeries();
        NumFactory factory = series.numFactory();
        List<SwingPivot> pivotsA = List.of(new SwingPivot(1, factory.hundred(), SwingPivotType.LOW),
                new SwingPivot(2, factory.numOf(120), SwingPivotType.HIGH),
                new SwingPivot(4, factory.numOf(105), SwingPivotType.LOW));
        List<SwingPivot> pivotsB = List.of(new SwingPivot(1, factory.hundred().plus(factory.one()), SwingPivotType.LOW),
                new SwingPivot(2, factory.numOf(90), SwingPivotType.LOW),
                new SwingPivot(5, factory.numOf(125), SwingPivotType.HIGH));

        SwingDetector detectorA = (s, index, degree) -> new SwingDetectorResult(pivotsA, List.of());
        SwingDetector detectorB = (s, index, degree) -> new SwingDetectorResult(pivotsB, List.of());

        CompositeSwingDetector composite = new CompositeSwingDetector(CompositeSwingDetector.Policy.OR,
                List.of(detectorA, detectorB));
        SwingDetectorResult result = composite.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);

        assertThat(result.pivots()).extracting(SwingPivot::index, SwingPivot::type)
                .containsExactly(tuple(1, SwingPivotType.LOW), tuple(2, SwingPivotType.HIGH),
                        tuple(4, SwingPivotType.LOW), tuple(5, SwingPivotType.HIGH));
        assertThat(result.pivots().get(1).price()).isEqualByComparingTo(factory.numOf(120));
        assertThat(result.swings()).allSatisfy(swing -> assertThat(swing.toIndex()).isGreaterThan(swing.fromIndex()));
    }

    @Test
    void orPolicySharedIndexConflictPrefersValidAlternatingPivotWhenPeerPriceIsNaN() {
        BarSeries series = singleSeries();
        NumFactory factory = series.numFactory();
        List<SwingPivot> pivotsA = List.of(new SwingPivot(1, factory.hundred(), SwingPivotType.LOW),
                new SwingPivot(2, NaN, SwingPivotType.LOW), new SwingPivot(5, factory.numOf(125), SwingPivotType.HIGH));
        List<SwingPivot> pivotsB = List.of(new SwingPivot(1, factory.hundred().plus(factory.one()), SwingPivotType.LOW),
                new SwingPivot(2, factory.numOf(120), SwingPivotType.HIGH),
                new SwingPivot(4, factory.numOf(105), SwingPivotType.LOW));

        SwingDetector detectorA = (s, index, degree) -> new SwingDetectorResult(pivotsA, List.of());
        SwingDetector detectorB = (s, index, degree) -> new SwingDetectorResult(pivotsB, List.of());

        CompositeSwingDetector composite = new CompositeSwingDetector(CompositeSwingDetector.Policy.OR,
                List.of(detectorA, detectorB));
        SwingDetectorResult result = composite.detect(series, series.getEndIndex(), ElliottDegree.PRIMARY);

        assertThat(result.pivots()).extracting(SwingPivot::index, SwingPivot::type)
                .containsExactly(tuple(1, SwingPivotType.LOW), tuple(2, SwingPivotType.HIGH),
                        tuple(4, SwingPivotType.LOW), tuple(5, SwingPivotType.HIGH));
        assertThat(result.pivots().get(1).price()).isEqualByComparingTo(factory.numOf(120));
    }

    @Test
    void normalizePivotsPrefersFiniteSharedIndexPivotBeforeAlternation() {
        BarSeries series = singleSeries();
        NumFactory factory = series.numFactory();
        List<SwingPivot> normalized = SwingDetectorSupport.normalizePivots(List.of(
                new SwingPivot(1, factory.hundred(), SwingPivotType.LOW),
                new SwingPivot(2, factory.numOf(120), SwingPivotType.HIGH), new SwingPivot(4, NaN, SwingPivotType.LOW),
                new SwingPivot(4, factory.numOf(118), SwingPivotType.HIGH)));

        assertThat(normalized).extracting(SwingPivot::index, SwingPivot::type)
                .containsExactly(tuple(1, SwingPivotType.LOW), tuple(2, SwingPivotType.HIGH));
        assertThat(normalized).allSatisfy(pivot -> assertThat(pivot.price()).isNotEqualTo(NaN));
    }

    @Test
    void normalizePivotsUsesEarlierAnchorDistanceToBreakSharedIndexOpposites() {
        BarSeries series = singleSeries();
        NumFactory factory = series.numFactory();
        List<SwingPivot> normalized = SwingDetectorSupport
                .normalizePivots(List.of(new SwingPivot(1, factory.hundred(), SwingPivotType.LOW),
                        new SwingPivot(2, factory.numOf(130), SwingPivotType.HIGH),
                        new SwingPivot(4, factory.numOf(110), SwingPivotType.LOW),
                        new SwingPivot(6, factory.numOf(125), SwingPivotType.HIGH),
                        new SwingPivot(6, factory.numOf(95), SwingPivotType.LOW)));

        assertThat(normalized).extracting(SwingPivot::index, SwingPivot::type)
                .containsExactly(tuple(1, SwingPivotType.LOW), tuple(2, SwingPivotType.HIGH),
                        tuple(6, SwingPivotType.LOW));
        assertThat(normalized.getLast().price()).isEqualByComparingTo(factory.numOf(95));
    }

    @Test
    void swingsFromPivotsSkipsDuplicateIndexNeighborsDefensively() {
        BarSeries series = singleSeries();
        NumFactory factory = series.numFactory();
        List<SwingPivot> pivots = List.of(new SwingPivot(1, factory.hundred(), SwingPivotType.LOW),
                new SwingPivot(1, factory.numOf(120), SwingPivotType.HIGH),
                new SwingPivot(4, factory.numOf(105), SwingPivotType.LOW));

        assertThat(SwingDetectorSupport.swingsFromPivots(pivots, ElliottDegree.PRIMARY)).singleElement()
                .satisfies(swing -> {
                    assertThat(swing.fromIndex()).isEqualTo(1);
                    assertThat(swing.toIndex()).isEqualTo(4);
                });
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
