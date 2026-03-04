/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.reports.TradingStatement;

/**
 * Shared contract for execution results that expose statement-level outputs.
 *
 * <p>
 * This contract provides common statement/criterion utilities for backtest and
 * strategy walk-forward result models.
 * </p>
 *
 * @param <R> runtime report type
 * @since 0.22.4
 */
public interface TradingStatementExecutionResult<R> {

    /**
     * @return bar series used to produce this result
     * @since 0.22.4
     */
    BarSeries barSeries();

    /**
     * @return ordered trading statements produced by this result
     * @since 0.22.4
     */
    List<TradingStatement> tradingStatements();

    /**
     * @return runtime report for this result
     * @since 0.22.4
     */
    R runtimeReport();

    /**
     * Returns trading records in statement order.
     *
     * @return ordered trading records
     * @since 0.22.4
     */
    default List<TradingRecord> tradingRecords() {
        List<TradingRecord> records = new ArrayList<>(tradingStatements().size());
        for (TradingStatement statement : tradingStatements()) {
            records.add(statement.getTradingRecord());
        }
        return Collections.unmodifiableList(records);
    }

    /**
     * Evaluates one criterion over all statement trading records in statement
     * order.
     *
     * @param criterion analysis criterion
     * @return criterion values in statement order
     * @since 0.22.4
     */
    default List<Num> criterionValues(AnalysisCriterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        List<Num> values = new ArrayList<>(tradingStatements().size());
        for (TradingStatement statement : tradingStatements()) {
            TradingRecord tradingRecord = statement.getTradingRecord();
            Num value = tradingRecord == null ? NaN.NaN : criterion.calculate(barSeries(), tradingRecord);
            values.add(value);
        }
        return Collections.unmodifiableList(values);
    }

    /**
     * Evaluates one criterion and returns values keyed by statement index.
     *
     * @param criterion analysis criterion
     * @return ordered statement-index to criterion value map
     * @since 0.22.4
     */
    default Map<Integer, Num> criterionValuesByIndex(AnalysisCriterion criterion) {
        Objects.requireNonNull(criterion, "criterion");
        Map<Integer, Num> values = new LinkedHashMap<>();
        List<TradingStatement> statements = tradingStatements();
        for (int index = 0; index < statements.size(); index++) {
            TradingRecord tradingRecord = statements.get(index).getTradingRecord();
            Num value = tradingRecord == null ? NaN.NaN : criterion.calculate(barSeries(), tradingRecord);
            values.put(index, value);
        }
        return Collections.unmodifiableMap(values);
    }

