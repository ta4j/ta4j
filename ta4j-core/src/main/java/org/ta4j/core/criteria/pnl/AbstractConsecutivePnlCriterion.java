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
 * Base class for consecutive PnL criteria.
 * <p>
 * It computes the best cumulative streak over a {@link TradingRecord}:
 * subclasses define what counts as a contributing position (via
 * {@link #accepts(Num)}) and how to choose the preferred streak value (via
 * {@link #preferForStreak(Num, Num)}).
 *
 * @since 0.19
 */
public abstract class AbstractConsecutivePnlCriterion extends AbstractAnalysisCriterion {

    /**
     * Positive for profit-streaks, negative for loss-streaks.
     */
    protected abstract boolean accepts(Num profit);

    /**
     * For profit-streaks choose greater, for loss-streaks choose smaller (more
     * severe).
     */
    protected abstract boolean preferForStreak(Num candidate, Num best);

    /**
     * Returns the profit or loss of the position.
     *
     * @param series   the bar series used for number creation
     * @param position the evaluated position
     * @return the profit or loss of the position, or zero when it is not a profit
     *         or a loss
     */
    @Override
    public Num calculate(BarSeries series, Position position) {
        var zero = series.numFactory().zero();
        if (position.isNew() || position.getEntry() == null) {
            return zero;
        }
        var profit = position.getProfit();
        return accepts(profit) ? profit : zero;
    }

    /**
     * Determines the biggest cumulative profit or loss across consecutive winning
     * or losing positions in the trading record.
     *
     * @param series        the bar series used for number creation
     * @param tradingRecord the trading record containing the positions to scan
     * @return the best or worst consecutive profit or loss or zero when there are
     *         no wins or losses
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var zero = series.numFactory().zero();
        var current = zero;
        var best = zero;

        for (var position : tradingRecord.getPositions()) {
            var contribution = position.isClosed() ? calculate(series, position) : zero;
            if (!contribution.isZero()) {
                current = current.plus(contribution);
                if (preferForStreak(current, best)) {
                    best = current;
                }
            } else {
                current = zero;
            }
        }
        return best;
    }
}
