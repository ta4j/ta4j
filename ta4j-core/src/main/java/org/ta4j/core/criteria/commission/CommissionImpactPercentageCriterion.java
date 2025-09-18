package org.ta4j.core.criteria.commission;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.pnl.GrossProfitLossCriterion;
import org.ta4j.core.num.Num;

/**
 * Analysis criterion that measures how much commission reduces gross profit.
 *
 * <p>It expresses the commission impact as the percentage of the gross profit or
 * loss.</p>
 *
 * @since 0.19
 */
public final class CommissionImpactPercentageCriterion extends AbstractAnalysisCriterion {

    private static final GrossProfitLossCriterion GROSS_PROFIT_LOSS_CRITERION = new GrossProfitLossCriterion();
    private static final CommissionCriterion COMMISSION_CRITERION = new CommissionCriterion();

    /**
     * Calculates the commission percentage impact for a single position.
     *
     * @param s the bar series used for number creation
     * @param p the evaluated position
     * @return the percentage of commission relative to gross profit or zero when
     *         there is no gross profit
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries s, Position p) {
        var numFactory = s.numFactory();
        var zero = numFactory.zero();
        var gross = p.getGrossProfit().abs();
        var comm = zero;
        if (p.getEntry() != null) {
            var model = p.getEntry().getCostModel();
            var transactionCost = model.calculate(p);
            if (transactionCost != null) {
                comm = transactionCost.abs();
            }
        }
        return gross.isZero() ? zero : comm.dividedBy(gross).multipliedBy(numFactory.hundred());
    }

    /**
     * Calculates the commission percentage impact for all positions in a trading
     * record.
     *
     * @param s the bar series used for number creation
     * @param r the trading record containing the positions to evaluate
     * @return the percentage of commission relative to the record gross profit or
     *         zero when there is no gross profit
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries s, TradingRecord r) {
        var gross = GROSS_PROFIT_LOSS_CRITERION.calculate(s, r).abs();
        var comm = COMMISSION_CRITERION.calculate(s, r).abs();
        var numFactory = s.numFactory();
        var zero = numFactory.zero();
        var hundred = numFactory.hundred();
        return gross.isZero() ? zero : comm.dividedBy(gross).multipliedBy(hundred);
    }

    /**
     * Indicates whether the first percentage is preferable to the second.
     *
     * @param a the first value to compare
     * @param b the second value to compare
     * @return {@code true} when the first value is lower
     * @since 0.19
     */
    @Override
    public boolean betterThan(Num a, Num b) {
        return a.isLessThan(b);
    }

}
