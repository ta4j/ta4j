/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.ta4j.core.indicators.elliott.confidence.ConfidenceModel;
import org.ta4j.core.indicators.elliott.confidence.ConfidenceProfiles;
import org.ta4j.core.indicators.elliott.confidence.ElliottConfidenceBreakdown;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Generates multiple alternative Elliott wave interpretations for a swing
 * sequence.
 *
 * <p>
 * The generator explores different starting points, pattern interpretations,
 * and wave assignments to produce a ranked set of plausible scenarios. Each
 * scenario receives a confidence score, and low-confidence interpretations are
 * pruned from the final output.
 *
 * @since 0.22.0
 */
public final class ElliottScenarioGenerator {

    /** Default minimum confidence threshold for retaining scenarios. */
    public static final double DEFAULT_MIN_CONFIDENCE = 0.15;

    /** Maximum number of scenarios to retain after pruning. */
    public static final int DEFAULT_MAX_SCENARIOS = 5;

    private final NumFactory numFactory;
    private final ElliottFibonacciValidator fibValidator;
    private final ConfidenceModel confidenceModel;
    private final PatternSet patternSet;
    private final double minConfidence;
    private final int maxScenarios;
    private final AtomicInteger scenarioCounter = new AtomicInteger(0);

    /**
     * Creates a generator with default settings.
     *
     * @param numFactory factory for creating numeric values
     * @since 0.22.0
     */
    public ElliottScenarioGenerator(final NumFactory numFactory) {
        this(numFactory, DEFAULT_MIN_CONFIDENCE, DEFAULT_MAX_SCENARIOS, ConfidenceProfiles.defaultModel(numFactory),
                PatternSet.all());
    }

    /**
     * Creates a generator with custom pruning thresholds.
     *
     * @param numFactory    factory for creating numeric values
     * @param minConfidence minimum confidence to retain a scenario (0.0 - 1.0)
     * @param maxScenarios  maximum number of scenarios to return
     * @since 0.22.0
     */
    public ElliottScenarioGenerator(final NumFactory numFactory, final double minConfidence, final int maxScenarios) {
        this(numFactory, minConfidence, maxScenarios, ConfidenceProfiles.defaultModel(numFactory), PatternSet.all());
    }

    /**
     * Creates a generator with custom pruning thresholds and scoring model.
     *
     * @param numFactory      factory for creating numeric values
     * @param minConfidence   minimum confidence to retain a scenario (0.0 - 1.0)
     * @param maxScenarios    maximum number of scenarios to return
     * @param confidenceModel confidence model used to score scenarios
     * @param patternSet      enabled pattern set
     * @since 0.22.2
     */
    public ElliottScenarioGenerator(final NumFactory numFactory, final double minConfidence, final int maxScenarios,
            final ConfidenceModel confidenceModel, final PatternSet patternSet) {
        this.numFactory = Objects.requireNonNull(numFactory, "numFactory");
        this.fibValidator = new ElliottFibonacciValidator(numFactory);
        this.confidenceModel = Objects.requireNonNull(confidenceModel, "confidenceModel");
        this.patternSet = Objects.requireNonNull(patternSet, "patternSet");
        this.minConfidence = minConfidence;
        this.maxScenarios = maxScenarios;
    }

    /**
     * Generates all valid scenarios for the given swing sequence.
     *
     * @param swings  the swing sequence to analyze
     * @param degree  the wave degree for generated scenarios
     * @param channel optional channel for scoring (may be null)
     * @return scenario set containing ranked alternatives
     * @since 0.22.0
     */
    public ElliottScenarioSet generate(final List<ElliottSwing> swings, final ElliottDegree degree,
            final ElliottChannel channel) {
        return generate(swings, degree, channel, 0);
    }

