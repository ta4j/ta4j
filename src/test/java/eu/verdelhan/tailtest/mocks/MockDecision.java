package eu.verdelhan.tailtest.mocks;

import eu.verdelhan.tailtest.TimeSeriesSlicer;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.analysis.evaluator.Decision;
import eu.verdelhan.tailtest.runner.HistoryRunner;
import java.util.LinkedList;
import java.util.List;

public class MockDecision extends Decision {

	private double value;

	public MockDecision(double value) {
		super(null, null, 0, null, null, new HistoryRunner(null, null));
		this.value = value;
	}

	public MockDecision(double value, TimeSeriesSlicer slicer) {
		super(null, slicer, 0, null, new LinkedList<Trade>(), null);
		this.value = value;
	}

	public MockDecision(List<Trade> trades, TimeSeriesSlicer slicer) {
		super(null, slicer, 0, null, trades, null);
	}

	@Override
	public double evaluateCriterion() {
		return this.value;
	}
}