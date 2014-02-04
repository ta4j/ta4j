package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
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