    /**
     * Generates all valid scenarios for the given swing sequence.
     *
     * @param swings   the swing sequence to analyze
     * @param degree   the wave degree for generated scenarios
     * @param channel  optional channel for scoring (may be null)
     * @param barIndex the bar index for the resulting scenario set
     * @return scenario set containing ranked alternatives
     * @since 0.22.0
     */
    public ElliottScenarioSet generate(final List<ElliottSwing> swings, final ElliottDegree degree,
            final ElliottChannel channel, final int barIndex) {
        if (swings == null || swings.isEmpty()) {
            return ElliottScenarioSet.empty(barIndex);
        }

        final List<ElliottScenario> candidates = new ArrayList<>();
        final Set<String> seenSignatures = new HashSet<>();

        // Try different starting points
        for (int startIndex = 0; startIndex < swings.size() && startIndex < 3; startIndex++) {
            final List<ElliottSwing> segment = swings.subList(startIndex, swings.size());
            if (segment.isEmpty()) {
                continue;
            }

            // Try impulse interpretation
            if (patternSet.allows(ScenarioType.IMPULSE)) {
                generateImpulseScenarios(segment, degree, channel, startIndex, candidates, seenSignatures);
            }

            // Try corrective interpretation
            if (patternSet.allows(ScenarioType.CORRECTIVE_ZIGZAG) || patternSet.allows(ScenarioType.CORRECTIVE_FLAT)
                    || patternSet.allows(ScenarioType.CORRECTIVE_TRIANGLE)
                    || patternSet.allows(ScenarioType.CORRECTIVE_COMPLEX)) {
                generateCorrectiveScenarios(segment, degree, channel, startIndex, candidates, seenSignatures);
            }
        }

        // Prune and rank scenarios
        final List<ElliottScenario> pruned = prune(candidates);

        return ElliottScenarioSet.of(pruned, barIndex);
    }

    private void generateImpulseScenarios(final List<ElliottSwing> swings, final ElliottDegree degree,
            final ElliottChannel channel, final int startIndex, final List<ElliottScenario> candidates,
            final Set<String> seenSignatures) {
        if (swings.isEmpty()) {
            return;
        }

        // Try to identify impulse waves 1-5
        for (int waveCount = 1; waveCount <= Math.min(5, swings.size()); waveCount++) {
            final List<ElliottSwing> impulseSwings = swings.subList(0, waveCount);
            final ElliottPhase phase = determineImpulsePhase(impulseSwings);

            if (phase == ElliottPhase.NONE) {
                continue;
            }

            if (!validateImpulseStructure(impulseSwings, phase)) {
                continue;
            }

            final String signature = createSignature(ScenarioType.IMPULSE, phase, startIndex);
            if (seenSignatures.contains(signature)) {
                continue;
            }
            seenSignatures.add(signature);

            final ElliottConfidenceBreakdown breakdown = confidenceModel.score(impulseSwings, phase, channel,
                    ScenarioType.IMPULSE);
            final ElliottConfidence confidence = breakdown.confidence();

            if (confidence.overall().doubleValue() < minConfidence) {
                continue;
            }

            final Num invalidation = calculateImpulseInvalidation(impulseSwings, phase);
            final List<Num> targets = calculateImpulseTargets(impulseSwings, phase);
            final Num primaryTarget = targets.isEmpty() ? numFactory.zero() : targets.get(0);

            final ElliottScenario scenario = ElliottScenario.builder()
                    .id(generateId("impulse"))
                    .currentPhase(phase)
                    .swings(impulseSwings)
                    .confidence(confidence)
                    .degree(degree)
                    .invalidationPrice(invalidation)
                    .primaryTarget(primaryTarget)
                    .fibonacciTargets(targets)
                    .type(ScenarioType.IMPULSE)
                    .startIndex(startIndex)
                    .build();

            candidates.add(scenario);
        }
    }

