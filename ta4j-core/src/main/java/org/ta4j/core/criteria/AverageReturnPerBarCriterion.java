/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.Optional;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Calculates the average return per bar criterion, honoring the configured
 * return representation.
 *
 * <p>
 * It uses the following formula to accurately capture the compounding effect of
 * returns over the specified number of bars:
 *
 * <pre>
 * AverageReturnPerBar = pow({@link NetReturnCriterion net return}, 1/ {@link NumberOfBarsCriterion number of bars})
 * </pre>
 */
public class AverageReturnPerBarCriterion extends AbstractAnalysisCriterion {

    private final NetReturnCriterion netReturn;
    private final ReturnRepresentation returnRepresentation;
    private final NumberOfBarsCriterion numberOfBars = new NumberOfBarsCriterion();

    public AverageReturnPerBarCriterion() {
        this(ReturnRepresentationPolicy.getDefaultRepresentation());
    }

    public AverageReturnPerBarCriterion(ReturnRepresentation returnRepresentation) {
        this.returnRepresentation = returnRepresentation;
        this.netReturn = new NetReturnCriterion(returnRepresentation);
    }

    @Override
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.of(returnRepresentation);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var bars = numberOfBars.calculate(series, position);
        // If a simple division was used (net return/bars), compounding would not be
        // considered, leading to inaccuracies in the calculation.
        // Therefore, we need to use "pow" to accurately capture the compounding effect.
        var numFactory = series.numFactory();
        if (bars.isZero()) {
            return getNeutralValue(numFactory);
        }
        var representedReturn = netReturn.calculate(series, position);
        var totalReturn = returnRepresentation.toTotalReturn(representedReturn);
        var one = numFactory.one();
        var perBarTotalReturn = totalReturn.pow(one.dividedBy(bars));
        return convertTotalReturnToRepresentation(perBarTotalReturn, numFactory);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var bars = numberOfBars.calculate(series, tradingRecord);
        var numFactory = series.numFactory();
        if (bars.isZero()) {
            return getNeutralValue(numFactory);
        }
        var representedReturn = this.netReturn.calculate(series, tradingRecord);
        var totalReturn = returnRepresentation.toTotalReturn(representedReturn);
        var one = numFactory.one();
        var perBarTotalReturn = totalReturn.pow(one.dividedBy(bars));
        return convertTotalReturnToRepresentation(perBarTotalReturn, numFactory);
    }

    /**
     * Converts a total return to the target representation format. Optimized to
     * avoid unnecessary add/subtract operations where possible.
     *
     * @param totalReturn the total return (1-based)
     * @param numFactory  the number factory
     * @return the return in the target representation format
     */
    private Num convertTotalReturnToRepresentation(Num totalReturn, NumFactory numFactory) {
        if (returnRepresentation == ReturnRepresentation.MULTIPLICATIVE) {
            return totalReturn;
        }
        var one = numFactory.one();
        var rateOfReturn = totalReturn.minus(one);

        if (returnRepresentation == ReturnRepresentation.DECIMAL) {
            return rateOfReturn;
        }
        if (returnRepresentation == ReturnRepresentation.PERCENTAGE) {
            return rateOfReturn.multipliedBy(numFactory.numOf(100));
        }
        if (returnRepresentation == ReturnRepresentation.LOG) {
            return totalReturn.log();
        }
        // Fallback to conversion method
        return returnRepresentation.toRepresentationFromTotalReturn(totalReturn);
    }

    /**
     * Returns the neutral value (no return) in the target representation format.
     *
     * @param numFactory the number factory
     * @return the neutral value in the target representation
     */
    private Num getNeutralValue(NumFactory numFactory) {
        if (returnRepresentation == ReturnRepresentation.MULTIPLICATIVE) {
            return numFactory.one();
        }
        // DECIMAL, PERCENTAGE, and LOG all use 0.0 as neutral
        return numFactory.zero();
    }

    /** The higher the criterion value, the better. */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }
}
