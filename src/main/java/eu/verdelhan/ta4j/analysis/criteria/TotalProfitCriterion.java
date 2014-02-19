package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
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
        double exitClosePrice = series.getTick(trade.getExit().getIndex()).getClosePrice();
        double entryClosePrice = series.getTick(trade.getEntry().getIndex()).getClosePrice();

        if (trade.getEntry().getType() == OperationType.BUY) {
            return exitClosePrice / entryClosePrice;
        } else {
			return entryClosePrice / exitClosePrice;
		}
    }
}
