/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.ta4j.core.analysis.NamedScoreFunction;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Metric evaluated on a set of walk-forward observations.
 *
 * @param <P> prediction payload type
 * @param <O> realized outcome type
 * @since 0.22.4
 */
public interface WalkForwardMetric<P, O> extends NamedScoreFunction<List<WalkForwardObservation<P, O>>, Num> {

    /**
     * @return unique metric name
     * @since 0.22.4
     */
    @Override
    String name();

    /**
     * Computes metric value for provided observations.
     *
     * @param observations observations to evaluate
     * @return metric value, or {@link NaN#NaN} when unavailable
     * @since 0.22.4
     */
    Num compute(List<WalkForwardObservation<P, O>> observations);

    @Override
    default Num score(List<WalkForwardObservation<P, O>> observations) {
        return compute(observations);
    }

    /**
     * Groups observations by originating snapshot key while preserving encounter
     * order.
     *
     * @param observations observations to group
     * @param <P>          prediction payload type
     * @param <O>          realized outcome type
     * @return snapshot-key grouped observations
     * @since 0.22.4
     */
    static <P, O> Map<String, List<WalkForwardObservation<P, O>>> groupBySnapshot(
            List<WalkForwardObservation<P, O>> observations) {
        Map<String, List<WalkForwardObservation<P, O>>> grouped = new LinkedHashMap<>();
        for (WalkForwardObservation<P, O> observation : observations) {
            String key = observation.snapshot().snapshotKey();
            grouped.computeIfAbsent(key, unused -> new ArrayList<>()).add(observation);
        }
        return grouped;
    }

    /**
     * Clamps the provided value to {@code [0,1]} and maps NaN/null values to
     * {@code 0}.
     *
     * @param value input value
     * @return clamped value
     * @since 0.22.4
     */
    static Num clamp01(Num value) {
        if (Num.isNaNOrNull(value)) {
            return NaN.NaN;
        }
        Num zero = value.getNumFactory().zero();
        Num one = value.getNumFactory().one();
        if (value.isLessThan(zero)) {
            return zero;
        }
        if (value.isGreaterThan(one)) {
            return one;
        }
        return value;
    }

    /**
     * Normalizes the provided value to the requested factory then clamps to
     * {@code [0,1]}.
     *
     * @param value   value to normalize and clamp
     * @param factory target factory
     * @return normalized and clamped value
     * @since 0.22.4
     */
    static Num normalizeAndClamp01(Num value, NumFactory factory) {
        Objects.requireNonNull(factory, "factory");
        if (Num.isNaNOrNull(value)) {
            return factory.zero();
        }
        Num normalized = normalize(value, factory);
        return clamp01(normalized);
    }

    /**
     * Converts a {@link Number} to a normalized and clamped probability in
     * {@code [0,1]}.
     *
     * @param value   numeric value
     * @param factory target factory
     * @return normalized and clamped value
     * @since 0.22.4
     */
    static Num normalizeAndClamp01(Number value, NumFactory factory) {
        Objects.requireNonNull(factory, "factory");
        if (value == null) {
            return factory.zero();
        }
        double primitive = value.doubleValue();
        if (Double.isNaN(primitive) || Double.isInfinite(primitive)) {
            return factory.zero();
        }
        return normalizeAndClamp01(factory.numOf(primitive), factory);
    }

    /**
     * Clamps a probability to the open interval {@code (0,1)}.
     *
     * @param value probability value
     * @return value clamped to {@code [epsilon, 1-epsilon]}
     * @since 0.22.4
     */
    static Num clampOpen01(Num value) {
        if (Num.isNaNOrNull(value)) {
            return NaN.NaN;
        }
        Num clamped = clamp01(value);
        NumFactory factory = clamped.getNumFactory();
        Num epsilon = factory.epsilon();
        Num upper = factory.one().minus(epsilon);
        if (clamped.isLessThan(epsilon)) {
            return epsilon;
        }
        if (clamped.isGreaterThan(upper)) {
            return upper;
        }
        return clamped;
    }

