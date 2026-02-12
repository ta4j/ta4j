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

class ElliottScenarioAlternationRuleTest {

    private BarSeries series;
    private NumFactory numFactory;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        series = ElliottScenarioRuleTestSupport.buildSeries(numFactory);
    }

    @Test
    void alternationRuleEnforcesMinimumRatio() {
        ElliottScenario scenario = ElliottScenarioRuleTestSupport.buildBullishScenario(numFactory, ElliottPhase.WAVE5,
                0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series, set);

        ElliottScenarioAlternationRule rule = new ElliottScenarioAlternationRule(indicator, 1.5);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();

        ElliottScenarioAlternationRule strictRule = new ElliottScenarioAlternationRule(indicator, 2.5);
        assertThat(strictRule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }

    @Test
    void alternationRuleRejectsCorrectivePhase() {
        ElliottScenario corrective = ElliottScenarioRuleTestSupport.buildCorrectiveScenario(numFactory,
                ElliottPhase.CORRECTIVE_C, 0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(corrective), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series, set);

        ElliottScenarioAlternationRule rule = new ElliottScenarioAlternationRule(indicator, 1.5);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }
}
