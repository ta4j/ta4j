/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ConfidenceFactorResultTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    void factoryMethodAndWeightOverloadsUseNumWeights() {
        Num score = NUM_FACTORY.numOf(0.7);
        ConfidenceFactorResult base = ConfidenceFactorResult.of("Factor", ConfidenceFactorCategory.OTHER, score,
                Map.of(), "summary");

        assertThat(base.weight()).isEqualByComparingTo(NUM_FACTORY.zero());

        ConfidenceFactorResult weightedNum = base.withWeight(NUM_FACTORY.numOf(0.4));
        ConfidenceFactorResult weightedDouble = base.withWeight(0.25);

        assertThat(weightedNum.weight()).isEqualByComparingTo(NUM_FACTORY.numOf(0.4));
        assertThat(weightedDouble.weight()).isEqualByComparingTo(NUM_FACTORY.numOf(0.25));
    }

    @Test
    void constructorRejectsInvalidWeightValues() {
        Num score = NUM_FACTORY.numOf(0.7);
        assertThrows(IllegalArgumentException.class, () -> new ConfidenceFactorResult("Factor",
                ConfidenceFactorCategory.OTHER, score, NUM_FACTORY.minusOne(), Map.of(), "summary"));
        assertThrows(IllegalArgumentException.class, () -> new ConfidenceFactorResult("Factor",
                ConfidenceFactorCategory.OTHER, score, NUM_FACTORY.numOf(Double.NaN), Map.of(), "summary"));
    }
}
