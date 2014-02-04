package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import java.util.ArrayList;
import java.util.List;

public class NumberOfTicksCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        if (trades.isEmpty()) {
            return 0d;
        }

        int nTicks = 0;

        for (Trade trade : trades) {
            nTicks += (1 + trade.getExit().getIndex()) - trade.getEntry().getIndex();
        }
        return nTicks;
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        List<Trade> trades = new ArrayList<Trade>();
        trades.add(trade);
        return calculate(series, trades);
    }
}
