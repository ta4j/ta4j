package eu.verdelhan.ta4j;

import eu.verdelhan.ta4j.analysis.evaluators.Decision;
import java.util.List;

/**
 * An analysis criterion.
 */
public interface AnalysisCriterion {

	/**
	 * @param series a time series
	 * @param trade a trade
	 * @return the criterion value for the trade
	 */
    double calculate(TimeSeries series, Trade trade);

	/**
	 * @param series a time series
	 * @param trades a list of trades
	 * @return the criterion value for the trades
	 */
    double calculate(TimeSeries series, List<Trade> trades);

    double summarize(TimeSeries series, List<Decision> decisions);
}