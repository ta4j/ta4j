package org.ta4j.core.criteria.commission;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.pnl.GrossProfitLossCriterion;
import org.ta4j.core.num.Num;

public final class CommissionImpactPercentageCriterion extends AbstractAnalysisCriterion {

    private static final GrossProfitLossCriterion GROSS_PROFIT_LOSS_CRITERION = new GrossProfitLossCriterion();
    private static final CommissionCriterion COMMISSION_CRITERION = new CommissionCriterion();

    @Override
    public Num calculate(BarSeries s, Position p) {
        var gross = p.getGrossProfit().abs();
        var comm = p.getPositionCost().abs();
        var numFactory = s.numFactory();
        var zero = numFactory.zero();
        var hundred = numFactory.hundred();
        return gross.isZero() ? zero : comm.dividedBy(gross).multipliedBy(hundred);
    }

    @Override
    public Num calculate(BarSeries s, TradingRecord r) {
        var gross = GROSS_PROFIT_LOSS_CRITERION.calculate(s, r).abs();
        var comm = COMMISSION_CRITERION.calculate(s, r).abs();
        var numFactory = s.numFactory();
        var zero = numFactory.zero();
        var hundred = numFactory.hundred();
        return gross.isZero() ? zero : comm.dividedBy(gross).multipliedBy(hundred);
    }

    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isLessThan(b);
    }

}
