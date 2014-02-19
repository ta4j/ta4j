package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.CashFlow;
import java.util.ArrayList;
import java.util.List;

public class MaximumDrawDownCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        double maximumDrawDown = 0d;
        double maxPeak = 0;
        CashFlow cashFlow = new CashFlow(series, trades);

        for (int i = series.getBegin(); i <= series.getEnd(); i++) {
            double value = cashFlow.getValue(i);
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
