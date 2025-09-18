package org.ta4j.core.criteria.commission;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

public class CommissionCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, Position position) {
        if (!position.isClosed()) {
            return series.numFactory().zero();
        }
        var model = position.getEntry().getCostModel();
        return model.calculate(position);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord record) {
        var model = record.getTransactionCostModel();
        return record.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(model::calculate)
                .reduce(series.numFactory().zero(), Num::plus);
    }

    @Override
    public boolean betterThan(Num v1, Num v2) {
        return v1.isLessThan(v2);
    }
}
