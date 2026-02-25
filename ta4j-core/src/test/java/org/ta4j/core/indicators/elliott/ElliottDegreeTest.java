/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ElliottDegree}.
 */
class ElliottDegreeTest {

    @Test
    void higherDegree_shouldReturnNextHigher() {
        assertThat(ElliottDegree.INTERMEDIATE.higherDegree()).isEqualTo(ElliottDegree.PRIMARY);
        assertThat(ElliottDegree.MINOR.higherDegree()).isEqualTo(ElliottDegree.INTERMEDIATE);
        assertThat(ElliottDegree.MINUTE.higherDegree()).isEqualTo(ElliottDegree.MINOR);
    }

    @Test
    void higherDegree_atMaximum_shouldReturnSelf() {
        assertThat(ElliottDegree.GRAND_SUPERCYCLE.higherDegree()).isEqualTo(ElliottDegree.GRAND_SUPERCYCLE);
    }

    @Test
    void lowerDegree_shouldReturnNextLower() {
        assertThat(ElliottDegree.INTERMEDIATE.lowerDegree()).isEqualTo(ElliottDegree.MINOR);
        assertThat(ElliottDegree.MINOR.lowerDegree()).isEqualTo(ElliottDegree.MINUTE);
        assertThat(ElliottDegree.MINUTE.lowerDegree()).isEqualTo(ElliottDegree.MINUETTE);
    }

    @Test
    void lowerDegree_atMinimum_shouldReturnSelf() {
        assertThat(ElliottDegree.SUB_MINUETTE.lowerDegree()).isEqualTo(ElliottDegree.SUB_MINUETTE);
    }

    @Test
    void isHigherOrEqual_shouldCompareCorrectly() {
        assertThat(ElliottDegree.PRIMARY.isHigherOrEqual(ElliottDegree.INTERMEDIATE)).isTrue();
        assertThat(ElliottDegree.INTERMEDIATE.isHigherOrEqual(ElliottDegree.INTERMEDIATE)).isTrue();
        assertThat(ElliottDegree.MINOR.isHigherOrEqual(ElliottDegree.INTERMEDIATE)).isFalse();
    }

    @Test
    void isLowerOrEqual_shouldCompareCorrectly() {
        assertThat(ElliottDegree.MINOR.isLowerOrEqual(ElliottDegree.INTERMEDIATE)).isTrue();
        assertThat(ElliottDegree.INTERMEDIATE.isLowerOrEqual(ElliottDegree.INTERMEDIATE)).isTrue();
        assertThat(ElliottDegree.PRIMARY.isLowerOrEqual(ElliottDegree.INTERMEDIATE)).isFalse();
    }

    @Test
    void navigationChain_shouldWork() {
        // Navigate down two levels from INTERMEDIATE
        ElliottDegree start = ElliottDegree.INTERMEDIATE;
        ElliottDegree twoLower = start.lowerDegree().lowerDegree();
        assertThat(twoLower).isEqualTo(ElliottDegree.MINUTE);

        // Navigate back up
        ElliottDegree backUp = twoLower.higherDegree().higherDegree();
        assertThat(backUp).isEqualTo(start);
    }

    @Test
    void getRecommendedDegrees_dailyBars_shouldPreferIntermediate() {
        List<ElliottDegree> degrees = ElliottDegree.getRecommendedDegrees(Duration.ofDays(1), 250);

        assertThat(degrees).containsExactly(ElliottDegree.INTERMEDIATE, ElliottDegree.MINOR, ElliottDegree.PRIMARY);
    }

    @Test
    void getRecommendedDegrees_hourlyBars_shouldPreferMinuette() {
        List<ElliottDegree> degrees = ElliottDegree.getRecommendedDegrees(Duration.ofHours(1), 500);

        assertThat(degrees).containsExactly(ElliottDegree.MINUETTE, ElliottDegree.MINUTE);
    }

    @Test
    void getRecommendedDegrees_shouldFallbackWhenHistoryIsSparse() {
        List<ElliottDegree> degrees = ElliottDegree.getRecommendedDegrees(Duration.ofDays(1), 5);

        assertThat(degrees).containsExactly(ElliottDegree.MINUTE);
    }

    @Test
    void getRecommendedDegrees_shouldRejectInvalidInputs() {
        assertThrows(NullPointerException.class, () -> ElliottDegree.getRecommendedDegrees(null, 10));
        assertThrows(IllegalArgumentException.class, () -> ElliottDegree.getRecommendedDegrees(Duration.ZERO, 10));
        assertThrows(IllegalArgumentException.class, () -> ElliottDegree.getRecommendedDegrees(Duration.ofDays(1), 0));
        assertThrows(IllegalArgumentException.class,
                () -> ElliottDegree.getRecommendedDegrees(Duration.ofDays(-1), 10));
    }

    @Test
    void historyFitScore_shouldRejectInvalidInputs() {
        assertThrows(NullPointerException.class, () -> ElliottDegree.PRIMARY.historyFitScore(null, 10));
        assertThrows(IllegalArgumentException.class, () -> ElliottDegree.PRIMARY.historyFitScore(Duration.ZERO, 10));
        assertThrows(IllegalArgumentException.class,
                () -> ElliottDegree.PRIMARY.historyFitScore(Duration.ofDays(1), 0));
    }

    @Test
    void recommendedHistoryDays_shouldExposeMaxFlag() {
        ElliottDegree.RecommendedHistory primary = ElliottDegree.PRIMARY.recommendedHistoryDays();
        assertThat(primary.minDays()).isGreaterThan(0.0);
        assertThat(primary.hasMax()).isTrue();
        assertThat(primary.maxDays()).isGreaterThan(primary.minDays());

        ElliottDegree.RecommendedHistory grandSupercycle = ElliottDegree.GRAND_SUPERCYCLE.recommendedHistoryDays();
        assertThat(grandSupercycle.minDays()).isGreaterThan(0.0);
        assertThat(grandSupercycle.hasMax()).isFalse();
        assertThat(grandSupercycle.maxDays()).isEqualTo(0.0);
    }

    @Test
    void recommendedHistoryConstructor_shouldRejectBoundedMaxBelowMin() {
        assertThrows(IllegalArgumentException.class, () -> new ElliottDegree.RecommendedHistory(10.0, 9.0));
    }

    @Test
    void recommendedHistoryConstructor_shouldAllowUnboundedRange() {
        ElliottDegree.RecommendedHistory history = new ElliottDegree.RecommendedHistory(10.0, 0.0);

        assertThat(history.hasMax()).isFalse();
        assertThat(history.maxDays()).isEqualTo(0.0);
    }

    @Test
    void historyFitScore_shouldReturnOneWhenWithinRange() {
        double score = ElliottDegree.PRIMARY.historyFitScore(Duration.ofDays(1), 1000);
        assertThat(score).isCloseTo(1.0, within(1e-12));
    }

    @Test
    void historyFitScore_shouldDecreaseWhenOutsideRange() {
        double tooShort = ElliottDegree.PRIMARY.historyFitScore(Duration.ofDays(1), 200);
        assertThat(tooShort).isCloseTo(0.5, within(1e-12));

        double tooLong = ElliottDegree.PRIMARY.historyFitScore(Duration.ofDays(1), 2000);
        assertThat(tooLong).isCloseTo(0.5, within(1e-12));
    }
}
