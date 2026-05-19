/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.optimization.ga;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.math3.genetics.AbstractListChromosome;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

/**
 * Commons Math chromosome wrapper for a decoded ta4j candidate.
 *
 * @param <C> decoded candidate context type
 * @since 0.22.7
 */
public class CandidateChromosome<C> extends AbstractListChromosome<Integer> {

    private final CandidateCodec<C> codec;
    private final EvaluationContext<C> evaluationContext;

    /**
     * Creates a chromosome with its own evaluation cache.
     *
     * @param representation allele indexes in domain order
     * @param codec          chromosome decoder
     * @param evaluator      fitness evaluator
     * @since 0.22.7
     */
    public CandidateChromosome(List<Integer> representation, CandidateCodec<C> codec,
            CandidateFitnessEvaluator<C> evaluator) {
        this(representation, codec, new EvaluationContext<>(codec, evaluator));
    }

    CandidateChromosome(List<Integer> representation, CandidateCodec<C> codec, EvaluationContext<C> evaluationContext) {
        super(List.copyOf(Objects.requireNonNull(representation, "representation")), false);
        this.codec = Objects.requireNonNull(codec, "codec");
        this.evaluationContext = Objects.requireNonNull(evaluationContext, "evaluationContext");
        this.codec.validateRepresentation(getRepresentation());
    }

    @Override
    protected void checkValidity(List<Integer> representation) {
        // Validation runs after construction once the codec is available.
    }

    /**
     * @return stable candidate identifier
     * @since 0.22.7
     */
    public String candidateId() {
        return evaluated().decodedCandidate().id();
    }

    /**
     * @return decoded candidate context
     * @since 0.22.7
     */
    public C context() {
        return evaluated().decodedCandidate().context();
    }

    /**
     * @return decoded parameter values
     * @since 0.22.7
     */
    public CandidateCodec.ParameterValues parameterValues() {
        return evaluated().decodedCandidate().parameters();
    }

    /**
     * @return evaluated fitness score before double conversion
     * @since 0.22.7
     */
    public Num fitnessScore() {
        return evaluated().fitnessScore();
    }

    /**
     * @return decoded candidate bundle
     * @since 0.22.7
     */
    public CandidateCodec.DecodedCandidate<C> decodedCandidate() {
        return evaluated().decodedCandidate();
    }

    @Override
    public CandidateChromosome<C> newFixedLengthChromosome(List<Integer> representation) {
        return new CandidateChromosome<>(representation, codec, evaluationContext);
    }

    @Override
    public double fitness() {
        return evaluated().fitnessDouble();
    }

    List<Integer> representationCopy() {
        return new ArrayList<>(getRepresentation());
    }

    private EvaluationRecord<C> evaluated() {
        return evaluationContext.evaluate(getRepresentation());
    }

    static int compareScores(Num left, Num right) {
        double leftValue = toFitnessDouble(left);
        double rightValue = toFitnessDouble(right);
        return Double.compare(leftValue, rightValue);
    }

    private static double toFitnessDouble(Num score) {
        if (Num.isNaNOrNull(score)) {
            return Double.NEGATIVE_INFINITY;
        }
        double value = score.doubleValue();
        return Double.isNaN(value) ? Double.NEGATIVE_INFINITY : value;
    }

    static final class EvaluationContext<C> {

        private final CandidateCodec<C> codec;
        private final CandidateFitnessEvaluator<C> evaluator;
        private final Map<String, EvaluationRecord<C>> resultsById = new LinkedHashMap<>();

        EvaluationContext(CandidateCodec<C> codec, CandidateFitnessEvaluator<C> evaluator) {
            this.codec = Objects.requireNonNull(codec, "codec");
            this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        }

        synchronized EvaluationRecord<C> evaluate(List<Integer> representation) {
            CandidateCodec.DecodedCandidate<C> decoded = codec.decode(representation);
            EvaluationRecord<C> cached = resultsById.get(decoded.id());
            if (cached != null) {
                return cached;
            }
            Num fitnessScore = evaluator.evaluate(decoded.context());
            if (fitnessScore == null) {
                fitnessScore = NaN.NaN;
            }
            EvaluationRecord<C> record = new EvaluationRecord<>(decoded, fitnessScore, toFitnessDouble(fitnessScore));
            resultsById.put(decoded.id(), record);
            return record;
        }

        synchronized List<EvaluationRecord<C>> results() {
            return List.copyOf(resultsById.values());
        }
    }

    static record EvaluationRecord<C>(CandidateCodec.DecodedCandidate<C> decodedCandidate, Num fitnessScore,
            double fitnessDouble) {

        EvaluationRecord {
            Objects.requireNonNull(decodedCandidate, "decodedCandidate");
            if (fitnessScore == null) {
                fitnessScore = NaN.NaN;
            }
        }
    }
}
