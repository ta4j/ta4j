package eu.verdelhan.tailtest.sample;

import eu.verdelhan.tailtest.analysis.evaluator.Decision;
import eu.verdelhan.tailtest.runner.HistoryRunner;

/**
 * A sample decision.
 */
public class SampleDecision extends Decision {

	private double value;

	public SampleDecision(double value) {
		super(null, null, 0, null, null, new HistoryRunner(null, null));
		this.value = value;
	}

	@Override
	public double evaluateCriterion() {
		return this.value;
	}
}