    /**
     * Ranks all statements using a weighted, normalized criterion profile.
     *
     * @param profile ranking profile
     * @return ranked statement rows sorted by composite score descending
     * @since 0.22.4
     */
    default List<RankedTradingStatement> rankTradingStatements(RankingProfile profile) {
        Objects.requireNonNull(profile, "profile");

        List<TradingStatement> statements = tradingStatements();
        if (statements.isEmpty()) {
            return List.of();
        }

        NumFactory numFactory = barSeries().numFactory();
        List<WeightedCriterion> weightedCriteria = profile.criteria();
        Map<AnalysisCriterion, Num> normalizedWeights = normalizeWeights(weightedCriteria, numFactory);

        Map<AnalysisCriterion, List<Num>> rawValuesByCriterion = new LinkedHashMap<>();
        for (WeightedCriterion weightedCriterion : weightedCriteria) {
            rawValuesByCriterion.put(weightedCriterion.criterion(), new ArrayList<>(statements.size()));
        }

        for (TradingStatement statement : statements) {
            TradingRecord tradingRecord = statement.getTradingRecord();
            for (WeightedCriterion weightedCriterion : weightedCriteria) {
                AnalysisCriterion criterion = weightedCriterion.criterion();
                Num rawValue = tradingRecord == null ? NaN.NaN : criterion.calculate(barSeries(), tradingRecord);
                rawValuesByCriterion.get(criterion).add(rawValue);
            }
        }

        Map<AnalysisCriterion, Num> bestValueByCriterion = new LinkedHashMap<>();
        Map<AnalysisCriterion, Num> worstValueByCriterion = new LinkedHashMap<>();
        for (WeightedCriterion weightedCriterion : weightedCriteria) {
            AnalysisCriterion criterion = weightedCriterion.criterion();
            Num[] pair = findBestWorst(rawValuesByCriterion.get(criterion), criterion);
            bestValueByCriterion.put(criterion, pair[0]);
            worstValueByCriterion.put(criterion, pair[1]);
        }

        List<RankedTradingStatement> rankedStatements = new ArrayList<>(statements.size());
        for (int statementIndex = 0; statementIndex < statements.size(); statementIndex++) {
            TradingStatement statement = statements.get(statementIndex);
            Map<AnalysisCriterion, Num> rawScores = new LinkedHashMap<>();
            Map<AnalysisCriterion, Num> normalizedScores = new LinkedHashMap<>();

            Num weightedScore = numFactory.zero();
            Num activeWeight = numFactory.zero();
            boolean excluded = false;

            for (WeightedCriterion weightedCriterion : weightedCriteria) {
                AnalysisCriterion criterion = weightedCriterion.criterion();
                Num weight = normalizedWeights.get(criterion);
                Num rawValue = rawValuesByCriterion.get(criterion).get(statementIndex);
                rawScores.put(criterion, rawValue);

                if (Num.isNaNOrNull(rawValue)) {
                    Num missingScore = handleMissing(profile.missingValuePolicy(), numFactory, normalizedScores,
                            criterion);
                    if (missingScore == null) {
                        excluded = true;
                        break;
                    }
                    if (profile.missingValuePolicy() != MissingValuePolicy.RENORMALIZE_WEIGHTS) {
                        weightedScore = weightedScore.plus(weight.multipliedBy(missingScore));
                        activeWeight = activeWeight.plus(weight);
                    }
                    continue;
                }

                Num bestValue = bestValueByCriterion.get(criterion);
                Num worstValue = worstValueByCriterion.get(criterion);
                Num normalizedValue = profile.normalizer()
                        .normalize(criterion, rawValue, bestValue, worstValue, numFactory);

                if (Num.isNaNOrNull(normalizedValue)) {
                    Num missingScore = handleMissing(profile.missingValuePolicy(), numFactory, normalizedScores,
                            criterion);
                    if (missingScore == null) {
                        excluded = true;
                        break;
                    }
                    if (profile.missingValuePolicy() != MissingValuePolicy.RENORMALIZE_WEIGHTS) {
                        weightedScore = weightedScore.plus(weight.multipliedBy(missingScore));
                        activeWeight = activeWeight.plus(weight);
                    }
                    continue;
                }

                Num clampedScore = clamp01(normalizedValue, numFactory);
                normalizedScores.put(criterion, clampedScore);
                weightedScore = weightedScore.plus(weight.multipliedBy(clampedScore));
                activeWeight = activeWeight.plus(weight);
            }

            if (excluded) {
                continue;
            }

            Num compositeScore = weightedScore;
            if (profile.missingValuePolicy() == MissingValuePolicy.RENORMALIZE_WEIGHTS) {
                compositeScore = activeWeight.isZero() ? numFactory.zero() : weightedScore.dividedBy(activeWeight);
            }

            rankedStatements
                    .add(new RankedTradingStatement(statement, compositeScore, Collections.unmodifiableMap(rawScores),
                            Collections.unmodifiableMap(normalizedScores), statementIndex));
        }

        rankedStatements.sort(Comparator
                .comparing(RankedTradingStatement::compositeScore, TradingStatementExecutionResult::compareScores)
                .reversed()
                .thenComparingInt(RankedTradingStatement::originalIndex));
        return Collections.unmodifiableList(rankedStatements);
    }

