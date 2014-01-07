package eu.verdelhan.tailtest.analysis.criteria;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.analysis.evaluator.Decision;
import java.util.LinkedList;
import java.util.List;

public class BrazilianTotalProfitCriterion implements AnalysisCriterion {

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        return calculateProfit(series, trade);
    }

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        double value = 1d;
        for (Trade trade : trades) {
            value *= calculateProfit(series, trade);
        }
        return value;
    }

    @Override
    public double summarize(TimeSeries series, List<Decision> decisions) {
        List<Trade> trades = new LinkedList<Trade>();
        for (Decision decision : decisions) {
            trades.addAll(decision.getTrades());
        }
        return calculate(series, trades);
    }

    private double calculateProfit(TimeSeries series, Trade trade) {
        double exitClosePrice = series.getTick(trade.getExit().getIndex()).getClosePrice().doubleValue();
        double entryClosePrice = series.getTick(trade.getEntry().getIndex()).getClosePrice().doubleValue();

        if (trade.getEntry().getType() == OperationType.BUY) {
            return (exitClosePrice * 0.99965d) / (entryClosePrice * 1.00035d);
        }
        return (entryClosePrice * 0.99965d) / (exitClosePrice * 1.00035d);
    }

    @Override
    public String getName() {
        return "Brazilian Total Profit";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + (this.getClass().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        return true;
    }
}
