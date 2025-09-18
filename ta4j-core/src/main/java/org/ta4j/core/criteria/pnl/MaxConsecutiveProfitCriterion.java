package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

public class MaxConsecutiveProfitCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        var profit = position.getProfit();
        return profit.isPositive() ? profit : series.numFactory().zero();
    }

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

    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }
}
