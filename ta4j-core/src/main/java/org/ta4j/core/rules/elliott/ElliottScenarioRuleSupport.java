/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.elliott;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.num.NaN;
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

    static Indicator<Num> targetIndicator(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final boolean bullish) {
        return scenarioValueIndicator(scenarioIndicator, scenario -> selectTarget(scenario, bullish));
    }

    static Indicator<Num> stopIndicator(final Indicator<ElliottScenarioSet> scenarioIndicator) {
        return scenarioValueIndicator(scenarioIndicator, ElliottScenarioRuleSupport::selectStop);
    }

    static Indicator<Num> confidenceIndicator(final Indicator<ElliottScenarioSet> scenarioIndicator) {
        return scenarioValueIndicator(scenarioIndicator, ElliottScenario::confidenceScore);
    }

    static Indicator<Num> wave3DurationIndicator(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final Num multiplier) {
        Objects.requireNonNull(multiplier, "multiplier");
        BarSeries series = Objects.requireNonNull(scenarioIndicator.getBarSeries(), "scenarioIndicator.getBarSeries()");
        return scenarioValueIndicator(scenarioIndicator, scenario -> wave3Duration(scenario, series, multiplier));
    }

    private static Num wave3Duration(final ElliottScenario scenario, final BarSeries series, final Num multiplier) {
        List<ElliottSwing> swings = scenario.swings();
        if (swings == null || swings.size() < 3) {
            return NaN.NaN;
        }
        int wave3Bars = swings.get(2).length();
        if (wave3Bars <= 0) {
            return NaN.NaN;
        }
        return series.numFactory().numOf(wave3Bars).multipliedBy(multiplier);
    }

    private static Indicator<Num> scenarioValueIndicator(final Indicator<ElliottScenarioSet> scenarioIndicator,
            final Function<ElliottScenario, Num> extractor) {
        Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
        Objects.requireNonNull(extractor, "extractor");
        return new ScenarioValueIndicator(scenarioIndicator, extractor);
    }

    private static final class ScenarioValueIndicator implements Indicator<Num> {

        private final Indicator<ElliottScenarioSet> scenarioIndicator;
        private final Function<ElliottScenario, Num> extractor;

        private ScenarioValueIndicator(final Indicator<ElliottScenarioSet> scenarioIndicator,
                final Function<ElliottScenario, Num> extractor) {
            this.scenarioIndicator = scenarioIndicator;
            this.extractor = extractor;
        }

        @Override
        public Num getValue(final int index) {
            ElliottScenario base = baseScenario(scenarioIndicator, index);
            if (base == null) {
                return NaN.NaN;
            }
            Num value = extractor.apply(base);
            return Num.isNaNOrNull(value) ? NaN.NaN : value;
        }

        @Override
        public int getCountOfUnstableBars() {
            return scenarioIndicator.getCountOfUnstableBars();
        }

        @Override
        public BarSeries getBarSeries() {
            return scenarioIndicator.getBarSeries();
        }
    }
}
