package eu.verdelhan.tailtest;

import eu.verdelhan.tailtest.analysis.evaluator.Decision;

public interface StrategyEvaluator {

	/**
	 * Apply all <code>strategies</code> in <code>series</code>, and return
	 * the best decision according to <code>criterion</code>.
	 * 
	 * @param strategies
	 * @param series
	 * @param criterion
	 * @return <code>Decision</code>
	 */
	Decision evaluate(int slicePosition);
}
