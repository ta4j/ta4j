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

import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.ReturnRepresentationPolicy;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Base class for profit/loss percentage criteria.
 * <p>
 * Calculates the aggregated profit or loss in percent relative to the entry
 * price of each position. Handles the output {@link ReturnRepresentation
 * representation}. Internally the criterion works with rates of return (a
 * neutral value of {@code 0.0}). The representation is applied before values
 * are returned to callers.
 */
public abstract class AbstractProfitLossPercentageCriterion extends AbstractAnalysisCriterion {

    /**
     * Output representation used for this criterion.
     */
    protected final ReturnRepresentation returnRepresentation;

    /**
     * Constructor with {@link ReturnRepresentationPolicy#getDefaultRepresentation()
     * global default representation}.
     */
    protected AbstractProfitLossPercentageCriterion() {
        this(ReturnRepresentationPolicy.getDefaultRepresentation());
    }

    /**
     * Constructor.
     *
     * @param returnRepresentation the return representation to use
     */
    protected AbstractProfitLossPercentageCriterion(ReturnRepresentation returnRepresentation) {
        this.returnRepresentation = returnRepresentation;
    }

    @Override
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.of(returnRepresentation);
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var numFactory = series.numFactory();
        if (position.isClosed()) {
            var entryValue = position.getEntry().getValue();
            if (entryValue.isZero()) {
                // Special case: division by zero - return 0.0 regardless of representation
                return numFactory.zero();
            }
            var rate = profit(position).dividedBy(entryValue);
            var totalReturn = rate.plus(numFactory.one());
            return convertTotalReturnToRepresentation(totalReturn, numFactory);
        }
        return getNeutralValue(numFactory);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();

        var totalProfit = tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(this::profit)
                .reduce(zero, Num::plus);

        var totalEntryPrice = tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(Position::getEntry)
                .map(Trade::getValue)
                .reduce(zero, Num::plus);

        if (totalEntryPrice.isZero()) {
            return getNeutralValue(numFactory);
        }
        var rate = totalProfit.dividedBy(totalEntryPrice);
        var totalReturn = rate.plus(numFactory.one());
        return convertTotalReturnToRepresentation(totalReturn, numFactory);
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
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

    /**
     * Returns the profit or loss for the given position.
     *
     * @param position the position
     * @return the profit or loss
     */
    protected abstract Num profit(Position position);
}
