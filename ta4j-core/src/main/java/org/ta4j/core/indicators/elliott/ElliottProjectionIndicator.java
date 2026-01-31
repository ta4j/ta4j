/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import static org.ta4j.core.num.NaN.NaN;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Calculates Fibonacci-based price projections for Elliott wave structures.
 *
 * <p>
 * Returns the primary target price based on the current wave phase. The
 * projection uses standard Fibonacci relationships:
 * <ul>
 * <li>Wave 3 target: 1.618 extension of Wave 1 from Wave 2 terminus</li>
 * <li>Wave 5 target: 1.0 extension of Wave 1 from Wave 4 terminus</li>
 * <li>Wave C target: A = C equality from Wave B terminus</li>
 * </ul>
 *
 * @since 0.22.0
 */
public class ElliottProjectionIndicator extends CachedIndicator<Num> {

    private final ElliottScenarioIndicator scenarioIndicator;

    /**
     * Creates a projection indicator from a scenario indicator.
     *
     * @param scenarioIndicator source of scenario data
     * @since 0.22.0
     */
    public ElliottProjectionIndicator(final ElliottScenarioIndicator scenarioIndicator) {
        super(requireSeries(scenarioIndicator));
        this.scenarioIndicator = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator");
    }

    private static BarSeries requireSeries(final ElliottScenarioIndicator scenarioIndicator) {
        final BarSeries series = Objects.requireNonNull(scenarioIndicator, "scenarioIndicator").getBarSeries();
        if (series == null) {
            throw new IllegalArgumentException("Scenario indicator must expose a backing series");
        }
        return series;
    }

    @Override
    protected Num calculate(final int index) {
        return scenarioIndicator.primaryScenario(index)
                .map(ElliottScenario::primaryTarget)
                .filter(Num::isValid)
                .orElse(NaN);
    }

    @Override
    public int getCountOfUnstableBars() {
        return scenarioIndicator.getCountOfUnstableBars();
    }

    /**
     * Gets all Fibonacci targets for the primary scenario.
     *
     * @param index bar index
     * @return list of all Fibonacci-based targets
     * @since 0.22.0
     */
    public List<Num> allTargets(final int index) {
        return scenarioIndicator.primaryScenario(index).map(ElliottScenario::fibonacciTargets).orElse(List.of());
    }

    /**
     * Calculates projection targets for a given swing sequence and phase.
     *
     * @param swings the swing sequence
     * @param phase  current Elliott phase
     * @return list of Fibonacci-based targets
     * @since 0.22.0
     */
    public List<Num> calculateTargets(final List<ElliottSwing> swings, final ElliottPhase phase) {
        if (swings == null || swings.isEmpty() || phase == null) {
            return List.of();
        }

        final NumFactory factory = getBarSeries().numFactory();

        if (phase.isImpulse()) {
            return calculateImpulseTargets(swings, phase, factory);
        } else if (phase.isCorrective()) {
            return calculateCorrectiveTargets(swings, phase, factory);
        }

        return List.of();
    }

    private List<Num> calculateImpulseTargets(final List<ElliottSwing> swings, final ElliottPhase phase,
            final NumFactory factory) {
        final List<Num> targets = new ArrayList<>();

        if (swings.isEmpty()) {
            return targets;
        }

        final ElliottSwing wave1 = swings.get(0);
        final Num wave1Amp = wave1.amplitude();
        final boolean bullish = wave1.isRising();

        // Wave 3 projections (from wave 2 end)
        if (phase == ElliottPhase.WAVE2 && swings.size() >= 2) {
            final Num wave2End = swings.get(1).toPrice();

            // 1.618 extension (most common)
            targets.add(projectFromBase(wave2End, wave1Amp, factory.numOf(1.618), bullish));
            // 2.618 extension (extended wave 3)
            targets.add(projectFromBase(wave2End, wave1Amp, factory.numOf(2.618), bullish));
            // 1.0 extension (minimum)
            targets.add(projectFromBase(wave2End, wave1Amp, factory.numOf(1.0), bullish));
        }

        // Wave 5 projections (from wave 4 end)
        if (phase == ElliottPhase.WAVE4 && swings.size() >= 4) {
            final Num wave4End = swings.get(3).toPrice();

            // 1.0 extension (equality with wave 1)
            targets.add(projectFromBase(wave4End, wave1Amp, factory.numOf(1.0), bullish));
            // 0.618 extension (truncated wave 5)
            targets.add(projectFromBase(wave4End, wave1Amp, factory.numOf(0.618), bullish));
            // 1.618 extension (extended wave 5)
            targets.add(projectFromBase(wave4End, wave1Amp, factory.numOf(1.618), bullish));
        }

        return targets;
    }

    private List<Num> calculateCorrectiveTargets(final List<ElliottSwing> swings, final ElliottPhase phase,
            final NumFactory factory) {
        final List<Num> targets = new ArrayList<>();

        if (swings.isEmpty()) {
            return targets;
        }

        final ElliottSwing waveA = swings.get(0);
        final Num waveAAmp = waveA.amplitude();
        // Corrective direction is opposite to wave A
        final boolean cDown = waveA.isRising();

        // Wave C projections (from wave B end)
        if (phase == ElliottPhase.CORRECTIVE_B && swings.size() >= 2) {
            final Num waveBEnd = swings.get(1).toPrice();

            // A = C equality (most common)
            targets.add(projectFromBase(waveBEnd, waveAAmp, factory.numOf(1.0), !cDown));
            // 1.618 extension
            targets.add(projectFromBase(waveBEnd, waveAAmp, factory.numOf(1.618), !cDown));
            // 0.618 extension (truncated C)
            targets.add(projectFromBase(waveBEnd, waveAAmp, factory.numOf(0.618), !cDown));
        }

        return targets;
    }

    private Num projectFromBase(final Num base, final Num amplitude, final Num multiplier, final boolean additive) {
        final Num projection = amplitude.multipliedBy(multiplier);
        return additive ? base.plus(projection) : base.minus(projection);
    }
}
