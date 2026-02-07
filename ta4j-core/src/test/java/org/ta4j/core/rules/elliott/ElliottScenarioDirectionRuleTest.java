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
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

class ElliottScenarioDirectionRuleTest {

    private BarSeries series;
    private NumFactory numFactory;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        series = ElliottScenarioRuleTestSupport.buildSeries(numFactory);
    }

    @Test
    void directionRuleMatchesScenarioDirection() {
        ElliottScenario bullishScenario = ElliottScenarioRuleTestSupport.buildBullishScenario(numFactory,
                ElliottPhase.WAVE3, 0.8);
        ElliottScenarioSet bullishSet = ElliottScenarioSet.of(List.of(bullishScenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series,
                bullishSet);

        ElliottScenarioDirectionRule bullishRule = new ElliottScenarioDirectionRule(indicator, true);
        ElliottScenarioDirectionRule bearishRule = new ElliottScenarioDirectionRule(indicator, false);

        assertThat(bullishRule.isSatisfied(series.getEndIndex(), null)).isTrue();
        assertThat(bearishRule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }

    @Test
    void directionRuleRejectsUnknownDirection() {
        ElliottScenario unknown = ElliottScenario.builder()
                .id("unknown")
                .currentPhase(ElliottPhase.WAVE3)
                .swings(List.of())
                .confidence(ElliottScenarioRuleTestSupport.buildConfidence(numFactory, 0.8))
                .degree(ElliottDegree.PRIMARY)
                .type(ScenarioType.IMPULSE)
                .bullishDirection(null)
                .build();
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(unknown), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series, set);

        ElliottScenarioDirectionRule rule = new ElliottScenarioDirectionRule(indicator, true);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }
}