    /**
     * Normalizes the value to the requested factory.
     *
     * @param value   source value
     * @param factory target factory
     * @return normalized value
     * @since 0.22.4
     */
    static Num normalize(Num value, NumFactory factory) {
        Objects.requireNonNull(factory, "factory");
        if (Num.isNaNOrNull(value)) {
            return NaN.NaN;
        }
        if (factory.produces(value)) {
            return value;
        }
        return factory.numOf(value.doubleValue());
    }

    /**
     * Resolves metric factory from observations.
     *
     * @param observations observations
     * @param <P>          prediction payload type
     * @param <O>          outcome type
     * @return resolved factory, or {@link NaN#NaN} factory fallback
     * @since 0.22.4
     */
    static <P, O> NumFactory resolveFactory(List<WalkForwardObservation<P, O>> observations) {
        if (observations != null && !observations.isEmpty()) {
            Num probability = observations.getFirst().prediction().probability();
            if (!Num.isNaNOrNull(probability)) {
                return probability.getNumFactory();
            }
        }
        return DoubleNumFactory.getInstance();
    }

    /**
     * Creates a generic agreement ratio metric at a selected rank.
     *
     * @param name               metric name
     * @param rank               prediction rank to evaluate
     * @param agreementPredicate agreement predicate for prediction/outcome pairs
     * @param <P>                prediction payload type
     * @param <O>                realized outcome type
     * @return metric implementation
     * @since 0.22.4
     */
    static <P, O> WalkForwardMetric<P, O> agreement(String name, int rank,
            BiPredicate<RankedPrediction<P>, O> agreementPredicate) {
        String validatedName = validateName(name);
        int validatedRank = validateRank(rank);
        Objects.requireNonNull(agreementPredicate, "agreementPredicate");
        return new WalkForwardMetric<>() {
            @Override
            public String name() {
                return validatedName;
            }

            @Override
            public Num compute(List<WalkForwardObservation<P, O>> observations) {
                NumFactory factory = resolveFactory(observations);
                int matches = 0;
                int count = 0;
                for (WalkForwardObservation<P, O> observation : observations) {
                    if (observation.prediction().rank() != validatedRank) {
                        continue;
                    }
                    if (agreementPredicate.test(observation.prediction(), observation.realizedOutcome())) {
                        matches++;
                    }
                    count++;
                }
                if (count == 0) {
                    return NaN.NaN;
                }
                return ratio(factory, matches, count);
            }
        };
    }

    /**
     * Creates a binary F1 score metric for one rank.
     *
     * @param name                       metric name
     * @param rank                       prediction rank to evaluate
     * @param predictedPositivePredicate prediction-side positive predicate
     * @param actualPositivePredicate    outcome-side positive predicate
     * @param <P>                        prediction payload type
     * @param <O>                        realized outcome type
     * @return metric implementation
     * @since 0.22.4
     */
    static <P, O> WalkForwardMetric<P, O> binaryF1(String name, int rank,
            BiPredicate<RankedPrediction<P>, O> predictedPositivePredicate, Predicate<O> actualPositivePredicate) {
        String validatedName = validateName(name);
        int validatedRank = validateRank(rank);
        Objects.requireNonNull(predictedPositivePredicate, "predictedPositivePredicate");
        Objects.requireNonNull(actualPositivePredicate, "actualPositivePredicate");
        return new WalkForwardMetric<>() {
            @Override
            public String name() {
                return validatedName;
            }

            @Override
            public Num compute(List<WalkForwardObservation<P, O>> observations) {
                NumFactory factory = resolveFactory(observations);
                int truePositive = 0;
                int falsePositive = 0;
                int falseNegative = 0;

                for (WalkForwardObservation<P, O> observation : observations) {
                    if (observation.prediction().rank() != validatedRank) {
                        continue;
                    }
                    boolean predicted = predictedPositivePredicate.test(observation.prediction(),
                            observation.realizedOutcome());
                    boolean actual = actualPositivePredicate.test(observation.realizedOutcome());

                    if (predicted && actual) {
                        truePositive++;
                    } else if (predicted) {
                        falsePositive++;
                    } else if (actual) {
                        falseNegative++;
                    }
                }

                Num precisionDenominator = factory.numOf(truePositive + falsePositive);
                Num recallDenominator = factory.numOf(truePositive + falseNegative);
                if (precisionDenominator.isZero() || recallDenominator.isZero()) {
                    return factory.zero();
                }
                Num precision = factory.numOf(truePositive).dividedBy(precisionDenominator);
                Num recall = factory.numOf(truePositive).dividedBy(recallDenominator);
                Num denominator = precision.plus(recall);
                if (denominator.isZero()) {
                    return factory.zero();
                }
                return factory.two().multipliedBy(precision).multipliedBy(recall).dividedBy(denominator);
            }
        };
    }

