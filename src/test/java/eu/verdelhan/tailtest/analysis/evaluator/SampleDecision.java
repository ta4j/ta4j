package net.sf.tail.analysis.evaluator;

import net.sf.tail.runner.HistoryRunner;

public class SampleDecision extends Decision {

	private double value;

	public SampleDecision(double value) {
		super(null, null,0, null, null, new HistoryRunner(null,null));
		this.value = value;
	}

	@Override
	public double evaluateCriterion() {
		return this.value;
	}
}
