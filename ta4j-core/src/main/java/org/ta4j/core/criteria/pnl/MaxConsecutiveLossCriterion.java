package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Analysis criterion that finds the largest string of consecutive losing
 * positions.
 *
 * <p>The criterion sums the losses of each losing streak and returns the lowest
 * value.</p>
 *
 * @since 0.19
 */
public class MaxConsecutiveLossCriterion extends AbstractAnalysisCriterion {

    /**
     * Returns the loss of the position when it ends in negative territory.
     *
     * @param series the bar series used for number creation
     * @param position the evaluated position
     * @return the loss of the position or zero when it is not a loss
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, Position position) {
        var profit = position.getProfit();
        return profit.isNegative() ? profit : series.numFactory().zero();
    }

    /**
     * Determines the most severe cumulative loss across consecutive losing
     * positions in the trading record.
     *
     * @param series the bar series used for number creation
     * @param tradingRecord the trading record containing the positions to scan
     * @return the worst consecutive loss or zero when there are no losses
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var zero = series.numFactory().zero();
        var current = zero;
        var max = zero;
        for (var position : tradingRecord.getPositions()) {
            if (position.isClosed() && position.getProfit().isNegative()) {
                current = current.plus(position.getProfit());
            } else {
                if (current.isLessThan(max)) {
                    max = current;
                }
                current = zero;
            }
        }
        if (current.isLessThan(max)) {
            max = current;
        }
        return max;
    }

    /**
     * Indicates whether the first loss streak is preferable to the second.
     *
     * @param a the first value to compare
     * @param b the second value to compare
     * @return {@code true} when the first value is higher (a smaller loss)
     * @since 0.19
     */
    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }
}
