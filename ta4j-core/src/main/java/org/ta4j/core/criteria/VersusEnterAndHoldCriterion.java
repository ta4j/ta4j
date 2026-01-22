/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Versus "enter and hold" criterion, returned in the configured
 * {@link ReturnRepresentation} format.
 *
 * <p>
 * Compares the value of a provided {@link AnalysisCriterion criterion} with the
 * value of an {@link EnterAndHoldCriterion} by using the following formula:
 *
 * <pre>
 * xVersusEnterAndHold = (rate_x - rate_enterAndHold) / abs(rate_enterAndHold)
 * </pre>
 *
 * <p>
 * This criterion automatically detects if the provided criterion uses a
 * {@link ReturnRepresentation} by calling
 * {@link AbstractAnalysisCriterion#getReturnRepresentation()}. For criteria
 * using {@link ReturnRepresentation#MULTIPLICATIVE} representation, values are
 * automatically converted to rates (by subtracting 1) before comparison to
 * ensure semantically consistent results across all representations. For other
 * representations (DECIMAL, PERCENTAGE, LOG) or criteria that don't use return
 * representations, the values are used directly as rates.
 *
 * <p>
 * <b>Return Representation:</b> The calculated ratio (which represents how much
 * better or worse the strategy is compared to enter-and-hold) is converted to
 * the configured {@link ReturnRepresentation} format. This criterion defaults
 * to the global default from {@link ReturnRepresentationPolicy}, but you can
 * override it per-instance via the constructor.
 *
 * <p>
 * <b>Usage Examples:</b>
 *
 * <pre>{@code
 * // Default representation (from ReturnRepresentationPolicy)
 * var vsBuyHold = new VersusEnterAndHoldCriterion(TradeType.BUY,
 *         new GrossReturnCriterion(ReturnRepresentation.DECIMAL));
 *
 * // Explicit DECIMAL representation (default for ratios)
 * var vsBuyHoldDecimal = new VersusEnterAndHoldCriterion(TradeType.BUY,
 *         new GrossReturnCriterion(ReturnRepresentation.DECIMAL), BigDecimal.ONE, ReturnRepresentation.DECIMAL);
 * // Result: 0.5 means strategy is 50% better than buy-and-hold
 *
 * // PERCENTAGE representation
 * var vsBuyHoldPercentage = new VersusEnterAndHoldCriterion(TradeType.BUY,
 *         new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE), BigDecimal.ONE, ReturnRepresentation.PERCENTAGE);
 * // Result: 50.0 means strategy is 50% better than buy-and-hold
 *
 * // MULTIPLICATIVE representation
 * var vsBuyHoldMultiplicative = new VersusEnterAndHoldCriterion(TradeType.BUY,
 *         new GrossReturnCriterion(ReturnRepresentation.MULTIPLICATIVE), BigDecimal.ONE,
 *         ReturnRepresentation.MULTIPLICATIVE);
 * // Result: 1.5 means strategy is 50% better (1 + 0.5 = 1.5)
 * }</pre>
 *
 * <p>
 * <b>Ratio Format Examples:</b> A ratio of 0.5 (strategy is 50% better than
 * buy-and-hold) can be expressed as:
 * <ul>
 * <li><b>DECIMAL</b>: 0.5 (50% better)
 * <li><b>PERCENTAGE</b>: 50.0 (50% better)
 * <li><b>MULTIPLICATIVE</b>: 1.5 (1 + 0.5 = 1.5, meaning 50% better)
 * <li><b>LOG</b>: ln(1.5) ≈ 0.405 (log representation)
 * </ul>
 *
 * <p>
 * <b>Calculation Examples:</b>
 * <ul>
 * <li>DECIMAL: Strategy 0.155 vs Buy-and-hold 0.05 → (0.155 - 0.05) / 0.05 =
 * 2.1 (210% better)
 * <li>MULTIPLICATIVE: Strategy 1.155 vs Buy-and-hold 1.05 → Automatically
 * converted to rates: (0.155 - 0.05) / 0.05 = 2.1 (210% better) - same semantic
 * result
 * <li>Absolute values: (400$ / 500$) - 1 = -0.2 (20% worse)
 * </ul>
 *
 * @see ReturnRepresentation
 * @see ReturnRepresentationPolicy
 */
public class VersusEnterAndHoldCriterion extends AbstractAnalysisCriterion {

    private final AnalysisCriterion criterion;
    private final EnterAndHoldCriterion enterAndHoldCriterion;
    private final ReturnRepresentation returnRepresentation;

    /**
     * Constructor with an entry amount of {@code 1} and
     * {@link ReturnRepresentationPolicy#getDefaultRepresentation() global default
     * representation}.
     *
     * @param criterion the criterion to be compared to
     *                  {@link EnterAndHoldCriterion}
     */
    public VersusEnterAndHoldCriterion(AnalysisCriterion criterion) {
        this(TradeType.BUY, criterion);
    }

    /**
     * Constructor with an entry amount of {@code 1} and
     * {@link ReturnRepresentationPolicy#getDefaultRepresentation() global default
     * representation}.
     *
     * @param tradeType the {@link TradeType} used to open the position
     * @param criterion the criterion to be compared to
     *                  {@link EnterAndHoldCriterion}
     */
    public VersusEnterAndHoldCriterion(TradeType tradeType, AnalysisCriterion criterion) {
        this(tradeType, criterion, BigDecimal.ONE);
    }

    /**
     * Constructor with {@link ReturnRepresentationPolicy#getDefaultRepresentation()
     * global default representation}.
     *
     * @param tradeType the {@link TradeType} used to open the position
     * @param criterion the criterion to be compared to
     *                  {@link EnterAndHoldCriterion}
     * @param amount    the amount to be used to hold the entry position; if
     *                  {@code null} then {@code 1} is used.
     * @throws NullPointerException if {@code amount} is {@code null}
     */
    public VersusEnterAndHoldCriterion(TradeType tradeType, AnalysisCriterion criterion, BigDecimal amount) {
        this(tradeType, criterion, amount, ReturnRepresentationPolicy.getDefaultRepresentation());
    }

    /**
     * Constructor with explicit return representation.
     * <p>
     * Use this constructor to specify how the ratio output should be formatted. The
     * ratio represents how much better or worse the strategy is compared to
     * enter-and-hold. See the class javadoc for examples of how ratios are
     * expressed in different formats.
     *
     * @param tradeType            the {@link TradeType} used to open the position
     * @param criterion            the criterion to be compared to
     *                             {@link EnterAndHoldCriterion}
     * @param amount               the amount to be used to hold the entry position;
     *                             if {@code null} then {@code 1} is used.
     * @param returnRepresentation the return representation to use for the output
     *                             ratio (e.g.,
     *                             {@link ReturnRepresentation#DECIMAL},
     *                             {@link ReturnRepresentation#PERCENTAGE},
     *                             {@link ReturnRepresentation#MULTIPLICATIVE})
     * @throws NullPointerException if {@code amount} or
     *                              {@code returnRepresentation} is {@code null}
     */
    public VersusEnterAndHoldCriterion(TradeType tradeType, AnalysisCriterion criterion, BigDecimal amount,
            ReturnRepresentation returnRepresentation) {
        this.criterion = criterion;
        this.enterAndHoldCriterion = new EnterAndHoldCriterion(tradeType, criterion, amount);
        this.returnRepresentation = returnRepresentation;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var x = criterion.calculate(series, position);
        var enterAndHold = enterAndHoldCriterion.calculate(series, position);
        return calculateComparison(x, enterAndHold, series.numFactory());
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        if (series.isEmpty()) {
            return series.numFactory().one();
        }

        var x = criterion.calculate(series, tradingRecord);
        var enterAndHold = enterAndHoldCriterion.calculate(series, tradingRecord);
        return calculateComparison(x, enterAndHold, series.numFactory());
    }

    /**
     * Calculates the comparison ratio, normalizing MULTIPLICATIVE values to rates
     * first to ensure semantically consistent results. The ratio is then converted
     * to the configured {@link ReturnRepresentation} format.
     *
     * @param x            the strategy value
     * @param enterAndHold the enter-and-hold value
     * @param numFactory   the number factory
     * @return the comparison ratio in the configured return representation format
     */
    private Num calculateComparison(Num x, Num enterAndHold, NumFactory numFactory) {
        // Calculate the ratio as a rate of return (0-based)
        Num ratio;

        // Check if the criterion uses MULTIPLICATIVE representation
        Optional<ReturnRepresentation> representation = getReturnRepresentation(criterion);
        if (representation.isPresent() && representation.get() == ReturnRepresentation.MULTIPLICATIVE) {
            // Convert MULTIPLICATIVE values to rates before comparison
            // This ensures semantic consistency: 1.155 vs 1.05 should give the same
            // result as 0.155 vs 0.05 (both represent 15.5% vs 5% returns)
            var one = numFactory.one();
            var rateX = x.minus(one);
            var rateEnterAndHold = enterAndHold.minus(one);
            var absRateEnterAndHold = rateEnterAndHold.abs();
            if (absRateEnterAndHold.isZero()) {
                // Avoid division by zero - return 0 if enter-and-hold had no return
                ratio = numFactory.zero();
            } else {
                ratio = rateX.minus(rateEnterAndHold).dividedBy(absRateEnterAndHold);
            }
        } else {
            // For non-MULTIPLICATIVE representations, use standard formula
            var absEnterAndHold = enterAndHold.abs();
            if (absEnterAndHold.isZero()) {
                // Avoid division by zero
                ratio = numFactory.zero();
            } else {
                var one = numFactory.one();
                if (enterAndHold.isNegative()) {
                    ratio = x.dividedBy(absEnterAndHold).plus(one);
                } else {
                    ratio = x.dividedBy(absEnterAndHold).minus(one);
                }
            }
        }

        // Convert the ratio (which is a rate of return) to the configured
        // representation
        return returnRepresentation.toRepresentationFromRateOfReturn(ratio);
    }

    /**
     * Gets the ReturnRepresentation used by the criterion, if applicable.
     *
     * @param criterion the criterion to check
     * @return the ReturnRepresentation, or empty if not applicable
     */
    private Optional<ReturnRepresentation> getReturnRepresentation(AnalysisCriterion criterion) {
        if (criterion instanceof AbstractAnalysisCriterion) {
            return ((AbstractAnalysisCriterion) criterion).getReturnRepresentation();
        }
        return Optional.empty();
    }

    @Override
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.of(returnRepresentation);
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    @Override
    public String toString() {
        return criterion.getClass().getSimpleName() + " vs. " + enterAndHoldCriterion.getClass().getSimpleName();
    }

}
