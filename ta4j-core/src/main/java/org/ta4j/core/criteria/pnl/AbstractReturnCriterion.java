/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.Optional;

/**
 * Base class for return based criteria.
 * <p>
 * Handles calculation of the aggregated return across positions and the output
 * {@link ReturnRepresentation representation}. Internally the criterion works
 * with total returns (a neutral value of {@code 1.0}). The representation is
 * applied before values are returned to callers.
 */
public abstract class AbstractReturnCriterion extends AbstractAnalysisCriterion {

    /**
     * Output representation used for this criterion.
     */
    protected final ReturnRepresentation returnRepresentation;

    /**
     * Constructor with {@link ReturnRepresentationPolicy#getDefaultRepresentation()
     * global default representation}.
     */
    protected AbstractReturnCriterion() {
        this(ReturnRepresentationPolicy.getDefaultRepresentation());
    }

    /**
     * Constructor.
     *
     * @param addBase whether to include the base percentage
     * @deprecated Use {@link #AbstractReturnCriterion(ReturnRepresentation)} to
     *             express the desired return output. This constructor will be
     *             removed in a future release.
     */
    @Deprecated(since = "0.24.0")
    protected AbstractReturnCriterion(boolean addBase) {
        this(ReturnRepresentation.fromAddBase(addBase));
    }

    /**
     * Constructor.
     *
     * @param returnRepresentation the return representation to use
     */
    protected AbstractReturnCriterion(ReturnRepresentation returnRepresentation) {
        this.returnRepresentation = returnRepresentation;
    }

    @Override
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.of(returnRepresentation);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position.isClosed()) {
            // Optimize single position calculation by avoiding unnecessary conversions
            // Calculate total return once, then convert directly to target representation
            var totalReturn = calculateReturn(series, position);
            return convertTotalReturnToRepresentation(totalReturn);
        }
        // Open position: return neutral value directly in target representation
        return getNeutralValue(series.numFactory());
    }

    /**
     * Converts a total return to the target representation format
     *
     * @param totalReturn the total return (1-based)
     * @return the return in the target representation format
     */
    private Num convertTotalReturnToRepresentation(Num totalReturn) {
        if (returnRepresentation == ReturnRepresentation.MULTIPLICATIVE) {
            return totalReturn;
        }
        var numFactory = totalReturn.getNumFactory();
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

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var one = series.numFactory().one();
        var totalReturn = tradingRecord.getPositions()
                .stream()
                .map(p -> calculateReturn(series, p))
                .reduce(one, Num::multipliedBy);
        return returnRepresentation.toRepresentationFromTotalReturn(totalReturn);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    /**
     * Calculates the return of the given closed position including the base.
     *
     * @param series   the bar series
     * @param position the closed position
     * @return the total return of the position (1-based)
     */
    protected abstract Num calculateReturn(BarSeries series, Position position);
}
