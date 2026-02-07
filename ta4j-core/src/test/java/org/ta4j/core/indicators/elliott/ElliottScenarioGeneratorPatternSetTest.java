/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceProfiles;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

class ElliottScenarioGeneratorPatternSetTest {

    @Test
    void patternSetFiltersScenarioTypes() {
        BarSeries series = new MockBarSeriesBuilder().withName("PatternSetTest").build();
        NumFactory factory = series.numFactory();
        ElliottDegree degree = ElliottDegree.PRIMARY;
        List<ElliottSwing> swings = List.of(new ElliottSwing(0, 2, factory.hundred(), factory.numOf(120), degree),
                new ElliottSwing(2, 4, factory.numOf(120), factory.numOf(110), degree),
                new ElliottSwing(4, 6, factory.numOf(110), factory.numOf(130), degree));

        ElliottScenarioGenerator impulseOnly = new ElliottScenarioGenerator(series.numFactory(), 0.0, 5,
                ConfidenceProfiles.defaultModel(series.numFactory()), PatternSet.of(ScenarioType.IMPULSE));
        ElliottScenarioSet impulseSet = impulseOnly.generate(swings, degree, null, 6);

        assertThat(impulseSet.isEmpty()).isFalse();
        assertThat(impulseSet.all()).allMatch(scenario -> scenario.type() == ScenarioType.IMPULSE);

        ElliottScenarioGenerator correctiveOnly = new ElliottScenarioGenerator(series.numFactory(), 0.0, 5,
                ConfidenceProfiles.defaultModel(series.numFactory()),
                PatternSet.of(ScenarioType.CORRECTIVE_ZIGZAG, ScenarioType.CORRECTIVE_FLAT));
        ElliottScenarioSet correctiveSet = correctiveOnly.generate(swings, degree, null, 6);

        assertThat(correctiveSet.isEmpty()).isFalse();
        assertThat(correctiveSet.all()).allMatch(scenario -> scenario.type().isCorrective());
    }

}
