package eu.verdelhan.tailtest.analysis.criteria;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.analysis.evaluator.Decision;
import java.util.LinkedList;
import java.util.List;

/**
 * An abstract analysis criterion.
 */
public abstract class AbstractAnalysisCriterion implements AnalysisCriterion {

	@Override
	public double summarize(TimeSeries series, List<Decision> decisions) {
        List<Trade> trades = new LinkedList<Trade>();
        for (Decision decision : decisions) {
            trades.addAll(decision.getTrades());
        }
        return calculate(series, trades);
	}

	@Override
	public String toString() {
		String[] tokens = getClass().getSimpleName().split("(?=\\p{Lu})", -1);
		StringBuilder sb = new StringBuilder();
		for (int i = 1; i < tokens.length - 1; i++) {
			sb.append(tokens[i]).append(' ');
		}
		sb.deleteCharAt(sb.length() - 1);
		return sb.toString();
	}
}