    private void generateCorrectiveScenarios(final List<ElliottSwing> swings, final ElliottDegree degree,
            final ElliottChannel channel, final int startIndex, final List<ElliottScenario> candidates,
            final Set<String> seenSignatures) {
        if (swings.isEmpty()) {
            return;
        }

        // Try zigzag (A-B-C with C exceeding A)
        if (patternSet.allows(ScenarioType.CORRECTIVE_ZIGZAG) && swings.size() >= 1) {
            generateZigzagScenario(swings, degree, channel, startIndex, candidates, seenSignatures);
        }

        // Try flat (A-B-C with B retracing most of A)
        if (patternSet.allows(ScenarioType.CORRECTIVE_FLAT) && swings.size() >= 2) {
            generateFlatScenario(swings, degree, channel, startIndex, candidates, seenSignatures);
        }
    }

    private void generateZigzagScenario(final List<ElliottSwing> swings, final ElliottDegree degree,
            final ElliottChannel channel, final int startIndex, final List<ElliottScenario> candidates,
            final Set<String> seenSignatures) {

        for (int waveCount = 1; waveCount <= Math.min(3, swings.size()); waveCount++) {
            final List<ElliottSwing> corrSwings = swings.subList(0, waveCount);
            final ElliottPhase phase = determineCorrectivePhase(corrSwings);

            if (phase == ElliottPhase.NONE) {
                continue;
            }

            final String signature = createSignature(ScenarioType.CORRECTIVE_ZIGZAG, phase, startIndex);
            if (seenSignatures.contains(signature)) {
                continue;
            }
            seenSignatures.add(signature);

            final ElliottConfidenceBreakdown breakdown = confidenceModel.score(corrSwings, phase, channel,
                    ScenarioType.CORRECTIVE_ZIGZAG);
            final ElliottConfidence confidence = breakdown.confidence();

            if (confidence.overall().doubleValue() < minConfidence) {
                continue;
            }

            final Num invalidation = calculateCorrectiveInvalidation(corrSwings, phase);
            final List<Num> targets = calculateCorrectiveTargets(corrSwings, phase);
            final Num primaryTarget = targets.isEmpty() ? numFactory.zero() : targets.get(0);

            final ElliottScenario scenario = ElliottScenario.builder()
                    .id(generateId("zigzag"))
                    .currentPhase(phase)
                    .swings(corrSwings)
                    .confidence(confidence)
                    .degree(degree)
                    .invalidationPrice(invalidation)
                    .primaryTarget(primaryTarget)
                    .fibonacciTargets(targets)
                    .type(ScenarioType.CORRECTIVE_ZIGZAG)
                    .startIndex(startIndex)
                    .build();

            candidates.add(scenario);
        }
    }

    private void generateFlatScenario(final List<ElliottSwing> swings, final ElliottDegree degree,
            final ElliottChannel channel, final int startIndex, final List<ElliottScenario> candidates,
            final Set<String> seenSignatures) {

        // Flat requires wave B to retrace at least 78.6% of wave A
        if (swings.size() < 2) {
            return;
        }

        final ElliottSwing waveA = swings.get(0);
        final ElliottSwing waveB = swings.get(1);

        // Flat patterns require wave B to retrace at least 78.6% of wave A.
        // The validator handles zero-amplitude checks internally.
        if (!fibValidator.isWaveBFlatRetracementValid(waveA, waveB)) {
            return;
        }

        for (int waveCount = 2; waveCount <= Math.min(3, swings.size()); waveCount++) {
            final List<ElliottSwing> corrSwings = swings.subList(0, waveCount);
            final ElliottPhase phase = determineCorrectivePhase(corrSwings);

            if (phase == ElliottPhase.NONE) {
                continue;
            }

            final String signature = createSignature(ScenarioType.CORRECTIVE_FLAT, phase, startIndex);
            if (seenSignatures.contains(signature)) {
                continue;
            }
            seenSignatures.add(signature);

            final ElliottConfidenceBreakdown breakdown = confidenceModel.score(corrSwings, phase, channel,
                    ScenarioType.CORRECTIVE_FLAT);
            final ElliottConfidence confidence = breakdown.confidence();

            if (confidence.overall().doubleValue() < minConfidence) {
                continue;
            }

            final Num invalidation = calculateCorrectiveInvalidation(corrSwings, phase);
            final List<Num> targets = calculateCorrectiveTargets(corrSwings, phase);
            final Num primaryTarget = targets.isEmpty() ? numFactory.zero() : targets.get(0);

            final ElliottScenario scenario = ElliottScenario.builder()
                    .id(generateId("flat"))
                    .currentPhase(phase)
                    .swings(corrSwings)
                    .confidence(confidence)
                    .degree(degree)
                    .invalidationPrice(invalidation)
                    .primaryTarget(primaryTarget)
                    .fibonacciTargets(targets)
                    .type(ScenarioType.CORRECTIVE_FLAT)
                    .startIndex(startIndex)
                    .build();

            candidates.add(scenario);
        }
    }

