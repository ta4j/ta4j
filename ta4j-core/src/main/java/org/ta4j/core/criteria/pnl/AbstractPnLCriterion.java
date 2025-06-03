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
import org.ta4j.core.num.Num;

/**
 * Base class for profit and loss criteria.
 */
public abstract class AbstractPnLCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (position.isClosed()) {
            return calculatePosition(position);
        }
        return series.numFactory().zero();
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var zero = series.numFactory().zero();
        return tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(this::calculatePosition)
                .reduce(zero, Num::plus);
    }

    /**
     * Calculates the criterion value for the given position.
     *
     * @param position the trading position
     * @return the value for the position
     */
    protected abstract Num calculatePosition(Position position);

    /**
     * Returns a zero value using the position's num factory.
     *
     * @param position the position
     * @return zero in the same num scale
     */
    protected Num zero(Position position) {
        return position.getEntry().getNetPrice().getNumFactory().zero();
    }

    /**
     * Returns the net profit or loss of the position (includes costs).
     *
     * @param position the position
     * @return the net profit or loss
     */
    protected Num netPnL(Position position) {
        return position.getProfit();
    }

    /**
     * Returns the gross profit or loss of the position (excludes costs).
     *
     * @param position the position
     * @return the gross profit or loss
     */
    protected Num grossPnL(Position position) {
        return position.getGrossProfit();
    }

    /**
     * Returns the profit part of the given pnl, otherwise zero.
     *
     * @param pnl  the profit or loss
     * @param zero the zero value
     * @return the profit or zero
     */
    protected Num profit(Num pnl, Num zero) {
        if (pnl.isPositive()) {
            return pnl;
        }
        return zero;
    }

    /**
     * Returns the loss part of the given pnl, otherwise zero.
     *
     * @param pnl  the profit or loss
     * @param zero the zero value
     * @return the loss or zero
     */
    protected Num loss(Num pnl, Num zero) {
        if (pnl.isNegative()) {
            return pnl;
        }
        return zero;
    }
}
