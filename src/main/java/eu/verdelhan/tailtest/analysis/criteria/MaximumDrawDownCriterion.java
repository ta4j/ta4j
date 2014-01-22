package eu.verdelhan.tailtest.analysis.criteria;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.flow.CashFlow;
import java.util.ArrayList;
import java.util.List;

public class MaximumDrawDownCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        double maximumDrawDown = 0d;
        double maxPeak = 0;
        CashFlow cashFlow = new CashFlow(series, trades);

        for (int i = series.getBegin(); i <= series.getEnd(); i++) {
            double value = cashFlow.getValue(i).doubleValue();
            if (value > maxPeak) {
                maxPeak = value;
            }

            double drawDown = (maxPeak - value) / maxPeak;
            if (drawDown > maximumDrawDown) {
                maximumDrawDown = drawDown;
                // absolute maximumDrawDown.
                // should it be maximumDrawDown = drawDown/maxPeak ?
            }
        }
        return maximumDrawDown;
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        List<Trade> trades = new ArrayList<Trade>();
        trades.add(trade);
        return calculate(series, trades);

    }
}
