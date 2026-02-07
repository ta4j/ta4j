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

class ElliottScenarioTargetReachedRuleTest {

    private BarSeries series;
    private NumFactory numFactory;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        series = ElliottScenarioRuleTestSupport.buildSeries(numFactory);
    }

    @Test
    void targetReachedRuleDetectsHit() {
        ElliottScenario scenario = ElliottScenarioRuleTestSupport.buildBullishScenario(numFactory, ElliottPhase.WAVE5,
                0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series, set);
        Indicator<Num> close = ElliottScenarioRuleTestSupport.constantIndicator(series, numFactory.numOf(240));

        ElliottScenarioTargetReachedRule rule = new ElliottScenarioTargetReachedRule(indicator, close, true);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();
    }

    @Test
    void targetReachedRuleDetectsBearishHit() {
        ElliottScenario scenario = ElliottScenarioRuleTestSupport.buildBearishScenario(numFactory, ElliottPhase.WAVE5,
                0.8);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series, set);
        Indicator<Num> close = ElliottScenarioRuleTestSupport.constantIndicator(series, numFactory.numOf(70));

        ElliottScenarioTargetReachedRule rule = new ElliottScenarioTargetReachedRule(indicator, close, false);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isTrue();
    }
}