    /**
     * Creates a Brier score metric for a selected rank.
     *
     * @param name                       metric name
     * @param rank                       prediction rank to evaluate
     * @param actualProbabilityExtractor outcome to probability mapper in
     *                                   {@code [0,1]}
     * @param <P>                        prediction payload type
     * @param <O>                        realized outcome type
     * @return metric implementation
     * @since 0.22.4
     */
    static <P, O> WalkForwardMetric<P, O> brierScore(String name, int rank,
            Function<O, Num> actualProbabilityExtractor) {
        String validatedName = validateName(name);
        int validatedRank = validateRank(rank);
        Objects.requireNonNull(actualProbabilityExtractor, "actualProbabilityExtractor");
        return new WalkForwardMetric<>() {
            @Override
            public String name() {
                return validatedName;
            }

            @Override
            public Num compute(List<WalkForwardObservation<P, O>> observations) {
                NumFactory factory = resolveFactory(observations);
                Num sum = factory.zero();
                int count = 0;
                for (WalkForwardObservation<P, O> observation : observations) {
                    if (observation.prediction().rank() != validatedRank) {
                        continue;
                    }
                    Num predicted = normalizeAndClamp01(observation.prediction().probability(), factory);
                    Num actual = normalizeAndClamp01(actualProbabilityExtractor.apply(observation.realizedOutcome()),
                            factory);
                    Num error = predicted.minus(actual);
                    sum = sum.plus(error.multipliedBy(error));
                    count++;
                }
                if (count == 0) {
                    return NaN.NaN;
                }
                return sum.dividedBy(factory.numOf(count));
            }
        };
    }

    /**
     * Creates an expected calibration error metric.
     *
     * @param name                       metric name
     * @param rank                       prediction rank to evaluate
     * @param bins                       number of equal-width probability bins
     * @param actualProbabilityExtractor outcome to probability mapper in
     *                                   {@code [0,1]}
     * @param <P>                        prediction payload type
     * @param <O>                        realized outcome type
     * @return metric implementation
     * @since 0.22.4
     */
    static <P, O> WalkForwardMetric<P, O> expectedCalibrationError(String name, int rank, int bins,
            Function<O, Num> actualProbabilityExtractor) {
        String validatedName = validateName(name);
        int validatedRank = validateRank(rank);
        if (bins <= 0) {
            throw new IllegalArgumentException("bins must be > 0");
        }
        Objects.requireNonNull(actualProbabilityExtractor, "actualProbabilityExtractor");
        return new WalkForwardMetric<>() {
            @Override
            public String name() {
                return validatedName;
            }

            @Override
            public Num compute(List<WalkForwardObservation<P, O>> observations) {
                NumFactory factory = resolveFactory(observations);
                double[] predictedSums = new double[bins];
                double[] actualSums = new double[bins];
                int[] counts = new int[bins];
                int total = 0;

                for (WalkForwardObservation<P, O> observation : observations) {
                    if (observation.prediction().rank() != validatedRank) {
                        continue;
                    }
                    Num predicted = normalizeAndClamp01(observation.prediction().probability(), factory);
                    Num actual = normalizeAndClamp01(actualProbabilityExtractor.apply(observation.realizedOutcome()),
                            factory);

                    double predictedValue = predicted.doubleValue();
                    int index = Math.min(bins - 1, (int) Math.floor(predictedValue * bins));
                    predictedSums[index] += predictedValue;
                    actualSums[index] += actual.doubleValue();
                    counts[index]++;
                    total++;
                }

                if (total == 0) {
                    return NaN.NaN;
                }

                double ece = 0.0;
                for (int i = 0; i < bins; i++) {
                    if (counts[i] == 0) {
                        continue;
                    }
                    double meanPredicted = predictedSums[i] / counts[i];
                    double meanActual = actualSums[i] / counts[i];
                    ece += (Math.abs(meanPredicted - meanActual) * counts[i]) / total;
                }
                return factory.numOf(ece);
            }
        };
    }

