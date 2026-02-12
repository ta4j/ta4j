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

class ElliottTrendBiasRuleTest {

    private BarSeries series;
    private NumFactory numFactory;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        series = ElliottScenarioRuleTestSupport.buildSeries(numFactory);
    }

    @Test
    void trendBiasRuleRequiresDirectionAndStrength() {
        ElliottScenario bullishScenario = ElliottScenarioRuleTestSupport.buildBullishScenario(numFactory,
                ElliottPhase.WAVE3, 0.8);
        ElliottScenarioSet bullishSet = ElliottScenarioSet.of(List.of(bullishScenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> bullishIndicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series,
                bullishSet);

        ElliottTrendBiasRule bullishRule = new ElliottTrendBiasRule(bullishIndicator, true, 0.2);
        assertThat(bullishRule.isSatisfied(series.getEndIndex(), null)).isTrue();

        ElliottScenario bearishScenario = ElliottScenarioRuleTestSupport.buildBearishScenario(numFactory,
                ElliottPhase.WAVE3, 0.8);
        ElliottScenarioSet neutralSet = ElliottScenarioSet.of(List.of(bullishScenario, bearishScenario),
                series.getEndIndex());
        Indicator<ElliottScenarioSet> neutralIndicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series,
                neutralSet);

        ElliottTrendBiasRule neutralRule = new ElliottTrendBiasRule(neutralIndicator, true, 0.2);
        assertThat(neutralRule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }

    @Test
    void trendBiasRuleRejectsEmptyScenarioSets() {
        ElliottScenarioSet emptySet = ElliottScenarioSet.empty(series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series,
                emptySet);

        ElliottTrendBiasRule rule = new ElliottTrendBiasRule(indicator, true, 0.2);
        assertThat(rule.isSatisfied(series.getEndIndex(), null)).isFalse();
    }
}