    private ElliottPhase determineImpulsePhase(final List<ElliottSwing> swings) {
        if (swings.isEmpty()) {
            return ElliottPhase.NONE;
        }

        return switch (swings.size()) {
        case 1 -> ElliottPhase.WAVE1;
        case 2 -> ElliottPhase.WAVE2;
        case 3 -> ElliottPhase.WAVE3;
        case 4 -> ElliottPhase.WAVE4;
        default -> ElliottPhase.WAVE5;
        };
    }

    private ElliottPhase determineCorrectivePhase(final List<ElliottSwing> swings) {
        if (swings.isEmpty()) {
            return ElliottPhase.NONE;
        }

        return switch (swings.size()) {
        case 1 -> ElliottPhase.CORRECTIVE_A;
        case 2 -> ElliottPhase.CORRECTIVE_B;
        default -> ElliottPhase.CORRECTIVE_C;
        };
    }

    private boolean validateImpulseStructure(final List<ElliottSwing> swings, final ElliottPhase phase) {
        if (swings.isEmpty()) {
            return false;
        }

        // Validate that phase is an impulse phase
        if (phase == null || !phase.isImpulse()) {
            return false;
        }

        // Basic direction alternation check
        for (int i = 1; i < swings.size(); i++) {
            if (swings.get(i).isRising() == swings.get(i - 1).isRising()) {
                return false; // Consecutive swings should alternate
            }
        }

        // Wave 2 should not retrace below wave 1 start (for bullish)
        if (swings.size() >= 2) {
            final ElliottSwing wave1 = swings.get(0);
            final ElliottSwing wave2 = swings.get(1);
            if (wave1.isRising()) {
                // Bullish: wave 2 end should not go below wave 1 start
                if (wave2.toPrice().isLessThan(wave1.fromPrice())) {
                    return false;
                }
            } else {
                // Bearish: wave 2 end should not go above wave 1 start
                if (wave2.toPrice().isGreaterThan(wave1.fromPrice())) {
                    return false;
                }
            }
        }

        // Wave 4 should not overlap wave 1 territory
        if (swings.size() >= 4) {
            final ElliottSwing wave1 = swings.get(0);
            final ElliottSwing wave4 = swings.get(3);
            if (wave1.isRising()) {
                // Bullish: wave 4 low should not go below wave 1 high
                if (wave4.toPrice().isLessThan(wave1.toPrice())) {
                    return false;
                }
            } else {
                // Bearish: wave 4 high should not go above wave 1 low
                if (wave4.toPrice().isGreaterThan(wave1.toPrice())) {
                    return false;
                }
            }
        }

        return true;
    }

    private Num calculateImpulseInvalidation(final List<ElliottSwing> swings, final ElliottPhase phase) {
        if (swings.isEmpty()) {
            return numFactory.zero();
        }

        // Validate that phase is an impulse phase
        if (phase == null || !phase.isImpulse()) {
            return numFactory.zero();
        }

        // Invalidation is the start of wave 1 for waves 2-5
        return swings.get(0).fromPrice();
    }

