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
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class ElliottScenarioRiskRewardRuleTest {

    private BarSeries series;
    private NumFactory numFactory;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        series = ElliottScenarioRuleTestSupport.buildSeries(numFactory);
    }

    @Test
    void riskRewardRuleEvaluatesTargetsAndStops() {
        ElliottScenario scenario = ElliottScenarioRuleTestSupport.buildBullishScenario(numFactory, ElliottPhase.WAVE3,
                0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series, set);
        Indicator<Num> close = ElliottScenarioRuleTestSupport.constantIndicator(series, numFactory.numOf(150));

        ElliottScenarioRiskRewardRule rule = new ElliottScenarioRiskRewardRule(indicator, close, true, 3.0);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();

        ElliottScenarioRiskRewardRule strictRule = new ElliottScenarioRiskRewardRule(indicator, close, true, 6.0);
        assertThat(strictRule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }

    @Test
    void riskRewardRuleSupportsBearishScenarios() {
        ElliottScenario scenario = ElliottScenarioRuleTestSupport.buildBearishScenario(numFactory, ElliottPhase.WAVE3,
                0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series, set);
        Indicator<Num> close = ElliottScenarioRuleTestSupport.constantIndicator(series, numFactory.numOf(140));

        ElliottScenarioRiskRewardRule rule = new ElliottScenarioRiskRewardRule(indicator, close, false, 2.0);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();
    }
}
