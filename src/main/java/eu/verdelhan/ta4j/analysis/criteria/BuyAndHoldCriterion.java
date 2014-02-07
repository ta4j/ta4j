package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.TAUtils;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.evaluator.Decision;
import java.util.ArrayList;
import java.util.List;

public class BuyAndHoldCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        return series.getTick(series.getEnd()).getClosePrice().divide(series.getTick(series.getBegin()).getClosePrice(), TAUtils.MATH_CONTEXT).doubleValue();
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
            return series.getTick(exitIndex).getClosePrice().divide(series.getTick(entryIndex).getClosePrice(), TAUtils.MATH_CONTEXT).doubleValue();
        } else {
            return series.getTick(entryIndex).getClosePrice().divide(series.getTick(exitIndex).getClosePrice(), TAUtils.MATH_CONTEXT).doubleValue();
        }
    }
}
