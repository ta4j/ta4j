/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

import java.util.Objects;
import java.util.Optional;

/**
 * Active return between two return criteria.
 *
 * <p>
 * Active return is the arithmetic difference between a primary return and a
 * benchmark return:
 *
 * <pre>
 * activeReturn = primaryReturn - benchmarkReturn
 * </pre>
 *
 * <p>
 * Both wrapped criteria must be {@link ReturnCriterion return criteria} and
 * must expose a {@link ReturnRepresentation}. Their calculated values are
 * normalized to 0-based rates of return before subtraction, then the active
 * return is formatted with the configured output representation. A value of
 * {@code 0.0} in {@link ReturnRepresentation#DECIMAL} or {@code 0.0} in
 * {@link ReturnRepresentation#PERCENTAGE} means the primary criterion matched
 * the benchmark criterion. A positive value means outperformance, and a
 * negative value means underperformance.
 *
 * <p>
 * This criterion measures percentage-point active return. It does not divide by
 * the benchmark return magnitude. Use {@link VersusEnterAndHoldCriterion} for
 * the existing normalized enter-and-hold comparison.
 *
 * @see ActiveReturnVersusEnterAndHoldCriterion
 * @see ReturnCriterion
 * @see ReturnRepresentation
 * @since 0.22.7
 */
public class ActiveReturnCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion primaryCriterion;
    private final AnalysisCriterion benchmarkCriterion;
    private final ReturnRepresentation primaryReturnRepresentation;
    private final ReturnRepresentation benchmarkReturnRepresentation;
    private final ReturnRepresentation returnRepresentation;

    /**
     * Constructor with {@link ReturnRepresentation#DECIMAL} output.
     *
     * @param primaryCriterion   the return criterion to compare
     * @param benchmarkCriterion the return benchmark criterion
     * @throws IllegalArgumentException if either criterion does not expose a
     *                                  {@link ReturnRepresentation}, or if either
     *                                  criterion is not a return criterion or is
     *                                  itself a relative-return criterion
     * @throws NullPointerException     if either criterion is {@code null}
     * @since 0.22.7
     */
    public ActiveReturnCriterion(AnalysisCriterion primaryCriterion, AnalysisCriterion benchmarkCriterion) {
        this(primaryCriterion, benchmarkCriterion, ReturnRepresentation.DECIMAL);
    }

    /**
     * Constructor with explicit output representation.
     *
     * @param primaryCriterion     the return criterion to compare
     * @param benchmarkCriterion   the return benchmark criterion
     * @param returnRepresentation the representation to use for the active-return
     *                             output
     * @throws IllegalArgumentException if either criterion does not expose a
     *                                  {@link ReturnRepresentation}, or if either
     *                                  criterion is not a return criterion or is
     *                                  itself a relative-return criterion
     * @throws NullPointerException     if any argument is {@code null}
     * @since 0.22.7
     */
    public ActiveReturnCriterion(AnalysisCriterion primaryCriterion, AnalysisCriterion benchmarkCriterion,
            ReturnRepresentation returnRepresentation) {
        this.primaryCriterion = Objects.requireNonNull(primaryCriterion, "primaryCriterion");
        this.benchmarkCriterion = Objects.requireNonNull(benchmarkCriterion, "benchmarkCriterion");
        this.primaryReturnRepresentation = validateReturnCriterion("primaryCriterion", this.primaryCriterion);
        this.benchmarkReturnRepresentation = validateReturnCriterion("benchmarkCriterion", this.benchmarkCriterion);
        this.returnRepresentation = Objects.requireNonNull(returnRepresentation, "returnRepresentation");
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        Num primaryValue = primaryCriterion.calculate(series, position);
        Num benchmarkValue = benchmarkCriterion.calculate(series, position);
        return calculateActiveReturn(primaryValue, benchmarkValue);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        Num primaryValue = primaryCriterion.calculate(series, tradingRecord);
        Num benchmarkValue = benchmarkCriterion.calculate(series, tradingRecord);
        return calculateActiveReturn(primaryValue, benchmarkValue);
    }

    @Override
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.of(returnRepresentation);
    }

    /** The higher the active return, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    private Num calculateActiveReturn(Num primaryValue, Num benchmarkValue) {
        Num primaryRate = primaryReturnRepresentation.toRateOfReturn(primaryValue);
        Num benchmarkRate = benchmarkReturnRepresentation.toRateOfReturn(benchmarkValue);
        Num activeReturn = primaryRate.minus(benchmarkRate);
        return returnRepresentation.toRepresentationFromRateOfReturn(activeReturn);
    }

    private static ReturnRepresentation validateReturnCriterion(String role, AnalysisCriterion criterion) {
        rejectRelativeReturnCriterion(role, criterion);
        if (!isReturnCriterion(criterion)) {
            throw new IllegalArgumentException(role + " must be a ReturnCriterion.");
        }
        Optional<ReturnRepresentation> representation = getReturnRepresentation(criterion);
        if (representation.isEmpty()) {
            throw new IllegalArgumentException(role + " must expose a ReturnRepresentation.");
        }
        return representation.get();
    }

    private static boolean isReturnCriterion(AnalysisCriterion criterion) {
        if (criterion instanceof ReturnCriterion) {
            return true;
        }
        if (criterion instanceof EnterAndHoldCriterion) {
            return isReturnCriterion(((EnterAndHoldCriterion) criterion).getCriterion());
        }
        return false;
    }

    private static void rejectRelativeReturnCriterion(String role, AnalysisCriterion criterion) {
        if (criterion instanceof ActiveReturnCriterion) {
            throw new IllegalArgumentException(role + " cannot be an instance of ActiveReturnCriterion.");
        }
        if (criterion instanceof ActiveReturnVersusEnterAndHoldCriterion) {
            throw new IllegalArgumentException(
                    role + " cannot be an instance of ActiveReturnVersusEnterAndHoldCriterion.");
        }
        if (criterion instanceof VersusEnterAndHoldCriterion) {
            throw new IllegalArgumentException(role + " cannot be an instance of VersusEnterAndHoldCriterion.");
        }
    }

    private static Optional<ReturnRepresentation> getReturnRepresentation(AnalysisCriterion criterion) {
        if (criterion instanceof AbstractAnalysisCriterion) {
            AbstractAnalysisCriterion abstractCriterion = (AbstractAnalysisCriterion) criterion;
            return abstractCriterion.getReturnRepresentation();
        }
        return Optional.empty();
    }
}
