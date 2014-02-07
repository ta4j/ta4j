package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.TAUtils;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
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
            result = series.getTick(exitIndex).getClosePrice().divide(series.getTick(entryIndex).getClosePrice(), TAUtils.MATH_CONTEXT);
        } else {
            result = series.getTick(entryIndex).getClosePrice().divide(series.getTick(exitIndex).getClosePrice(), TAUtils.MATH_CONTEXT);
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
				BigDecimal result = series.getTick(exitIndex).getClosePrice().divide(series.getTick(entryIndex).getClosePrice(), TAUtils.MATH_CONTEXT);
                if (result.compareTo(BigDecimal.ONE) == 1) {
                    numberOfProfitable++;
                }
            } else if (series.getTick(entryIndex).getClosePrice().divide(series.getTick(exitIndex).getClosePrice(), TAUtils.MATH_CONTEXT).compareTo(BigDecimal.ONE) == 1) {
                numberOfProfitable++;
            }
        }
        return (double) numberOfProfitable / trades.size();
    }
}
