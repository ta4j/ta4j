/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

class ElliottScenarioTimeStopRuleTest {

    private BarSeries series;
    private NumFactory numFactory;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        series = ElliottScenarioRuleTestSupport.buildSeries(numFactory);
    }

    @Test
    void timeStopRuleFiresAfterDuration() {
        ElliottScenario scenario = ElliottScenarioRuleTestSupport.buildShortWaveScenario(numFactory);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series, set);

        ElliottScenarioTimeStopRule rule = new ElliottScenarioTimeStopRule(indicator, 1.5);
        TradingRecord record = new BaseTradingRecord();
        record.enter(series.getBeginIndex());

        assertThat(rule.isSatisfied(series.getBeginIndex() + 3, record)).isTrue();
    }

    @Test
    void timeStopRuleSkipsClosedRecords() {
        ElliottScenario scenario = ElliottScenarioRuleTestSupport.buildShortWaveScenario(numFactory);
        ElliottScenarioSet set = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        Indicator<ElliottScenarioSet> indicator = ElliottScenarioRuleTestSupport.fixedScenarioIndicator(series, set);

        ElliottScenarioTimeStopRule rule = new ElliottScenarioTimeStopRule(indicator, 1.5);
        TradingRecord record = new BaseTradingRecord();
        assertThat(rule.isSatisfied(series.getBeginIndex(), record)).isFalse();
    }
}
