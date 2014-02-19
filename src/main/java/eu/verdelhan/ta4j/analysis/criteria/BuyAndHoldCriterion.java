package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.evaluators.Decision;
import java.util.ArrayList;
import java.util.List;

public class BuyAndHoldCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        return series.getTick(series.getEnd()).getClosePrice() / series.getTick(series.getBegin()).getClosePrice();
    }

    @Override
    public double summarize(TimeSeries series, List<Decision> decisions) {

        return calculate(series, new ArrayList<Trade>());
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
		int entryIndex = trade.getEntry().getIndex();
		int exitIndex = trade.getExit().getIndex();

        if (trade.getEntry().getType() == OperationType.BUY) {
            return series.getTick(exitIndex).getClosePrice() / series.getTick(entryIndex).getClosePrice();
        } else {
            return series.getTick(entryIndex).getClosePrice() / series.getTick(exitIndex).getClosePrice();
        }
    }
}
