package eu.verdelhan.tailtest.analysis.criteria;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import java.util.List;

public class NumberOfTradesCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        return trades.size();
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        return 1d;
    }
}