    /**
     * Returns top-ranked statements for the supplied weighted profile.
     *
     * @param limit   maximum number of statements to return
     * @param profile weighted ranking profile
     * @return top-ranked statements
     * @since 0.22.4
     */
    default List<TradingStatement> topTradingStatements(int limit, RankingProfile profile) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must not be negative");
        }
        if (limit == 0) {
            return List.of();
        }

        List<RankedTradingStatement> ranked = rankTradingStatements(profile);
        int effectiveLimit = Math.min(limit, ranked.size());
        List<TradingStatement> topStatements = new ArrayList<>(effectiveLimit);
        for (int i = 0; i < effectiveLimit; i++) {
            topStatements.add(ranked.get(i).statement());
        }
        return Collections.unmodifiableList(topStatements);
    }

    /**
     * Weighted criterion entry for composite ranking.
     *
     * @param criterion  criterion to evaluate
     * @param multiplier arbitrary non-negative multiplier
     * @since 0.22.4
     */
    record WeightedCriterion(AnalysisCriterion criterion, Num multiplier) {

        /**
         * Creates a validated weighted criterion.
         *
         * @param criterion  criterion to evaluate
         * @param multiplier arbitrary non-negative multiplier
         * @since 0.22.4
         */
        public WeightedCriterion {
            Objects.requireNonNull(criterion, "criterion");
            Objects.requireNonNull(multiplier, "multiplier");
            if (multiplier.isNaN()) {
                throw new IllegalArgumentException("multiplier must be finite");
            }
            if (Double.isInfinite(multiplier.doubleValue())) {
                throw new IllegalArgumentException("multiplier must be finite");
            }
            if (multiplier.isNegative()) {
                throw new IllegalArgumentException("multiplier must be >= 0");
            }
        }
    }

    /**
     * Criterion normalization strategy used by weighted ranking.
     *
     * @since 0.22.4
     */
    @FunctionalInterface
    interface CriterionNormalizer {

        /**
         * Normalizes a raw criterion value using criterion-specific best/worst values.
         *
         * @param criterion  criterion being normalized
         * @param rawValue   raw criterion value for one statement
         * @param bestValue  best observed finite value across statements for this
         *                   criterion
         * @param worstValue worst observed finite value across statements for this
         *                   criterion
         * @param numFactory target num factory
         * @return normalized value (expected in {@code [0,1]}); NaN to indicate
         *         missing/unavailable normalization
         * @since 0.22.4
         */
        Num normalize(AnalysisCriterion criterion, Num rawValue, Num bestValue, Num worstValue, NumFactory numFactory);
    }

    /**
     * Direction-aware min-max normalizer.
     *
     * <p>
     * Best observed value maps to {@code 1}, worst observed value maps to
     * {@code 0}, and intermediate values are scaled linearly.
     * </p>
     *
     * @since 0.22.4
     */
    final class DirectionAwareMinMaxNormalizer implements CriterionNormalizer {

        /**
         * Shared singleton instance.
         *
         * @since 0.22.4
         */
        public static final DirectionAwareMinMaxNormalizer INSTANCE = new DirectionAwareMinMaxNormalizer();

        private DirectionAwareMinMaxNormalizer() {
        }

        @Override
        public Num normalize(AnalysisCriterion criterion, Num rawValue, Num bestValue, Num worstValue,
                NumFactory numFactory) {
            Objects.requireNonNull(criterion, "criterion");
            Objects.requireNonNull(rawValue, "rawValue");
            Objects.requireNonNull(bestValue, "bestValue");
            Objects.requireNonNull(worstValue, "worstValue");
            Objects.requireNonNull(numFactory, "numFactory");

            Num normalizedRaw = normalizeToFactory(rawValue, numFactory);
            Num normalizedBest = normalizeToFactory(bestValue, numFactory);
            Num normalizedWorst = normalizeToFactory(worstValue, numFactory);

            if (Num.isNaNOrNull(normalizedRaw) || Num.isNaNOrNull(normalizedBest) || Num.isNaNOrNull(normalizedWorst)) {
                return NaN.NaN;
            }
            if (normalizedBest.isEqual(normalizedWorst)) {
                return numFactory.one();
            }

            if (normalizedBest.isGreaterThan(normalizedWorst)) {
                Num denominator = normalizedBest.minus(normalizedWorst);
                if (denominator.isZero()) {
                    return numFactory.one();
                }
                return normalizedRaw.minus(normalizedWorst).dividedBy(denominator);
            }

            Num denominator = normalizedWorst.minus(normalizedBest);
            if (denominator.isZero()) {
                return numFactory.one();
            }
            return normalizedWorst.minus(normalizedRaw).dividedBy(denominator);
        }
    }

    /**
     * Missing-value behavior for weighted ranking.
     *
     * @since 0.22.4
     */
    enum MissingValuePolicy {
        /** Treat missing criterion values as normalized score 0. */
        WORST_SCORE,
        /** Exclude missing criteria from statement-level weight sum. */
        RENORMALIZE_WEIGHTS,
        /** Exclude statements with any missing criterion value. */
        EXCLUDE_STATEMENT
    }

    /**
     * Weighted ranking configuration.
     *
     * @param criteria           criterion multipliers
     * @param normalizer         criterion normalizer (defaults to
     *                           {@link DirectionAwareMinMaxNormalizer#INSTANCE})
     * @param missingValuePolicy missing-value handling policy (defaults to
     *                           {@link MissingValuePolicy#WORST_SCORE})
     * @since 0.22.4
     */
    record RankingProfile(List<WeightedCriterion> criteria, CriterionNormalizer normalizer,
            MissingValuePolicy missingValuePolicy) {

        /**
         * Creates a validated ranking profile.
         *
         * @param criteria           criterion multipliers
         * @param normalizer         criterion normalizer
         * @param missingValuePolicy missing-value handling policy
         * @since 0.22.4
         */
        public RankingProfile {
            criteria = List.copyOf(Objects.requireNonNull(criteria, "criteria"));
            if (criteria.isEmpty()) {
                throw new IllegalArgumentException("criteria must not be empty");
            }
            normalizer = normalizer == null ? DirectionAwareMinMaxNormalizer.INSTANCE : normalizer;
            missingValuePolicy = missingValuePolicy == null ? MissingValuePolicy.WORST_SCORE : missingValuePolicy;

            Set<AnalysisCriterion> uniqueCriteria = new LinkedHashSet<>();
            for (WeightedCriterion weightedCriterion : criteria) {
                Objects.requireNonNull(weightedCriterion, "criteria must not contain null entries");
                if (!uniqueCriteria.add(weightedCriterion.criterion())) {
                    throw new IllegalArgumentException("criteria must not contain duplicates");
                }
            }
        }

        /**
         * Creates a profile with default normalizer and missing-value policy.
         *
         * @param criteria criterion multipliers
         * @return ranking profile
         * @since 0.22.4
         */
        public static RankingProfile of(List<WeightedCriterion> criteria) {
            return new RankingProfile(criteria, DirectionAwareMinMaxNormalizer.INSTANCE,
                    MissingValuePolicy.WORST_SCORE);
        }

        /**
         * Creates a profile with default normalizer and missing-value policy.
         *
         * @param criteria criterion multipliers
         * @return ranking profile
         * @since 0.22.4
         */
        public static RankingProfile of(WeightedCriterion... criteria) {
            Objects.requireNonNull(criteria, "criteria");
            return of(List.of(criteria));
        }
    }

    /**
     * Ranked statement row with composite and per-criterion details.
     *
     * @param statement        trading statement
     * @param compositeScore   weighted normalized composite score
     * @param rawScores        raw criterion scores by criterion
     * @param normalizedScores normalized criterion scores by criterion
     * @param originalIndex    original statement index before ranking
     * @since 0.22.4
     */
    record RankedTradingStatement(TradingStatement statement, Num compositeScore, Map<AnalysisCriterion, Num> rawScores,
            Map<AnalysisCriterion, Num> normalizedScores, int originalIndex) {

        /**
         * Creates a validated ranked statement row.
         *
         * @param statement        trading statement
         * @param compositeScore   weighted normalized composite score
         * @param rawScores        raw criterion scores by criterion
         * @param normalizedScores normalized criterion scores by criterion
         * @param originalIndex    original statement index before ranking
         * @since 0.22.4
         */
        public RankedTradingStatement {
            Objects.requireNonNull(statement, "statement");
            Objects.requireNonNull(compositeScore, "compositeScore");
            rawScores = Collections
                    .unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(rawScores, "rawScores")));
            normalizedScores = Collections
                    .unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(normalizedScores, "normalizedScores")));
            if (originalIndex < 0) {
                throw new IllegalArgumentException("originalIndex must be >= 0");
            }
        }
    }

    private static Num compareSafe(Num value, NumFactory numFactory) {
        if (Num.isNaNOrNull(value)) {
            return numFactory.minusOne();
        }
        return value;
    }

    private static int compareScores(Num left, Num right) {
        NumFactory numFactory = left != null && !left.isNaN() ? left.getNumFactory()
                : right != null && !right.isNaN() ? right.getNumFactory() : null;
        if (numFactory == null) {
            return 0;
        }
        Num leftComparable = compareSafe(left, numFactory);
        Num rightComparable = compareSafe(right, numFactory);
        return leftComparable.compareTo(rightComparable);
    }

    private static Num normalizeToFactory(Num value, NumFactory numFactory) {
        if (Num.isNaNOrNull(value)) {
            return NaN.NaN;
        }
        if (numFactory.produces(value)) {
            return value;
        }
        return numFactory.numOf(value.doubleValue());
    }

    private static Num clamp01(Num value, NumFactory numFactory) {
        if (Num.isNaNOrNull(value)) {
            return NaN.NaN;
        }
        Num normalizedValue = normalizeToFactory(value, numFactory);
        Num zero = numFactory.zero();
        Num one = numFactory.one();
        if (normalizedValue.isLessThan(zero)) {
            return zero;
        }
        if (normalizedValue.isGreaterThan(one)) {
            return one;
        }
        return normalizedValue;
    }

    private static Num handleMissing(MissingValuePolicy policy, NumFactory numFactory,
            Map<AnalysisCriterion, Num> normalizedScores, AnalysisCriterion criterion) {
        if (policy == MissingValuePolicy.EXCLUDE_STATEMENT) {
            return null;
        }
        if (policy == MissingValuePolicy.RENORMALIZE_WEIGHTS) {
            normalizedScores.put(criterion, NaN.NaN);
            return numFactory.zero();
        }
        Num worst = numFactory.zero();
        normalizedScores.put(criterion, worst);
        return worst;
    }

    private static Map<AnalysisCriterion, Num> normalizeWeights(List<WeightedCriterion> weightedCriteria,
            NumFactory numFactory) {
        Num totalMultiplier = numFactory.zero();
        for (WeightedCriterion weightedCriterion : weightedCriteria) {
            Num multiplier = normalizeToFactory(weightedCriterion.multiplier(), numFactory);
            if (Num.isNaNOrNull(multiplier) || multiplier.isNegative()) {
                throw new IllegalArgumentException("criterion multiplier must be finite and >= 0");
            }
            totalMultiplier = totalMultiplier.plus(multiplier);
        }
        if (totalMultiplier.isZero()) {
            throw new IllegalArgumentException("sum of criterion multipliers must be > 0");
        }

        Map<AnalysisCriterion, Num> normalizedWeights = new LinkedHashMap<>();
        for (WeightedCriterion weightedCriterion : weightedCriteria) {
            Num multiplier = normalizeToFactory(weightedCriterion.multiplier(), numFactory);
            normalizedWeights.put(weightedCriterion.criterion(), multiplier.dividedBy(totalMultiplier));
        }
        return Collections.unmodifiableMap(normalizedWeights);
    }

    private static Num[] findBestWorst(List<Num> values, AnalysisCriterion criterion) {
        Num bestValue = null;
        Num worstValue = null;
        for (Num value : values) {
            if (Num.isNaNOrNull(value)) {
                continue;
            }
            if (bestValue == null) {
                bestValue = value;
                worstValue = value;
                continue;
            }
            if (criterion.betterThan(value, bestValue)) {
                bestValue = value;
            }
            if (criterion.betterThan(worstValue, value)) {
                worstValue = value;
            }
        }
        if (bestValue == null) {
            return new Num[] { NaN.NaN, NaN.NaN };
        }
        return new Num[] { bestValue, worstValue };
    }
}
