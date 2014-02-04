package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import java.util.ArrayList;
import java.util.List;

public class AverageProfitCriterion extends AbstractAnalysisCriterion {

    private AnalysisCriterion totalProfit = new TotalProfitCriterion();

    private AnalysisCriterion numberOfTicks = new NumberOfTicksCriterion();

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        double ticks = numberOfTicks.calculate(series, trades);
        if (ticks == 0) {
            return 1;
        }
        return Math.pow(totalProfit.calculate(series, trades), 1d / ticks);
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        List<Trade> trades = new ArrayList<Trade>();
        trades.add(trade);
        return calculate(series, trades);

    }
}
