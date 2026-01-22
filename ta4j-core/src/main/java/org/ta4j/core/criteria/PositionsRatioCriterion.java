/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Calculates the percentage of losing or winning positions, returned in the
 * configured {@link ReturnRepresentation} format.
 *
 * <ul>
 * <li>For {@link #positionFilter} = {@link PositionFilter#PROFIT}:
 * <code>number of winning positions / total number of positions</code>
 * <li>For {@link #positionFilter} = {@link PositionFilter#LOSS}:
 * <code>number of losing positions / total number of positions</code>
 * </ul>
 *
 * <p>
 * <b>Return Representation:</b> This criterion defaults to
 * {@link ReturnRepresentation#DECIMAL} (ratios are typically expressed as
 * decimals), but you can override it via the constructor. The calculated ratio
 * (which represents the percentage of positions) is converted to the configured
 * representation format.
 *
 * <p>
 * <b>Usage Examples:</b>
 *
 * <pre>{@code
 * // Default DECIMAL representation
 * var winRatio = PositionsRatioCriterion.WinningPositionsRatioCriterion();
 * // Result: 0.5 means 50% of positions are winning
 *
 * // PERCENTAGE representation
 * var winRatioPercentage = new PositionsRatioCriterion(PositionFilter.PROFIT, ReturnRepresentation.PERCENTAGE);
 * // Result: 50.0 means 50% of positions are winning
 *
 * // MULTIPLICATIVE representation
 * var winRatioMultiplicative = new PositionsRatioCriterion(PositionFilter.PROFIT, ReturnRepresentation.MULTIPLICATIVE);
 * // Result: 1.5 means 50% of positions are winning (1 + 0.5 = 1.5)
 * }</pre>
 *
 * <p>
 * <b>Ratio Format Examples:</b> A ratio of 0.5 (50% winning positions) can be
 * expressed as:
 * <ul>
 * <li><b>DECIMAL</b>: 0.5 (50% of positions)
 * <li><b>PERCENTAGE</b>: 50.0 (50% of positions)
 * <li><b>MULTIPLICATIVE</b>: 1.5 (1 + 0.5 = 1.5)
 * </ul>
 *
 * @see ReturnRepresentation
 * @see ReturnRepresentationPolicy
 */
public class PositionsRatioCriterion extends AbstractAnalysisCriterion {

    private final PositionFilter positionFilter;
    private final AnalysisCriterion numberOfPositionsCriterion;
    private final ReturnRepresentation returnRepresentation;

    /**
     * @return {@link PositionsRatioCriterion} with {@link PositionFilter#PROFIT}
     *         and {@link ReturnRepresentationPolicy#getDefaultRepresentation()
     *         global default representation}
     */
    public static PositionsRatioCriterion WinningPositionsRatioCriterion() {
        return new PositionsRatioCriterion(PositionFilter.PROFIT);
    }

    /**
     * @return {@link PositionsRatioCriterion} with {@link PositionFilter#LOSS} and
     *         {@link ReturnRepresentationPolicy#getDefaultRepresentation() global
     *         default representation}
     */
    public static PositionsRatioCriterion LosingPositionsRatioCriterion() {
        return new PositionsRatioCriterion(PositionFilter.LOSS);
    }

    /**
     * Constructor with {@link ReturnRepresentation#DECIMAL} as the default (ratios
     * are typically expressed as decimals).
     * <p>
     * The ratio output will be in DECIMAL format (e.g., 0.5 means 50% of positions
     * are winning/losing). Use the other constructor to specify a different
     * representation.
     *
     * @param positionFilter consider either the winning or losing positions
     */
    public PositionsRatioCriterion(PositionFilter positionFilter) {
        this(positionFilter, ReturnRepresentation.DECIMAL);
    }

    /**
     * Constructor with explicit return representation.
     * <p>
     * Use this constructor to specify how the ratio output should be formatted. The
     * ratio represents the percentage of winning or losing positions. See the class
     * javadoc for examples of how ratios are expressed in different formats.
     *
     * @param positionFilter       consider either the winning or losing positions
     * @param returnRepresentation the return representation to use for the output
     *                             ratio (e.g.,
     *                             {@link ReturnRepresentation#DECIMAL},
     *                             {@link ReturnRepresentation#PERCENTAGE},
     *                             {@link ReturnRepresentation#MULTIPLICATIVE})
     */
    public PositionsRatioCriterion(PositionFilter positionFilter, ReturnRepresentation returnRepresentation) {
        this.positionFilter = positionFilter;
        this.returnRepresentation = returnRepresentation;
        if (positionFilter == PositionFilter.PROFIT) {
            this.numberOfPositionsCriterion = new NumberOfWinningPositionsCriterion();
        } else {
            this.numberOfPositionsCriterion = new NumberOfLosingPositionsCriterion();
        }
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        return numberOfPositionsCriterion.calculate(series, position);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        Num numberOfPositions = numberOfPositionsCriterion.calculate(series, tradingRecord);
        // Calculate the ratio as a rate of return (0-based)
        var ratio = numberOfPositions.dividedBy(numFactory.numOf(tradingRecord.getPositionCount()));
        // Convert the ratio to the configured representation
        return returnRepresentation.toRepresentationFromRateOfReturn(ratio);
    }

    @Override
    public java.util.Optional<ReturnRepresentation> getReturnRepresentation() {
        return java.util.Optional.of(returnRepresentation);
    }

    /**
     * <ul>
     * <li>For {@link PositionFilter#PROFIT}: The higher the criterion value, the
     * better.
     * <li>For {@link PositionFilter#LOSS}: The lower the criterion value, the
     * better.
     * </ul>
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return positionFilter == PositionFilter.PROFIT ? criterionValue1.isGreaterThan(criterionValue2)
                : criterionValue1.isLessThan(criterionValue2);
    }
}
