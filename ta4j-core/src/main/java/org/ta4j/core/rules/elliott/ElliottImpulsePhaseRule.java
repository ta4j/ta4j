/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.rules.AbstractRule;

/**
 * Checks that the base scenario is an impulse phase within the allowed set.
 *
 * @since 0.22.2
 */
public class ElliottImpulsePhaseRule extends AbstractRule {

    private final Indicator<ElliottScenarioSet> scenarioIndicator;
    private final Set<ElliottPhase> allowedPhases;

    /**
     * Creates a rule that matches impulse scenarios in the supplied phases.
     *
     * @param scenarioIndicator indicator supplying scenario sets
     * @param phases            impulse phases to accept
     * @since 0.22.2
     */
    public ElliottImpulsePhaseRule(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final ElliottPhase... phases) {
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        if (phases == null || phases.length == 0) {
            throw new IllegalArgumentException("phases must not be empty");
        }
        this.allowedPhases = EnumSet.copyOf(Arrays.asList(phases));
    }

    @Override
    public boolean isSatisfied(final int index, final TradingRecord tradingRecord) {
        ElliottScenario base = ElliottScenarioRuleSupport.baseScenario(scenarioIndicator, index);
        boolean satisfied = base != null && base.type() == ScenarioType.IMPULSE
                && allowedPhases.contains(base.currentPhase());
        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
