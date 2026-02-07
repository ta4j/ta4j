/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

class ScenarioTypeConfidenceModelTest {

    @Test
    void profilesApplyDifferentWeightsByScenarioType() {
        BarSeries series = new MockBarSeriesBuilder().withName("ProfileTest").build();
        Num high = series.numFactory().numOf(0.9);
        Num low = series.numFactory().numOf(0.2);

        ConfidenceProfile highProfile = new ConfidenceProfile(
                List.of(new ConfidenceProfile.WeightedFactor(new ConstantFactor(high), 1.0)));
        ConfidenceProfile lowProfile = new ConfidenceProfile(
                List.of(new ConfidenceProfile.WeightedFactor(new ConstantFactor(low), 1.0)));

        ScenarioTypeConfidenceModel model = ScenarioTypeConfidenceModel.builder(series.numFactory())
                .defaultProfile(highProfile)
                .overrideProfile(ScenarioType.CORRECTIVE_ZIGZAG, lowProfile)
                .build();

        ElliottConfidenceBreakdown impulse = model.score(List.of(), ElliottPhase.NONE, null, ScenarioType.IMPULSE);
        ElliottConfidenceBreakdown corrective = model.score(List.of(), ElliottPhase.NONE, null,
                ScenarioType.CORRECTIVE_ZIGZAG);

        assertThat(impulse.confidence().overall().doubleValue())
                .isGreaterThan(corrective.confidence().overall().doubleValue());
    }

    private static final class ConstantFactor implements ConfidenceFactor {

        private final Num score;

        private ConstantFactor(final Num score) {
            this.score = score;
        }

        @Override
        public String name() {
            return "Constant";
        }

        @Override
        public ConfidenceFactorCategory category() {
            return ConfidenceFactorCategory.OTHER;
        }

        @Override
        public ConfidenceFactorResult score(final ElliottConfidenceContext context) {
            return ConfidenceFactorResult.of(name(), category(), score, Map.of(), "Constant score");
        }
    }
}
