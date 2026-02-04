/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.rules.AbstractRule;

/**
 * Checks whether a trade has exceeded the maximum wave duration window.
 *
 * @since 0.22.2
 */
public class ElliottScenarioTimeStopRule extends AbstractRule {

    private final Indicator<ElliottScenarioSet> scenarioIndicator;
    private final double maxWaveDurationMultiplier;

    /**
     * Creates a time-stop rule.
     *
     * @param scenarioIndicator         indicator supplying scenario sets
     * @param maxWaveDurationMultiplier multiplier applied to wave 3 duration
     * @since 0.22.2
     */
    public ElliottScenarioTimeStopRule(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final double maxWaveDurationMultiplier) {
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        if (maxWaveDurationMultiplier <= 0.0) {
            throw new IllegalArgumentException("maxWaveDurationMultiplier must be positive");
        }
        this.maxWaveDurationMultiplier = maxWaveDurationMultiplier;
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        if (tradingRecord == null || tradingRecord.isClosed()) {
            traceIsSatisfied(index, false);
            return false;
        }
        Trade entry = tradingRecord.getCurrentPosition() == null ? null : tradingRecord.getCurrentPosition().getEntry();
        if (entry == null) {
            traceIsSatisfied(index, false);
            return false;
        }
        int barsOpen = index - entry.getIndex();
        if (barsOpen <= 0) {
            traceIsSatisfied(index, false);
            return false;
        }
        ElliottScenario base = ElliottScenarioRuleSupport.baseScenario(scenarioIndicator, index);
        if (base == null) {
            traceIsSatisfied(index, false);
            return false;
        }
        List<ElliottSwing> swings = base.swings();
        if (swings.size() < 3) {
            traceIsSatisfied(index, false);
            return false;
        }
        int wave3Bars = swings.get(2).length();
        if (wave3Bars <= 0) {
            traceIsSatisfied(index, false);
            return false;
        }
        double maxBars = wave3Bars * maxWaveDurationMultiplier;
        boolean satisfied = barsOpen >= Math.ceil(maxBars);
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
