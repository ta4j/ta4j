/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

class ElliottImpulsePhaseRuleTest {

    private BarSeries series;
    private NumFactory numFactory;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        series = ElliottScenarioRuleTestSupport.buildSeries(numFactory);
    }

    @Test
    void impulsePhaseRuleMatchesConfiguredPhases() {
        ElliottScenario scenario = ElliottScenarioRuleTestSupport.buildBullishScenario(numFactory, ElliottPhase.WAVE3,
                0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series, set);

        ElliottImpulsePhaseRule rule = new ElliottImpulsePhaseRule(indicator, ElliottPhase.WAVE3, ElliottPhase.WAVE5);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();

        ElliottScenario wave2Scenario = ElliottScenarioRuleTestSupport.buildBullishScenario(numFactory,
                ElliottPhase.WAVE2, 0.8);
        ElliottScenarioSet wave2Set = ElliottScenarioSet.of(List.of(wave2Scenario), series.getEndIndex());
        ElliottImpulsePhaseRule wave2Rule = new ElliottImpulsePhaseRule(
                ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series, wave2Set), ElliottPhase.WAVE3,
                ElliottPhase.WAVE5);
        assertThat(wave2Rule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }

    @Test
    void impulsePhaseRuleRejectsCorrectiveScenarios() {
        ElliottScenario corrective = ElliottScenarioRuleTestSupport.buildCorrectiveScenario(numFactory,
                ElliottPhase.CORRECTIVE_A, 0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(corrective), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series, set);

        ElliottImpulsePhaseRule rule = new ElliottImpulsePhaseRule(indicator, ElliottPhase.WAVE3, ElliottPhase.WAVE5);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }
}
