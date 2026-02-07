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
 * <p>
 * Use this scorer when you need direct control over factor weights or want to
 * build custom confidence profiles for {@link ElliottScenarioGenerator} or
 * {@link org.ta4j.core.indicators.elliott.confidence.ConfidenceModel}
 * implementations.
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

        final Num overall = fibScore.multipliedBy(numFactory.numOf(fibonacciWeight))
                .plus(timeScore.multipliedBy(numFactory.numOf(timeWeight)))
                .plus(altScore.multipliedBy(numFactory.numOf(alternationWeight)))
                .plus(chanScore.multipliedBy(numFactory.numOf(channelWeight)))
                .plus(compScore.multipliedBy(numFactory.numOf(completenessWeight)));

        final String reason = determinePrimaryReason(fibScore, timeScore, altScore, chanScore, compScore);

        return new ElliottConfidence(overall, fibScore, timeScore, altScore, chanScore, compScore, reason);
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

        Num totalScore = numFactory.zero();
        int count = 0;

        // Score wave 2 retracement (0.382 - 0.786 ideal)
        if (swings.size() >= 2) {
            final Num ratio = calculateRatio(swings.get(1), swings.get(0));
            totalScore = totalScore
                    .plus(scoreRetracementRatio(ratio, numFactory.numOf(0.382), numFactory.numOf(0.786)));
            count++;
        }

        // Score wave 3 extension (1.0 - 2.618 ideal, prefer 1.618)
        if (swings.size() >= 3) {
            final Num ratio = calculateRatio(swings.get(2), swings.get(0));
            totalScore = totalScore.plus(
                    scoreExtensionRatio(ratio, numFactory.one(), numFactory.numOf(2.618), numFactory.numOf(1.618)));
            count++;
        }

        // Score wave 4 retracement (0.236 - 0.786 ideal)
        if (swings.size() >= 4) {
            final Num ratio = calculateRatio(swings.get(3), swings.get(2));
            totalScore = totalScore
                    .plus(scoreRetracementRatio(ratio, numFactory.numOf(0.236), numFactory.numOf(0.786)));
            count++;
        }

        // Score wave 5 projection (0.618 - 1.618 ideal)
        if (swings.size() >= 5) {
            final Num ratio = calculateRatio(swings.get(4), swings.get(0));
            totalScore = totalScore.plus(
                    scoreExtensionRatio(ratio, numFactory.numOf(0.618), numFactory.numOf(1.618), numFactory.one()));
            count++;
        }

        return count > 0 ? totalScore.dividedBy(numFactory.numOf(count)) : numFactory.zero();
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
     * Computes alternation diagnostics between waves 2 and 4.
     *
     * @param swings swing sequence
     * @param phase  current phase
     * @return alternation diagnostics with score and duration ratios
     * @since 0.22.2
     */
    public AlternationDiagnostics alternationDiagnostics(final List<ElliottSwing> swings, final ElliottPhase phase) {
        if (swings == null || swings.size() < 4) {
            return AlternationDiagnostics.neutral();
        }

        if (phase != null && !phase.isImpulse()) {
            return AlternationDiagnostics.neutral();
        }

        final ElliottSwing wave2 = swings.get(1);
        final ElliottSwing wave4 = swings.get(3);

        final Num wave1Amp = swings.get(0).amplitude();
        final Num wave3Amp = swings.get(2).amplitude();
        final Num wave2Depth = wave1Amp.isPositive() ? wave2.amplitude().dividedBy(wave1Amp) : numFactory.zero();
        final Num wave4Depth = wave3Amp.isPositive() ? wave4.amplitude().dividedBy(wave3Amp) : numFactory.zero();

        final Num depthDifference = wave2Depth.minus(wave4Depth).abs();

        final int wave2Bars = wave2.length();
        final int wave4Bars = wave4.length();
        Num timeDifference = numFactory.zero();
        if (wave2Bars > 0 || wave4Bars > 0) {
            Num diff = numFactory.numOf(Math.abs(wave2Bars - wave4Bars));
            Num max = numFactory.numOf(Math.max(wave2Bars, wave4Bars));
            timeDifference = diff.dividedBy(max);
        }
        final double durationRatio = wave2Bars > 0
                ? numFactory.numOf(wave4Bars).dividedBy(numFactory.numOf(wave2Bars)).doubleValue()
                : Double.NaN;

        final Num depthScore = depthDifference.multipliedBy(numFactory.two()).min(numFactory.one());
        final Num timeScore = timeDifference.min(numFactory.one());
        final Num score = depthScore.plus(timeScore).dividedBy(numFactory.two());

        return new AlternationDiagnostics(wave2Bars, wave4Bars, durationRatio, depthDifference.doubleValue(),
                timeDifference.doubleValue(), score.doubleValue());
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
        return numFactory.numOf(alternationDiagnostics(swings, phase).score());
    }

    /**
     * Alternation diagnostics between waves 2 and 4.
     *
     * @param barsWave2       number of bars in wave 2
     * @param barsWave4       number of bars in wave 4
     * @param durationRatio   wave 4 / wave 2 bar ratio
     * @param depthDifference absolute difference in retracement depth ratios
     * @param timeDifference  relative duration difference (0.0 - 1.0)
     * @param score           alternation score (0.0 - 1.0)
     * @since 0.22.2
     */
    public record AlternationDiagnostics(int barsWave2, int barsWave4, double durationRatio, double depthDifference,
            double timeDifference, double score) {

        /**
         * @return neutral alternation diagnostics
         * @since 0.22.2
         */
        public static AlternationDiagnostics neutral() {
            return new AlternationDiagnostics(0, 0, Double.NaN, 0.0, 0.0, 0.5);
        }
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

        return totalPoints > 0 ? numFactory.numOf(withinChannel).dividedBy(numFactory.numOf(totalPoints))
                : numFactory.numOf(0.5);
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
        Num completeness = numFactory.numOf(actualWaves)
                .dividedBy(numFactory.numOf(expectedWaves))
                .min(numFactory.one());

        // Bonus for completed structures
        if (phase.completesStructure()) {
            return completeness.plus(numFactory.numOf(0.1)).min(numFactory.one());
        }

        return completeness;
    }

    private Num calculateRatio(final ElliottSwing numerator, final ElliottSwing denominator) {
        if (numerator == null || denominator == null) {
            return numFactory.zero();
        }
        final Num numAmp = numerator.amplitude();
        final Num denAmp = denominator.amplitude();
        return denAmp.isPositive() ? numAmp.dividedBy(denAmp) : numFactory.zero();
    }

    private Num scoreRetracementRatio(final Num ratio, final Num min, final Num max) {
        if (Num.isNaNOrNull(ratio)) {
            return numFactory.zero();
        }
        final Num lowerBound = min.multipliedBy(numFactory.numOf(0.8));
        final Num upperBound = max.multipliedBy(numFactory.numOf(1.2));
        if (ratio.isLessThan(lowerBound) || ratio.isGreaterThan(upperBound)) {
            return numFactory.zero(); // Well outside range
        }
        if (ratio.isGreaterThanOrEqual(min) && ratio.isLessThanOrEqual(max)) {
            return numFactory.one(); // Perfect
        }
        // Partial score for close misses
        if (ratio.isLessThan(min)) {
            Num score = numFactory.one().minus(min.minus(ratio).dividedBy(min));
            return score.max(numFactory.zero());
        }
        Num score = numFactory.one().minus(ratio.minus(max).dividedBy(max));
        return score.max(numFactory.zero());
    }

    private Num scoreExtensionRatio(final Num ratio, final Num min, final Num max, final Num ideal) {
        if (Num.isNaNOrNull(ratio)) {
            return numFactory.zero();
        }
        final Num lowerBound = min.multipliedBy(numFactory.numOf(0.8));
        final Num upperBound = max.multipliedBy(numFactory.numOf(1.2));
        if (ratio.isLessThan(lowerBound) || ratio.isGreaterThan(upperBound)) {
            return numFactory.zero();
        }

        // Base score scales down when slightly outside the preferred range
        Num score;
        if (ratio.isLessThan(min)) {
            final Num minBound = lowerBound;
            final Num fraction = ratio.minus(minBound).dividedBy(min.minus(minBound)).max(numFactory.zero());
            score = numFactory.numOf(0.7).multipliedBy(fraction);
        } else if (ratio.isGreaterThan(max)) {
            final Num maxBound = upperBound;
            final Num fraction = maxBound.minus(ratio).dividedBy(maxBound.minus(max)).max(numFactory.zero());
            score = numFactory.numOf(0.7).multipliedBy(fraction);
        } else {
            score = numFactory.numOf(0.7);
        }

        // Bonus for being close to ideal
        final Num distanceFromIdeal = ratio.minus(ideal).abs();
        final Num bonus = numFactory.numOf(0.3)
                .multipliedBy(numFactory.one().minus(distanceFromIdeal.dividedBy(ideal)).max(numFactory.zero()));
        score = score.plus(bonus);

        return score.min(numFactory.one());
    }

    private String determinePrimaryReason(final Num fibScore, final Num timeScore, final Num altScore,
            final Num chanScore, final Num compScore) {

        // Find the highest weighted contributor
        final Num fibContrib = fibScore.multipliedBy(numFactory.numOf(fibonacciWeight));
        final Num timeContrib = timeScore.multipliedBy(numFactory.numOf(timeWeight));
        final Num altContrib = altScore.multipliedBy(numFactory.numOf(alternationWeight));
        final Num chanContrib = chanScore.multipliedBy(numFactory.numOf(channelWeight));
        final Num compContrib = compScore.multipliedBy(numFactory.numOf(completenessWeight));

        Num maxContrib = fibContrib;
        String reason = "Strong Fibonacci conformance";

        if (timeContrib.isGreaterThan(maxContrib)) {
            maxContrib = timeContrib;
            reason = "Good time proportions";
        }
        if (altContrib.isGreaterThan(maxContrib)) {
            maxContrib = altContrib;
            reason = "Clear wave alternation";
        }
        if (chanContrib.isGreaterThan(maxContrib)) {
            maxContrib = chanContrib;
            reason = "Strong channel adherence";
        }
        if (compContrib.isGreaterThan(maxContrib)) {
            reason = "Complete structure";
        }

        return reason;
    }
}
