/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Calculates confidence scores for Elliott wave interpretations.
 *
 * <p>
 * The scorer evaluates multiple factors and produces a weighted aggregate
 * confidence:
 * <ul>
 * <li>Fibonacci proximity (35%): How closely swing ratios match canonical
 * levels</li>
 * <li>Time proportions (20%): Whether wave durations follow expected
 * relationships</li>
 * <li>Alternation quality (15%): Degree of pattern/depth alternation between
 * waves 2 and 4</li>
 * <li>Channel adherence (15%): Price staying within projected channel
 * bounds</li>
 * <li>Structure completeness (15%): How many expected waves are confirmed</li>
 * </ul>
 *
 * @since 0.22.0
 */
public final class ElliottConfidenceScorer {

    /** Default weight for Fibonacci proximity factor. */
    public static final double DEFAULT_FIBONACCI_WEIGHT = 0.35;

    /** Default weight for time proportion factor. */
    public static final double DEFAULT_TIME_WEIGHT = 0.20;

    /** Default weight for alternation factor. */
    public static final double DEFAULT_ALTERNATION_WEIGHT = 0.15;

    /** Default weight for channel adherence factor. */
    public static final double DEFAULT_CHANNEL_WEIGHT = 0.15;

    /** Default weight for structure completeness factor. */
    public static final double DEFAULT_COMPLETENESS_WEIGHT = 0.15;

    private final double fibonacciWeight;
    private final double timeWeight;
    private final double alternationWeight;
    private final double channelWeight;
    private final double completenessWeight;
    private final NumFactory numFactory;

    /**
     * Creates a scorer with default weights.
     *
     * @param numFactory factory for creating numeric values
     * @since 0.22.0
     */
    public ElliottConfidenceScorer(final NumFactory numFactory) {
        this(numFactory, DEFAULT_FIBONACCI_WEIGHT, DEFAULT_TIME_WEIGHT, DEFAULT_ALTERNATION_WEIGHT,
                DEFAULT_CHANNEL_WEIGHT, DEFAULT_COMPLETENESS_WEIGHT);
    }

    /**
     * Creates a scorer with custom weights.
     *
     * @param numFactory         factory for creating numeric values
     * @param fibonacciWeight    weight for Fibonacci proximity (0.0 - 1.0)
     * @param timeWeight         weight for time proportions (0.0 - 1.0)
     * @param alternationWeight  weight for alternation quality (0.0 - 1.0)
     * @param channelWeight      weight for channel adherence (0.0 - 1.0)
     * @param completenessWeight weight for structure completeness (0.0 - 1.0)
     * @since 0.22.0
     */
    public ElliottConfidenceScorer(final NumFactory numFactory, final double fibonacciWeight, final double timeWeight,
            final double alternationWeight, final double channelWeight, final double completenessWeight) {
        this.numFactory = Objects.requireNonNull(numFactory, "numFactory");
        this.fibonacciWeight = fibonacciWeight;
        this.timeWeight = timeWeight;
        this.alternationWeight = alternationWeight;
        this.channelWeight = channelWeight;
        this.completenessWeight = completenessWeight;
    }

    /**
     * Scores a wave structure based on its swings and current phase.
     *
     * @param swings  the swing sequence to evaluate
     * @param phase   the current wave phase
     * @param channel optional channel for adherence scoring (may be null)
     * @return confidence metrics for the structure
     * @since 0.22.0
     */
    public ElliottConfidence score(final List<ElliottSwing> swings, final ElliottPhase phase,
            final ElliottChannel channel) {
        if (swings == null || swings.isEmpty() || phase == ElliottPhase.NONE) {
            return ElliottConfidence.zero(numFactory);
        }

        final Num fibScore = scoreFibonacci(swings, phase);
        final Num timeScore = scoreTimeProportions(swings, phase);
        final Num altScore = scoreAlternation(swings, phase);
        final Num chanScore = scoreChannel(swings, channel);
        final Num compScore = scoreCompleteness(swings, phase);

        final double overall = fibScore.doubleValue() * fibonacciWeight + timeScore.doubleValue() * timeWeight
                + altScore.doubleValue() * alternationWeight + chanScore.doubleValue() * channelWeight
                + compScore.doubleValue() * completenessWeight;

        final String reason = determinePrimaryReason(fibScore, timeScore, altScore, chanScore, compScore);

        return new ElliottConfidence(numFactory.numOf(overall), fibScore, timeScore, altScore, chanScore, compScore,
                reason);
    }

