/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott;

import java.util.Comparator;
import java.util.ArrayList;
import java.time.Duration;
import java.util.Objects;
import java.util.List;

/**
 * Enumerates common Elliott wave degrees.
 *
 * <p>
 * Degrees express relative time horizons rather than fixed durations. Typical
 * ranges used in classical Elliott Wave literature are:
 * <ul>
 * <li>{@link #GRAND_SUPERCYCLE}: multi-decade to century scale</li>
 * <li>{@link #SUPER_CYCLE}: decades</li>
 * <li>{@link #CYCLE}: years</li>
 * <li>{@link #PRIMARY}: months to years</li>
 * <li>{@link #INTERMEDIATE}: weeks to months</li>
 * <li>{@link #MINOR}: days to weeks</li>
 * <li>{@link #MINUTE}: hours to days</li>
 * <li>{@link #MINUETTE}: minutes to hours</li>
 * <li>{@link #SUB_MINUETTE}: minutes and below</li>
 * </ul>
 *
 * <p>
 * Use this enum to label the expected scale of your swings and scenarios.
 * Degrees are consumed by {@link ElliottSwingIndicator},
 * {@link ElliottScenarioGenerator}, and {@link ElliottWaveFacade} to keep
 * interpretations consistent with the bar resolution.
 *
 * <p>
 * Practical guidance for bar-based analysis (rule of thumb):
 * <ul>
 * <li>Weekly bars: {@link #CYCLE}, {@link #PRIMARY}, {@link #INTERMEDIATE};
 * {@link #MINOR} and below are usually too granular.</li>
 * <li>Daily bars: {@link #PRIMARY} through {@link #MINUTE}; {@link #MINUETTE}
 * and {@link #SUB_MINUETTE} are not recommended.</li>
 * <li>Hourly bars: {@link #INTERMEDIATE} through {@link #MINUETTE};
 * {@link #SUB_MINUETTE} requires high-resolution intraday data.</li>
 * <li>5-15 minute bars: {@link #MINOR} through {@link #SUB_MINUETTE}; higher
 * degrees need long histories.</li>
 * </ul>
 *
 * <p>
 * History length: aim for at least 2-3 full impulse+correction sequences at the
 * chosen degree and ensure typical swings span multiple bars (not single-bar
 * flips). For daily bars, a common starting point is:
 * <ul>
 * <li>{@link #MINUTE}: 30-90 bars</li>
 * <li>{@link #MINOR}: 60-180 bars</li>
 * <li>{@link #INTERMEDIATE}: 180-400 bars</li>
 * <li>{@link #PRIMARY}: 400-1000 bars</li>
 * <li>{@link #CYCLE} or higher: 1000+ bars</li>
 * </ul>
 *
 * <p>
 * If the available history falls below a range, step down one degree. Once the
 * history is comfortably above the upper bound and swings appear overly noisy,
 * consider stepping up a degree.
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/e/elliottwavetheory.asp">Investopedia:
 *      Elliott Wave Theory</a>
 * @since 0.22.0
 */
public enum ElliottDegree {

    /** Grand supercycle wave degree (multi-decade to century scale). */
    GRAND_SUPERCYCLE,

    /** Super cycle wave degree (decades). */
    SUPER_CYCLE,

    /** Cycle wave degree (years). */
    CYCLE,

    /** Primary wave degree (months to years). */
    PRIMARY,

    /** Intermediate wave degree (weeks to months). */
    INTERMEDIATE,

    /** Minor wave degree (days to weeks). */
    MINOR,

    /** Minute wave degree (hours to days). */
    MINUTE,

    /** Minuette wave degree (minutes to hours). */
    MINUETTE,

    /** Sub-minuette wave degree (minutes and below). */
    SUB_MINUETTE;

    private static final DegreeRange[] RECOMMENDED_DAYS = { new DegreeRange(20000.0, 0.0),
            new DegreeRange(7000.0, 20000.0), new DegreeRange(1000.0, 7000.0), new DegreeRange(400.0, 1000.0),
            new DegreeRange(180.0, 400.0), new DegreeRange(60.0, 180.0), new DegreeRange(30.0, 90.0),
            new DegreeRange(7.0, 30.0), new DegreeRange(2.0, 7.0) };
    private static final double MIN_RECOMMENDATION_SCORE = 0.5;
    private static final ElliottDegree[] VALUES = values();

    /**
     * Returns the next higher (larger timeframe) wave degree.
     *
     * @return the higher degree, or {@code this} if already at the highest
     * @since 0.22.0
     */
    public ElliottDegree higherDegree() {
        int currentOrdinal = ordinal();
        return currentOrdinal == 0 ? this : VALUES[currentOrdinal - 1];
    }

