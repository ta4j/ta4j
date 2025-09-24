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
 * Analysis criterion that identifies the best net profit among closed
 * positions.
 *
 * <p>
 * The criterion returns the highest profit value found within a trading record.
 * </p>
 *
 * @since 0.19
 */
public final class MaxPositionNetProfitCriterion extends AbstractAnalysisCriterion {

    /**
     * Returns the net profit of the provided position.
     *
     * @param s the bar series used for number creation
     * @param p the evaluated position
     * @return the net profit of the position
     */
    @Override
    public Num calculate(BarSeries s, Position p) {
        if (p.isNew() || p.getEntry() == null) {
            return s.numFactory().zero();
        }
        return p.getProfit();
    }

    /**
     * Finds the largest net profit among all closed positions in the trading
     * record.
     *
     * @param barSeries     the bar series used for number creation
     * @param tradingRecord the trading record containing the positions to scan
     * @return the highest profit value or zero when no position ends in profit
     */
    @Override
    public Num calculate(BarSeries barSeries, TradingRecord tradingRecord) {
        return tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(Position::getProfit)
                .filter(Num::isPositive)
                .max(Num::compareTo)
                .orElse(barSeries.numFactory().zero());
    }

    /**
     * Indicates whether the first profit value is preferable to the second.
     *
     * @param a the first value to compare
     * @param b the second value to compare
     * @return {@code true} when the first value is higher
     */
    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }

}
