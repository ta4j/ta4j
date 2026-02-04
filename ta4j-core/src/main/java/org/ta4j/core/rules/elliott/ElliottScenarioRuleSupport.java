/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import java.util.List;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.num.Num;

final class ElliottScenarioRuleSupport {

    private ElliottScenarioRuleSupport() {
    }

    static ElliottScenario baseScenario(final Indicator<ElliottScenarioSet> scenarioIndicator, final int index) {
        ElliottScenarioSet scenarioSet = scenarioIndicator.getValue(index);
        if (scenarioSet == null) {
            return null;
        }
        return scenarioSet.base().orElse(null);
    }

    static boolean isImpulseEntryPhase(final ElliottPhase phase) {
        return phase == ElliottPhase.WAVE3 || phase == ElliottPhase.WAVE5;
    }

    static Num selectStop(final ElliottScenario base) {
        List<ElliottSwing> swings = base.swings();
        if (swings == null || swings.isEmpty()) {
            return base.invalidationPrice();
        }
        ElliottPhase phase = base.currentPhase();
        int correctiveIndex = phase == ElliottPhase.WAVE3 ? 1 : phase == ElliottPhase.WAVE5 ? 3 : -1;
        if (correctiveIndex >= 0 && swings.size() > correctiveIndex) {
            ElliottSwing corrective = swings.get(correctiveIndex);
            return corrective.toPrice();
        }
        return base.invalidationPrice();
    }

    static Num selectTarget(final ElliottScenario base, final boolean bullish) {
        Num selected = Num.isValid(base.primaryTarget()) ? base.primaryTarget() : null;
        List<Num> targets = base.fibonacciTargets();
        if (targets == null || targets.isEmpty()) {
            return selected;
        }
        for (Num target : targets) {
            if (!Num.isValid(target)) {
                continue;
            }
            if (selected == null) {
                selected = target;
                continue;
            }
            if (bullish) {
                if (target.isGreaterThan(selected)) {
                    selected = target;
                }
            } else if (target.isLessThan(selected)) {
                selected = target;
            }
        }
        return selected;
    }
}
