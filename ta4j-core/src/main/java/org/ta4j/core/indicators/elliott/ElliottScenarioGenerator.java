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
 * <p>
 * Use this generator when you need programmatic access to alternative wave
 * interpretations outside of the indicator framework (for example in batch
 * analysis or custom pipelines). It is the engine behind
 * {@link ElliottScenarioIndicator} and {@link ElliottWaveAnalysisRunner}.
 *
 * @since 0.22.0
 */
public final class ElliottScenarioGenerator {

    /** Default minimum confidence threshold for retaining scenarios. */
    public static final double DEFAULT_MIN_CONFIDENCE = 0.15;

    /** Maximum number of scenarios to retain after pruning. */
    public static final int DEFAULT_MAX_SCENARIOS = 5;
    private static final double IMPULSE_STRUCTURE_REJECTION_SCORE = 0.0;

    private final NumFactory numFactory;
    private final ElliottFibonacciValidator fibValidator;
    private final ConfidenceModel confidenceModel;
    private final PatternSet patternSet;
    private final double minConfidence;
    private final Num minConfidenceNum;
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
        this.minConfidenceNum = numFactory.numOf(minConfidence);
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

        // Explore every feasible starting point so long multi-swing histories can
        // still surface a valid structure that begins after early noise.
        for (int startIndex = 0; startIndex < swings.size(); startIndex++) {
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

            double structureScore = scoreImpulseStructure(impulseSwings, phase);
            if (structureScore <= IMPULSE_STRUCTURE_REJECTION_SCORE) {
                continue;
            }

            final String signature = createSignature(ScenarioType.IMPULSE, phase, startIndex);
            if (seenSignatures.contains(signature)) {
                continue;
            }
            seenSignatures.add(signature);

            final ElliottConfidenceBreakdown breakdown = confidenceModel.score(impulseSwings, phase, channel,
                    ScenarioType.IMPULSE);
            final ElliottConfidence confidence = applyStructurePenalty(breakdown.confidence(), structureScore,
                    "Impulse structure score " + String.format(java.util.Locale.ROOT, "%.2f", structureScore));

            if (confidence.overall().isLessThan(minConfidenceNum)) {
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

            if (confidence.overall().isLessThan(minConfidenceNum)) {
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

            if (confidence.overall().isLessThan(minConfidenceNum)) {
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

    double scoreImpulseStructure(final List<ElliottSwing> swings, final ElliottPhase phase) {
        if (swings.isEmpty()) {
            return 0.0;
        }

        if (phase == null || !phase.isImpulse()) {
            return 0.0;
        }

        for (int i = 1; i < swings.size(); i++) {
            if (swings.get(i).isRising() == swings.get(i - 1).isRising()) {
                return 0.0;
            }
        }

        final List<Double> scores = new ArrayList<>();
        if (swings.size() >= 2) {
            final ElliottSwing wave1 = swings.get(0);
            final ElliottSwing wave2 = swings.get(1);
            scores.add(wave2InvalidationScore(wave1, wave2));
        }

        if (swings.size() >= 4) {
            final ElliottSwing wave1 = swings.get(0);
            final ElliottSwing wave4 = swings.get(3);
            scores.add(wave4OverlapScore(wave1, wave4));
        }

        if (swings.size() >= 5) {
            scores.add(wave3ShortestScore(swings.get(0), swings.get(2), swings.get(4)));
        }

        if (scores.isEmpty()) {
            return 1.0;
        }

        double total = 0.0;
        for (double score : scores) {
            total += score;
        }
        return clamp01(total / scores.size());
    }

    private ElliottConfidence applyStructurePenalty(final ElliottConfidence confidence, final double structureScore,
            final String reason) {
        Objects.requireNonNull(confidence, "confidence");
        double penalty = 0.35 + (0.65 * clamp01(structureScore));
        Num penaltyNum = numFactory.numOf(penalty);
        Num overall = confidence.overall().multipliedBy(penaltyNum);
        Num completeness = confidence.completenessScore().multipliedBy(penaltyNum);
        String primaryReason = confidence.primaryReason();
        if (primaryReason == null || primaryReason.isBlank()) {
            primaryReason = reason;
        } else if (structureScore < 0.999) {
            primaryReason = primaryReason + "; " + reason;
        }
        return new ElliottConfidence(overall, confidence.fibonacciScore(), confidence.timeProportionScore(),
                confidence.alternationScore(), confidence.channelScore(), completeness, primaryReason);
    }

    private double wave2InvalidationScore(final ElliottSwing wave1, final ElliottSwing wave2) {
        double denominator = Math.max(1e-9, wave1.amplitude().doubleValue());
        if (wave1.isRising()) {
            if (wave2.toPrice().isGreaterThanOrEqual(wave1.fromPrice())) {
                return 1.0;
            }
            double breach = wave1.fromPrice().minus(wave2.toPrice()).doubleValue();
            return clamp01(1.0 - (breach / denominator));
        }
        if (wave2.toPrice().isLessThanOrEqual(wave1.fromPrice())) {
            return 1.0;
        }
        double breach = wave2.toPrice().minus(wave1.fromPrice()).doubleValue();
        return clamp01(1.0 - (breach / denominator));
    }

    private double wave4OverlapScore(final ElliottSwing wave1, final ElliottSwing wave4) {
        double denominator = Math.max(1e-9, wave1.amplitude().doubleValue());
        if (wave1.isRising()) {
            if (wave4.toPrice().isGreaterThanOrEqual(wave1.toPrice())) {
                return 1.0;
            }
            double overlap = wave1.toPrice().minus(wave4.toPrice()).doubleValue();
            return clamp01(1.0 - (overlap / (denominator * 1.5)));
        }
        if (wave4.toPrice().isLessThanOrEqual(wave1.toPrice())) {
            return 1.0;
        }
        double overlap = wave4.toPrice().minus(wave1.toPrice()).doubleValue();
        return clamp01(1.0 - (overlap / (denominator * 1.5)));
    }

    private double wave3ShortestScore(final ElliottSwing wave1, final ElliottSwing wave3, final ElliottSwing wave5) {
        double wave1Amplitude = wave1.amplitude().doubleValue();
        double wave3Amplitude = wave3.amplitude().doubleValue();
        double wave5Amplitude = wave5.amplitude().doubleValue();
        double minimumPeer = Math.min(wave1Amplitude, wave5Amplitude);
        if (wave3Amplitude >= minimumPeer) {
            return 1.0;
        }
        return clamp01(wave3Amplitude / Math.max(1e-9, minimumPeer));
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
                .filter(s -> s.confidenceScore().isGreaterThanOrEqual(minConfidenceNum))
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

    private static double clamp01(final double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }
}