    /**
     * Returns the next lower (smaller timeframe) wave degree.
     *
     * @return the lower degree, or {@code this} if already at the lowest
     * @since 0.22.0
     */
    public ElliottDegree lowerDegree() {
        int currentOrdinal = ordinal();
        return currentOrdinal == VALUES.length - 1 ? this : VALUES[currentOrdinal + 1];
    }

    /**
     * Returns whether this degree is higher than or equal to the specified degree.
     *
     * @param other the degree to compare against
     * @return {@code true} if this degree is larger (longer timeframe) than or
     *         equal to {@code other}
     * @since 0.22.0
     */
    public boolean isHigherOrEqual(final ElliottDegree other) {
        return ordinal() <= other.ordinal();
    }

    /**
     * Returns whether this degree is lower than or equal to the specified degree.
     *
     * @param other the degree to compare against
     * @return {@code true} if this degree is smaller (shorter timeframe) than or
     *         equal to {@code other}
     * @since 0.22.0
     */
    public boolean isLowerOrEqual(final ElliottDegree other) {
        return ordinal() >= other.ordinal();
    }

    /**
     * Returns recommended Elliott degrees for the given bar duration and history
     * length. The recommendations are heuristic and ranked by how well the total
     * history length fits the typical range for each degree, with degrees that are
     * too fine for the bar duration filtered out.
     *
     * <p>
     * The list is ordered with the strongest recommendation first. If no degree
     * meets the minimum score threshold, the closest-fit degree is returned.
     *
     * @param barDuration duration of each bar
     * @param barCount    number of bars in the series
     * @return ordered list of recommended degrees (best fit first)
     * @throws IllegalArgumentException if {@code barCount <= 0} or the duration is
     *                                  zero/negative
     * @since 0.22.0
     */
    public static List<ElliottDegree> getRecommendedDegrees(final Duration barDuration, final int barCount) {
        Objects.requireNonNull(barDuration, "barDuration cannot be null");
        if (barCount <= 0) {
            throw new IllegalArgumentException("barCount must be positive");
        }
        if (barDuration.isZero() || barDuration.isNegative()) {
            throw new IllegalArgumentException("barDuration must be positive");
        }

        double totalDays = toDays(barDuration) * barCount;
        ElliottDegree minimumDegree = minimumDegreeForDuration(barDuration);
        List<Recommendation> recommendations = new ArrayList<>();

        for (ElliottDegree degree : VALUES) {
            if (!degree.isHigherOrEqual(minimumDegree)) {
                continue;
            }
            DegreeRange range = RECOMMENDED_DAYS[degree.ordinal()];
            recommendations
                    .add(new Recommendation(degree, range.scoreForDays(totalDays), range.midpointDistance(totalDays)));
        }

        recommendations.sort(Comparator.comparingDouble(Recommendation::score)
                .reversed()
                .thenComparingDouble(Recommendation::midpointDistance)
                .thenComparing(Recommendation::degree));

        List<ElliottDegree> filtered = recommendations.stream()
                .filter(recommendation -> recommendation.score() >= MIN_RECOMMENDATION_SCORE)
                .map(Recommendation::degree)
                .toList();

        if (!filtered.isEmpty()) {
            return filtered;
        }

        if (recommendations.isEmpty()) {
            return List.of();
        }

        return List.of(recommendations.get(0).degree());
    }

    private static ElliottDegree minimumDegreeForDuration(final Duration barDuration) {
        if (barDuration.compareTo(Duration.ofDays(7)) >= 0) {
            return INTERMEDIATE;
        }
        if (barDuration.compareTo(Duration.ofDays(1)) >= 0) {
            return MINUTE;
        }
        if (barDuration.compareTo(Duration.ofHours(1)) >= 0) {
            return MINUETTE;
        }
        if (barDuration.compareTo(Duration.ofMinutes(15)) > 0) {
            return MINUETTE;
        }
        return SUB_MINUETTE;
    }

    private static double toDays(final Duration barDuration) {
        return barDuration.toMillis() / (double) Duration.ofDays(1).toMillis();
    }

    private record Recommendation(ElliottDegree degree, double score, double midpointDistance) {
    }

    private record DegreeRange(double minDays, double maxDays) {

        private double scoreForDays(final double totalDays) {
            if (totalDays < minDays) {
                return totalDays / minDays;
            }
            if (maxDays > 0.0 && totalDays > maxDays) {
                return maxDays / totalDays;
            }
            return 1.0;
        }

        private double midpointDistance(final double totalDays) {
            if (totalDays < minDays) {
                return minDays - totalDays;
            }
            if (maxDays <= 0.0) {
                return 0.0;
            }
            if (totalDays > maxDays) {
                return totalDays - maxDays;
            }
            return Math.abs(totalDays - ((minDays + maxDays) / 2.0));
        }
    }
}
