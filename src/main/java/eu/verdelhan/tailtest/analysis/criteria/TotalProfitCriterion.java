package eu.verdelhan.tailtest.analysis.criteria;

import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class TotalProfitCriterion extends AbstractAnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        double value = 1d;
        for (Trade trade : trades) {
            value *= calculateProfit(series, trade);
        }
        return value;
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        return calculateProfit(series, trade);
    }

    private double calculateProfit(TimeSeries series, Trade trade) {
        BigDecimal exitClosePrice = series.getTick(trade.getExit().getIndex()).getClosePrice();
        BigDecimal entryClosePrice = series.getTick(trade.getEntry().getIndex()).getClosePrice();

        if (trade.getEntry().getType() == OperationType.BUY) {
            return exitClosePrice.divide(entryClosePrice, RoundingMode.HALF_UP).doubleValue();
        } else {
			return entryClosePrice.divide(exitClosePrice, RoundingMode.HALF_UP).doubleValue();
		}
    }
}
