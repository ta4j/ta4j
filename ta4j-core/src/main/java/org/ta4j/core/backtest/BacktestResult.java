package org.ta4j.core.backtest;

import java.util.Comparator;

import org.ta4j.core.Strategy;
import org.ta4j.core.num.Num;

/**
 * This class holds the backtesting backtest result of the AnalysisCriterion
 * running against a Strategy within a BarSeries.
 */
public class BacktestResult {

	static final Comparator<BacktestResult> COMPARE = Comparator
			.nullsLast(Comparator.comparing(BacktestResult::getCalculatedNum));

	/**
	 * Constructor.
	 * 
	 * @param strategy      the strategy
	 * @param calculatedNum the associated criterion value
	 */
	public BacktestResult(Strategy strategy, Num calculatedNum) {
		this.strategy = strategy;
		this.calculatedNum = calculatedNum;
		this.accepted = null;
		this.requiredNum = null;
	}

	/**
	 * Constructor.
	 * 
	 * @param strategy      the strategy
	 * @param requiredNum   the required criterion value
	 * @param calculatedNum the calculated criterion value
	 * @param accepted      the acceptance of the criterion tested against a
	 *                      required criterion value
	 */
	public BacktestResult(Strategy strategy, Num requiredNum, Num calculatedNum, Boolean accepted) {
		this.strategy = strategy;
		this.requiredNum = requiredNum;
		this.calculatedNum = calculatedNum;
		this.accepted = accepted;
	}

	private final Strategy strategy;
	private final Num requiredNum;
	private final Num calculatedNum;
	private final Boolean accepted;

	/** @return the Strategy */
	public Strategy getStrategy() {
		return strategy;
	}

	/** @return the calculated value of the criterion */
	public Num getCalculatedNum() {
		return calculatedNum;
	}

	/**
	 * @return the requiredNum value of the criterion (is null if no required
	 *         criterion value was set)
	 */
	public Num getRequiredNum() {
		return requiredNum;
	}

	/**
	 * @return the acceptance of the criterion tested against a required criterion
	 *         value (is null if no required criterion value was set)
	 */
	public Boolean getAccepted() {
		return accepted;
	}

}