    /**
     * Creates a log-loss metric for one rank.
     *
     * @param name                       metric name
     * @param rank                       prediction rank to evaluate
     * @param actualProbabilityExtractor outcome to probability mapper in
     *                                   {@code [0,1]}
     * @param <P>                        prediction payload type
     * @param <O>                        realized outcome type
     * @return metric implementation
     * @since 0.22.4
     */
    static <P, O> WalkForwardMetric<P, O> logLoss(String name, int rank, Function<O, Num> actualProbabilityExtractor) {
        String validatedName = validateName(name);
        int validatedRank = validateRank(rank);
        Objects.requireNonNull(actualProbabilityExtractor, "actualProbabilityExtractor");
        return new WalkForwardMetric<>() {
            @Override
            public String name() {
                return validatedName;
            }

            @Override
            public Num compute(List<WalkForwardObservation<P, O>> observations) {
                NumFactory factory = resolveFactory(observations);
                Num sum = factory.zero();
                int count = 0;
                for (WalkForwardObservation<P, O> observation : observations) {
                    if (observation.prediction().rank() != validatedRank) {
                        continue;
                    }
                    Num predicted = clampOpen01(normalizeAndClamp01(observation.prediction().probability(), factory));
                    Num actual = normalizeAndClamp01(actualProbabilityExtractor.apply(observation.realizedOutcome()),
                            factory);
                    Num one = factory.one();
                    Num loss = actual.multipliedBy(predicted.log())
                            .plus(one.minus(actual).multipliedBy(one.minus(predicted).log()))
                            .negate();
                    sum = sum.plus(loss);
                    count++;
                }
                if (count == 0) {
                    return NaN.NaN;
                }
                return sum.dividedBy(factory.numOf(count));
            }
        };
    }

    /**
     * Creates an NDCG metric at depth {@code k}.
     *
     * @param name              metric name
     * @param k                 ranking depth
     * @param relevanceFunction relevance scoring function
     * @param <P>               prediction payload type
     * @param <O>               realized outcome type
     * @return metric implementation
     * @since 0.22.4
     */
    static <P, O> WalkForwardMetric<P, O> ndcg(String name, int k,
            BiFunction<RankedPrediction<P>, O, Num> relevanceFunction) {
        String validatedName = validateName(name);
        if (k <= 0) {
            throw new IllegalArgumentException("k must be > 0");
        }
        Objects.requireNonNull(relevanceFunction, "relevanceFunction");
        return new WalkForwardMetric<>() {
            @Override
            public String name() {
                return validatedName;
            }

            @Override
            public Num compute(List<WalkForwardObservation<P, O>> observations) {
                NumFactory factory = resolveFactory(observations);
                Map<String, List<WalkForwardObservation<P, O>>> grouped = groupBySnapshot(observations);
                if (grouped.isEmpty()) {
                    return NaN.NaN;
                }

                double ndcgSum = 0.0;
                int count = 0;
                for (List<WalkForwardObservation<P, O>> snapshotRows : grouped.values()) {
                    List<WalkForwardObservation<P, O>> ranked = snapshotRows.stream()
                            .filter(row -> row.prediction().rank() <= k)
                            .sorted(Comparator.comparingInt(row -> row.prediction().rank()))
                            .toList();
                    if (ranked.isEmpty()) {
                        continue;
                    }

                    List<Double> relevance = new ArrayList<>(ranked.size());
                    for (WalkForwardObservation<P, O> row : ranked) {
                        Num raw = relevanceFunction.apply(row.prediction(), row.realizedOutcome());
                        Num normalized = Num.isNaNOrNull(raw) ? factory.zero() : normalize(raw, factory);
                        relevance.add(Math.max(0.0, normalized.doubleValue()));
                    }

                    double dcg = discountedGain(relevance);
                    List<Double> ideal = relevance.stream().sorted(Comparator.reverseOrder()).toList();
                    double idcg = discountedGain(ideal);
                    ndcgSum += idcg == 0.0 ? 0.0 : dcg / idcg;
                    count++;
                }

                if (count == 0) {
                    return NaN.NaN;
                }
                return factory.numOf(ndcgSum / count);
            }
        };
    }

