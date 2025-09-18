package org.ta4j.core.criteria.commission;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

/**
 * Analysis criterion that sums all commissions paid across positions.
 *
 * <p>The criterion relies on each position cost model to determine the paid
 * commission and adds them together for a trading record.</p>
 *
 * @since 0.19
 */
public class CommissionCriterion extends AbstractAnalysisCriterion {

    /**
     * Calculates the commission paid for a single position.
     *
     * @param series the bar series used for number creation
     * @param position the evaluated position
     * @return the commission paid for the provided position or zero when it is open
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, Position position) {
        if (!position.isClosed()) {
            return series.numFactory().zero();
        }
        var model = position.getEntry().getCostModel();
        return model.calculate(position);
    }

    /**
     * Calculates the total commission paid for every closed position in a trading
     * record.
     *
     * @param series the bar series used for number creation
     * @param record the trading record containing the positions to evaluate
     * @return the sum of commissions paid for the record
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord record) {
        var model = record.getTransactionCostModel();
        return record.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(model::calculate)
                .reduce(series.numFactory().zero(), Num::plus);
    }

    /**
     * Indicates whether the first commission value is preferable to the second.
     *
     * @param v1 the first value to compare
     * @param v2 the second value to compare
     * @return {@code true} when the first value is lower
     * @since 0.19
     */
    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isLessThan(v2);
    }
}
