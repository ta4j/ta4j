package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import java.util.List;

/**
 * Number of ticks in trades.
 */
public class NumberOfTicksCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        int nTicks = 0;
        for (Trade trade : trades) {
            nTicks += calculate(series, trade);
        }
        return nTicks;
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        return (1 + trade.getExit().getIndex()) - trade.getEntry().getIndex();
    }
}
