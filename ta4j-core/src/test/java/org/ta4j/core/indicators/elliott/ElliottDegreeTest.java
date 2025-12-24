/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.elliott;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;

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
}
