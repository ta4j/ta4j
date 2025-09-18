package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Analysis criterion that finds the most profitable streak of consecutive
 * positions.
 *
 * <p>The criterion sums profits across positive positions and returns the
 * highest value reached.</p>
 *
 * @since 0.19
 */
public class MaxConsecutiveProfitCriterion extends AbstractAnalysisCriterion {

    /**
     * Returns the profit of the position when it ends with a gain.
     *
     * @param series the bar series used for number creation
     * @param position the evaluated position
     * @return the profit of the position or zero when it is not profitable
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, Position position) {
        var profit = position.getProfit();
        return profit.isPositive() ? profit : series.numFactory().zero();
    }

    /**
     * Determines the largest cumulative gain produced by consecutive winning
     * positions in the trading record.
     *
     * @param series the bar series used for number creation
     * @param tradingRecord the trading record containing the positions to scan
     * @return the best consecutive profit or zero when there are no gains
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var zero = series.numFactory().zero();
        var current = zero;
        var max = zero;
        for (var position : tradingRecord.getPositions()) {
            if (position.isClosed() && position.getProfit().isPositive()) {
                current = current.plus(position.getProfit());
            } else {
                if (current.isGreaterThan(max)) {
                    max = current;
                }
                current = zero;
            }
        }
        if (current.isGreaterThan(max)) {
            max = current;
        }
        return max;
    }

    /**
     * Indicates whether the first profit streak is preferable to the second.
     *
     * @param a the first value to compare
     * @param b the second value to compare
     * @return {@code true} when the first value is higher (a larger gain)
     * @since 0.19
     */
    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }
}