    private Num calculateCorrectiveInvalidation(final List<ElliottSwing> swings, final ElliottPhase phase) {
        if (swings.isEmpty()) {
            return numFactory.zero();
        }

        // Validate that phase is a corrective phase
        if (phase == null || !phase.isCorrective()) {
            return numFactory.zero();
        }

        final ElliottSwing waveA = swings.get(0);
        // For corrective, invalidation is typically above/below wave A start
        return waveA.fromPrice();
    }

    private List<Num> calculateImpulseTargets(final List<ElliottSwing> swings, final ElliottPhase phase) {
        final List<Num> targets = new ArrayList<>();

        if (swings.isEmpty()) {
            return targets;
        }

        // Validate that phase is an impulse phase
        if (phase == null || !phase.isImpulse()) {
            return targets;
        }

        final ElliottSwing wave1 = swings.get(0);
        final Num wave1Amp = wave1.amplitude();
        final boolean bullish = wave1.isRising();

        // Calculate targets based on current phase
        if (phase.impulseIndex() >= 2 && swings.size() >= 2) {
            // Wave 3 target: 1.618 extension of wave 1 from wave 2 end
            final Num wave2End = swings.get(1).toPrice();
            final Num target3 = bullish ? wave2End.plus(wave1Amp.multipliedBy(numFactory.numOf(1.618)))
                    : wave2End.minus(wave1Amp.multipliedBy(numFactory.numOf(1.618)));
            targets.add(target3);
        }

        if (phase.impulseIndex() >= 4 && swings.size() >= 4) {
            // Wave 5 target: 1.0 extension of wave 1 from wave 4 end
            final Num wave4End = swings.get(3).toPrice();
            final Num target5 = bullish ? wave4End.plus(wave1Amp) : wave4End.minus(wave1Amp);
            targets.add(target5);

            // Alternative: 0.618 extension
            final Num target5Alt = bullish ? wave4End.plus(wave1Amp.multipliedBy(numFactory.numOf(0.618)))
                    : wave4End.minus(wave1Amp.multipliedBy(numFactory.numOf(0.618)));
            targets.add(target5Alt);
        }

        return targets;
    }

    private List<Num> calculateCorrectiveTargets(final List<ElliottSwing> swings, final ElliottPhase phase) {
        final List<Num> targets = new ArrayList<>();

        if (swings.isEmpty()) {
            return targets;
        }

        // Validate that phase is a corrective phase
        if (phase == null || !phase.isCorrective()) {
            return targets;
        }

        final ElliottSwing waveA = swings.get(0);
        final Num waveAAmp = waveA.amplitude();
        // Corrective wave A direction determines target calculation
        final boolean waveAFalling = !waveA.isRising();

        // Wave C target: equality with wave A
        if (phase.correctiveIndex() >= 2 && swings.size() >= 2) {
            final Num waveBEnd = swings.get(1).toPrice();
            final Num targetC = waveAFalling ? waveBEnd.minus(waveAAmp) : waveBEnd.plus(waveAAmp);
            targets.add(targetC);

            // Alternative: 1.618 extension
            final Num targetC618 = waveAFalling ? waveBEnd.minus(waveAAmp.multipliedBy(numFactory.numOf(1.618)))
                    : waveBEnd.plus(waveAAmp.multipliedBy(numFactory.numOf(1.618)));
            targets.add(targetC618);
        }

        return targets;
    }

    private List<ElliottScenario> prune(final List<ElliottScenario> candidates) {
        return candidates.stream()
                .filter(s -> s.confidenceScore().doubleValue() >= minConfidence)
                .sorted(ElliottScenarioSet.byConfidenceDescending())
                .limit(maxScenarios)
                .toList();
    }

    private String generateId(final String prefix) {
        return prefix + "-" + scenarioCounter.incrementAndGet();
    }

    private String createSignature(final ScenarioType type, final ElliottPhase phase, final int startIndex) {
        return type.name() + ":" + phase.name() + ":" + startIndex;
    }
}
