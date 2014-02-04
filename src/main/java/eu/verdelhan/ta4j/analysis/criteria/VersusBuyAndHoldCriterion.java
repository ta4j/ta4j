package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.Trade;
import java.util.ArrayList;
import java.util.List;

public class VersusBuyAndHoldCriterion extends AbstractAnalysisCriterion {

    private AnalysisCriterion criterion;

    public VersusBuyAndHoldCriterion(AnalysisCriterion criterion) {
        this.criterion = criterion;
    }

    @Override
    public double calculate(TimeSeries series, List<Trade> trades) {
        List<Trade> fakeTrades = new ArrayList<Trade>();
        fakeTrades.add(new Trade(new Operation(series.getBegin(), OperationType.BUY), new Operation(series.getEnd(),
                OperationType.SELL)));

        return criterion.calculate(series, trades) / criterion.calculate(series, fakeTrades);
    }

    @Override
    public double calculate(TimeSeries series, Trade trade) {
        List<Trade> trades = new ArrayList<Trade>();
        trades.add(trade);
        return calculate(series, trades);
    }

    @Override
    public String toString() {
		StringBuilder sb = new StringBuilder(super.toString());
		sb.append(" (").append(criterion).append(')');
        return sb.toString();
    }
}
