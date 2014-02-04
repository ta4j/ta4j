package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import java.util.ArrayList;
import java.util.List;

public class RewardRiskRatioCriterion extends AbstractAnalysisCriterion {

    private AnalysisCriterion totalProfit = new TotalProfitCriterion();

    private MaximumDrawDownCriterion maxDrawnDown = new MaximumDrawDownCriterion();

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        return totalProfit.calculate(series, trades) / maxDrawnDown.calculate(series, trades);
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        List<Trade> trades = new ArrayList<Trade>();
        trades.add(trade);
        return calculate(series, trades);
    }
}
