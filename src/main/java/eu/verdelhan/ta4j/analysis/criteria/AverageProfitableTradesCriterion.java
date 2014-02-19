package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.OperationType;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;

import java.util.List;

public class AverageProfitableTradesCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, Trade trade) {
		int entryIndex = trade.getEntry().getIndex();
		int exitIndex = trade.getExit().getIndex();

		double result;
        if (trade.getEntry().getType() == OperationType.BUY) {
            result = series.getTick(exitIndex).getClosePrice() / series.getTick(entryIndex).getClosePrice();
        } else {
            result = series.getTick(entryIndex).getClosePrice() / series.getTick(exitIndex).getClosePrice();
        }

		return (result > 1d) ? 1d : 0d;
    }

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        int numberOfProfitable = 0;
        for (Trade trade : trades) {
			int entryIndex = trade.getEntry().getIndex();
			int exitIndex = trade.getExit().getIndex();
			
            if (trade.getEntry().getType() == OperationType.BUY) {
				double result = series.getTick(exitIndex).getClosePrice() / series.getTick(entryIndex).getClosePrice();
                if (result > 1d) {
                    numberOfProfitable++;
                }
            } else if ((series.getTick(entryIndex).getClosePrice() / series.getTick(exitIndex).getClosePrice()) > 1d) {
                numberOfProfitable++;
            }
        }
        return (double) numberOfProfitable / trades.size();
    }
}
