package eu.verdelhan.tailtest.analysis.criteria;

import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class AverageProfitableTradesCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, Trade trade) {
		int entryIndex = trade.getEntry().getIndex();
		int exitIndex = trade.getExit().getIndex();

		BigDecimal result;
        if (trade.getEntry().getType() == OperationType.BUY) {
            result = series.getTick(exitIndex).getClosePrice().divide(series.getTick(entryIndex).getClosePrice(), RoundingMode.HALF_UP);
        } else {
            result = series.getTick(entryIndex).getClosePrice().divide(series.getTick(exitIndex).getClosePrice(), RoundingMode.HALF_UP);
        }

		return (result.compareTo(BigDecimal.ONE) == 1) ? 1d : 0d;
    }

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        int numberOfProfitable = 0;
        for (Trade trade : trades) {
			int entryIndex = trade.getEntry().getIndex();
			int exitIndex = trade.getExit().getIndex();
			
            if (trade.getEntry().getType() == OperationType.BUY) {
				BigDecimal result = series.getTick(exitIndex).getClosePrice().divide(series.getTick(entryIndex).getClosePrice(), RoundingMode.HALF_UP);
                if (result.compareTo(BigDecimal.ONE) == 1) {
                    numberOfProfitable++;
                }
            } else if (series.getTick(entryIndex).getClosePrice().divide(series.getTick(exitIndex).getClosePrice(), RoundingMode.HALF_UP).compareTo(BigDecimal.ONE) == 1) {
                numberOfProfitable++;
            }
        }
        return (double) numberOfProfitable / trades.size();
    }
}