    /**
     * Scores Fibonacci ratio conformance (0.0 - 1.0).
     *
     * <p>
     * The current implementation evaluates Fibonacci ratios for impulse waves
     * (5-wave patterns). For corrective waves (3-wave patterns), different
     * Fibonacci ratio rules may apply.
     *
     * @param swings the swing sequence
     * @param phase  current phase
     * @return Fibonacci proximity score
     * @since 0.22.0
     */
    public Num scoreFibonacci(final List<ElliottSwing> swings, final ElliottPhase phase) {
        if (swings == null || swings.size() < 2) {
            return numFactory.zero();
        }

        // Current Fibonacci ratio rules are specific to impulse waves
        if (phase != null && !phase.isImpulse()) {
            return numFactory.zero(); // Return zero for non-impulse patterns
        }

        double totalScore = 0.0;
        int count = 0;

        // Score wave 2 retracement (0.382 - 0.786 ideal)
        if (swings.size() >= 2) {
            final double ratio = calculateRatio(swings.get(1), swings.get(0));
            totalScore += scoreRetracementRatio(ratio, 0.382, 0.786);
            count++;
        }

        // Score wave 3 extension (1.0 - 2.618 ideal, prefer 1.618)
        if (swings.size() >= 3) {
            final double ratio = calculateRatio(swings.get(2), swings.get(0));
            totalScore += scoreExtensionRatio(ratio, 1.0, 2.618, 1.618);
            count++;
        }

        // Score wave 4 retracement (0.236 - 0.786 ideal)
        if (swings.size() >= 4) {
            final double ratio = calculateRatio(swings.get(3), swings.get(2));
            totalScore += scoreRetracementRatio(ratio, 0.236, 0.786);
            count++;
        }

        // Score wave 5 projection (0.618 - 1.618 ideal)
        if (swings.size() >= 5) {
            final double ratio = calculateRatio(swings.get(4), swings.get(0));
            totalScore += scoreExtensionRatio(ratio, 0.618, 1.618, 1.0);
            count++;
        }

        return count > 0 ? numFactory.numOf(totalScore / count) : numFactory.zero();
    }

    /**
     * Scores time proportion conformance (0.0 - 1.0).
     *
     * <p>
     * Wave 3 should typically be the longest in time, and wave 1 and 5 often have
     * similar durations.
     *
     * <p>
     * The current implementation evaluates time proportions for impulse waves
     * (5-wave patterns). For corrective waves (3-wave patterns), different time
     * proportion rules may apply.
     *
     * @param swings the swing sequence
     * @param phase  current phase
     * @return time proportion score
     * @since 0.22.0
     */
    public Num scoreTimeProportions(final List<ElliottSwing> swings, final ElliottPhase phase) {
        if (swings == null || swings.size() < 3) {
            return numFactory.numOf(0.5); // Neutral score for insufficient data
        }

        // Current time proportion rules are specific to impulse waves
        if (phase != null && !phase.isImpulse()) {
            return numFactory.numOf(0.5); // Neutral for non-impulse patterns
        }

        double score = 0.5; // Start at neutral

        // Wave 3 should be at least as long as wave 1 in time
        final int wave1Length = swings.get(0).length();
        final int wave3Length = swings.get(2).length();

        if (wave3Length >= wave1Length) {
            score += 0.25;
        }

        // Check wave 5 vs wave 1 similarity (if available)
        if (swings.size() >= 5) {
            final int wave5Length = swings.get(4).length();
            final double ratio = wave1Length > 0 ? (double) wave5Length / wave1Length : 0;
            // Score higher if wave 5 is between 0.5x and 1.5x of wave 1
            if (ratio >= 0.5 && ratio <= 1.5) {
                score += 0.25;
            }
        }

        return numFactory.numOf(Math.min(1.0, score));
    }

    /**
     * Scores wave 2/4 alternation quality (0.0 - 1.0).
     *
     * <p>
     * Waves 2 and 4 should alternate in character: if wave 2 is sharp, wave 4
     * should be sideways, and vice versa. This is evaluated by comparing their
     * retracement depths and time durations.
     *
     * <p>
     * Alternation only applies to impulse waves (5-wave patterns), not corrective
     * waves (3-wave patterns).
     *
     * @param swings the swing sequence
     * @param phase  current phase
     * @return alternation score
     * @since 0.22.0
     */
    public Num scoreAlternation(final List<ElliottSwing> swings, final ElliottPhase phase) {
        if (swings == null || swings.size() < 4) {
            return numFactory.numOf(0.5); // Neutral for insufficient data
        }

        // Alternation only applies to impulse waves, not corrective patterns
        if (phase != null && !phase.isImpulse()) {
            return numFactory.numOf(0.5); // Neutral for non-impulse patterns
        }

        final ElliottSwing wave2 = swings.get(1);
        final ElliottSwing wave4 = swings.get(3);

        // Compare retracement depths - they should differ
        final double wave1Amp = swings.get(0).amplitude().doubleValue();
        final double wave3Amp = swings.get(2).amplitude().doubleValue();
        final double wave2Depth = wave1Amp > 0 ? wave2.amplitude().doubleValue() / wave1Amp : 0;
        final double wave4Depth = wave3Amp > 0 ? wave4.amplitude().doubleValue() / wave3Amp : 0;

        // Ideal alternation: one shallow (< 0.5), one deep (> 0.5)
        final double depthDifference = Math.abs(wave2Depth - wave4Depth);

        // Compare time durations - they should also differ
        final int wave2Time = wave2.length();
        final int wave4Time = wave4.length();
        final double timeDifference = wave2Time > 0 || wave4Time > 0
                ? Math.abs(wave2Time - wave4Time) / (double) Math.max(wave2Time, wave4Time)
                : 0;

        // Score based on both depth and time alternation
        final double depthScore = Math.min(1.0, depthDifference * 2);
        final double timeScore = Math.min(1.0, timeDifference);

        return numFactory.numOf((depthScore + timeScore) / 2);
    }

