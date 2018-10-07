package org.ta4j.core.analysis.criteria;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Reward risk ratio criterion.
 * </p>
 * (i.e. the {@link TotalProfitCriterion total profit} over the {@link MaximumDrawdownCriterion maximum drawdown}.
 */
public class RewardRiskRatioCriterion extends AbstractAnalysisCriterion {

    private AnalysisCriterion totalProfit = new TotalProfitCriterion();

    private AnalysisCriterion maxDrawdown = new MaximumDrawdownCriterion();

    @Override
    public Num calculate(TimeSeries series, TradingRecord tradingRecord) {
        return totalProfit.calculate(series, tradingRecord).dividedBy(maxDrawdown.calculate(series, tradingRecord));
    }

    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isGreaterThan(criterionValue2);
    }

    @Override
    public Num calculate(TimeSeries series, Trade trade) {
        return totalProfit.calculate(series, trade).dividedBy(maxDrawdown.calculate(series, trade));
    }
}
