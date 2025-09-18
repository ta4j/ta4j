package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.num.Num;

public final class MaxPositionNetProfitCriterion extends AbstractAnalysisCriterion {

    @Override
    public Num calculate(BarSeries s, Position p) {
        return p.getProfit();
    }

    @Override
    public Num calculate(BarSeries barSeries, TradingRecord tradingRecord) {
        return tradingRecord.getPositions().stream()
                .filter(Position::isClosed)
                .map(Position::getProfit)
                .filter(Num::isPositive)
                .max(Num::compareTo)
                .orElse(barSeries.numFactory().zero());
    }

    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isGreaterThan(b);
    }

}
