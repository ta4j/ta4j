/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.rules.AbstractRule;

/**
 * Checks that wave 2/4 alternation meets a minimum duration ratio.
 *
 * @since 0.22.2
 */
public class ElliottScenarioAlternationRule extends AbstractRule {

    private final Indicator<ElliottScenarioSet> scenarioIndicator;
    private final double minAlternationRatio;

    /**
     * Creates an alternation rule.
     *
     * @param scenarioIndicator   indicator supplying scenario sets
     * @param minAlternationRatio minimum duration ratio (>= 1.0)
     * @since 0.22.2
     */
    public ElliottScenarioAlternationRule(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final double minAlternationRatio) {
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        if (minAlternationRatio < 1.0) {
            throw new IllegalArgumentException("minAlternationRatio must be >= 1.0");
        }
        this.minAlternationRatio = minAlternationRatio;
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        ElliottScenario base = ElliottScenarioRuleSupport.baseScenario(scenarioIndicator, index);
        if (base == null) {
            traceIsSatisfied(index, false);
            return false;
        }
        List<ElliottSwing> swings = base.swings();
        if (swings == null || swings.size() < 4) {
            traceIsSatisfied(index, false);
            return false;
        }
        ElliottPhase phase = base.currentPhase();
        if (phase != null && !phase.isImpulse()) {
            traceIsSatisfied(index, false);
            return false;
        }
        ElliottSwing wave2 = swings.get(1);
        ElliottSwing wave4 = swings.get(3);
        int wave2Bars = wave2.length();
        int wave4Bars = wave4.length();
        if (wave2Bars <= 0 || wave4Bars <= 0) {
            traceIsSatisfied(index, false);
            return false;
        }
        double ratio = (double) wave4Bars / wave2Bars;
        if (Double.isNaN(ratio) || ratio <= 0.0) {
            traceIsSatisfied(index, false);
            return false;
        }
        double normalized = ratio >= 1.0 ? ratio : 1.0 / ratio;
        boolean satisfied = normalized >= minAlternationRatio;
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