    /**
     * Scores channel adherence (0.0 - 1.0).
     *
     * @param swings  the swing sequence
     * @param channel the projected channel (may be null)
     * @return channel adherence score
     * @since 0.22.0
     */
    public Num scoreChannel(final List<ElliottSwing> swings, final ElliottChannel channel) {
        if (swings == null || swings.isEmpty() || channel == null || !channel.isValid()) {
            return numFactory.numOf(0.5); // Neutral for no channel
        }

        int withinChannel = 0;
        int totalPoints = 0;

        for (final ElliottSwing swing : swings) {
            // Check both endpoints of each swing
            if (channel.contains(swing.fromPrice(), numFactory.zero())) {
                withinChannel++;
            }
            totalPoints++;

            if (channel.contains(swing.toPrice(), numFactory.zero())) {
                withinChannel++;
            }
            totalPoints++;
        }

        return totalPoints > 0 ? numFactory.numOf((double) withinChannel / totalPoints) : numFactory.numOf(0.5);
    }

    /**
     * Scores structure completeness (0.0 - 1.0).
     *
     * @param swings the swing sequence
     * @param phase  current phase
     * @return completeness score
     * @since 0.22.0
     */
    public Num scoreCompleteness(final List<ElliottSwing> swings, final ElliottPhase phase) {
        if (swings == null || swings.isEmpty()) {
            return numFactory.zero();
        }

        final int expectedWaves;
        if (phase.isImpulse()) {
            expectedWaves = 5;
        } else if (phase.isCorrective()) {
            expectedWaves = 3;
        } else {
            return numFactory.zero();
        }

        final int actualWaves = swings.size();
        final double completeness = Math.min(1.0, (double) actualWaves / expectedWaves);

        // Bonus for completed structures
        if (phase.completesStructure()) {
            return numFactory.numOf(Math.min(1.0, completeness + 0.1));
        }

        return numFactory.numOf(completeness);
    }

    private double calculateRatio(final ElliottSwing numerator, final ElliottSwing denominator) {
        if (numerator == null || denominator == null) {
            return 0.0;
        }
        final double numAmp = numerator.amplitude().doubleValue();
        final double denAmp = denominator.amplitude().doubleValue();
        return denAmp > 0 ? numAmp / denAmp : 0.0;
    }

    private double scoreRetracementRatio(final double ratio, final double min, final double max) {
        if (ratio < min * 0.8 || ratio > max * 1.2) {
            return 0.0; // Well outside range
        }
        if (ratio >= min && ratio <= max) {
            return 1.0; // Perfect
        }
        // Partial score for close misses
        if (ratio < min) {
            return Math.max(0, 1.0 - (min - ratio) / min);
        }
        return Math.max(0, 1.0 - (ratio - max) / max);
    }

    private double scoreExtensionRatio(final double ratio, final double min, final double max, final double ideal) {
        if (ratio < min * 0.8 || ratio > max * 1.2) {
            return 0.0;
        }

        // Base score scales down when slightly outside the preferred range
        double score;
        if (ratio < min) {
            double minBound = min * 0.8;
            score = 0.7 * Math.max(0.0, (ratio - minBound) / (min - minBound));
        } else if (ratio > max) {
            double maxBound = max * 1.2;
            score = 0.7 * Math.max(0.0, (maxBound - ratio) / (maxBound - max));
        } else {
            score = 0.7;
        }

        // Bonus for being close to ideal
        final double distanceFromIdeal = Math.abs(ratio - ideal);
        score += 0.3 * Math.max(0, 1.0 - distanceFromIdeal / ideal);

        return Math.min(1.0, score);
    }

    private String determinePrimaryReason(final Num fibScore, final Num timeScore, final Num altScore,
            final Num chanScore, final Num compScore) {

        // Find the highest weighted contributor
        final double fibContrib = fibScore.doubleValue() * fibonacciWeight;
        final double timeContrib = timeScore.doubleValue() * timeWeight;
        final double altContrib = altScore.doubleValue() * alternationWeight;
        final double chanContrib = chanScore.doubleValue() * channelWeight;
        final double compContrib = compScore.doubleValue() * completenessWeight;

        double maxContrib = fibContrib;
        String reason = "Strong Fibonacci conformance";

        if (timeContrib > maxContrib) {
            maxContrib = timeContrib;
            reason = "Good time proportions";
        }
        if (altContrib > maxContrib) {
            maxContrib = altContrib;
            reason = "Clear wave alternation";
        }
        if (chanContrib > maxContrib) {
            maxContrib = chanContrib;
            reason = "Strong channel adherence";
        }
        if (compContrib > maxContrib) {
            reason = "Complete structure";
        }

        return reason;
    }
}
