/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.indicators.elliott.ElliottFibonacciValidator;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ConfidenceProfileTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    void scoreAggregatesNumWeightedContributions() {
        ConfidenceProfile profile = new ConfidenceProfile(List.of(
                new ConfidenceProfile.WeightedFactor(new ConstantFactor("Fib", ConfidenceFactorCategory.FIBONACCI,
                        NUM_FACTORY.numOf(0.8), "Fib fit"), NUM_FACTORY.numOf(3.0)),
                new ConfidenceProfile.WeightedFactor(
                        new ConstantFactor("Time", ConfidenceFactorCategory.TIME, NUM_FACTORY.numOf(0.2), "Time fit"),
                        NUM_FACTORY.numOf(1.0))));

        ElliottConfidenceBreakdown breakdown = profile.score(context(NUM_FACTORY));

        assertThat(breakdown.confidence().overall().doubleValue()).isCloseTo(0.65, within(1.0e-12));
        assertThat(breakdown.confidence().primaryReason()).isEqualTo("Fib fit");
        assertThat(breakdown.factors()).hasSize(2);
        assertThat(breakdown.factors().get(0).weight()).isEqualByComparingTo(NUM_FACTORY.numOf(3.0));
        assertThat(breakdown.factors().get(1).weight()).isEqualByComparingTo(NUM_FACTORY.numOf(1.0));
    }

    @Test
    void weightedFactorRejectsInvalidWeight() {
        ConstantFactor factor = new ConstantFactor("Factor", ConfidenceFactorCategory.OTHER, NUM_FACTORY.one(),
                "constant");
        assertThrows(NullPointerException.class, () -> new ConfidenceProfile.WeightedFactor(factor, (Num) null));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfidenceProfile.WeightedFactor(factor, NUM_FACTORY.minusOne()));
        assertThrows(IllegalArgumentException.class,
                () -> new ConfidenceProfile.WeightedFactor(factor, NUM_FACTORY.numOf(Double.NaN)));
    }

    private static ElliottConfidenceContext context(NumFactory numFactory) {
        return new ElliottConfidenceContext(List.of(), ElliottPhase.NONE, null,
                new ElliottFibonacciValidator(numFactory), numFactory);
    }

    private static final class ConstantFactor implements ConfidenceFactor {
        private final String name;
        private final ConfidenceFactorCategory category;
        private final Num score;
        private final String summary;

        private ConstantFactor(String name, ConfidenceFactorCategory category, Num score, String summary) {
            this.name = name;
            this.category = category;
            this.score = score;
            this.summary = summary;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public ConfidenceFactorCategory category() {
            return category;
        }

        @Override
        public ConfidenceFactorResult score(ElliottConfidenceContext context) {
            return ConfidenceFactorResult.of(name, category, score, Map.of(), summary);
        }
    }
}
