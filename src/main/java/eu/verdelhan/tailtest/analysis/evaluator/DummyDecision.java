package net.sf.tail.analysis.evaluator;

import java.util.LinkedList;
import java.util.List;

import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.Trade;

public class DummyDecision extends Decision {

	private double value;

	public DummyDecision(double value, TimeSeriesSlicer slicer) {
		super(null, slicer,0, null, new LinkedList<Trade>(), null);
		this.value = value;
	}

	public DummyDecision(List<Trade> trades, TimeSeriesSlicer slicer) {
		super(null, slicer,0, null, trades, null);
	}

	@Override
	public double evaluateCriterion() {
		return this.value;
	}

}