    /**
     * Creates a top-k hit-rate metric.
     *
     * @param name         metric name
     * @param k            top-k depth
     * @param hitPredicate hit predicate for prediction/outcome pairs
     * @param <P>          prediction payload type
     * @param <O>          realized outcome type
     * @return metric implementation
     * @since 0.22.4
     */
    static <P, O> WalkForwardMetric<P, O> topKHitRate(String name, int k,
            BiPredicate<RankedPrediction<P>, O> hitPredicate) {
        String validatedName = validateName(name);
        if (k <= 0) {
            throw new IllegalArgumentException("k must be > 0");
        }
        Objects.requireNonNull(hitPredicate, "hitPredicate");
        return new WalkForwardMetric<>() {
            @Override
            public String name() {
                return validatedName;
            }

            @Override
            public Num compute(List<WalkForwardObservation<P, O>> observations) {
                NumFactory factory = resolveFactory(observations);
                Map<String, List<WalkForwardObservation<P, O>>> grouped = groupBySnapshot(observations);
                if (grouped.isEmpty()) {
                    return NaN.NaN;
                }

                int hits = 0;
                for (List<WalkForwardObservation<P, O>> snapshotRows : grouped.values()) {
                    boolean hit = false;
                    for (WalkForwardObservation<P, O> row : snapshotRows) {
                        if (row.prediction().rank() <= k
                                && hitPredicate.test(row.prediction(), row.realizedOutcome())) {
                            hit = true;
                            break;
                        }
                    }
                    if (hit) {
                        hits++;
                    }
                }
                return ratio(factory, hits, grouped.size());
            }
        };
    }

    private static Num ratio(NumFactory factory, int numerator, int denominator) {
        return factory.numOf(numerator).dividedBy(factory.numOf(denominator));
    }

    private static String validateName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }

    private static int validateRank(int rank) {
        if (rank <= 0) {
            throw new IllegalArgumentException("rank must be > 0");
        }
        return rank;
    }

    private static double discountedGain(List<Double> relevance) {
        double dcg = 0.0;
        for (int i = 0; i < relevance.size(); i++) {
            double gain = Math.pow(2.0, relevance.get(i)) - 1.0;
            dcg += gain / (Math.log(i + 2.0) / Math.log(2.0));
        }
        return dcg;
    }

    /**
     * Computes base-2 logarithm.
     *
     * @param value input value
     * @return base-2 logarithm
     * @since 0.22.4
     */
    static Num log2(Num value) {
        if (Num.isNaNOrNull(value)) {
            return NaN.NaN;
        }
        NumFactory factory = value.getNumFactory();
        Num denominator = factory.two().log();
        if (Num.isNaNOrNull(denominator) || denominator.isZero()) {
            return NaN.NaN;
        }
        return value.log().dividedBy(denominator);
    }
}
