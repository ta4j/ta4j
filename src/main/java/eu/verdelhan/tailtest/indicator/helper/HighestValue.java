package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.Indicator;

public class HighestValue implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public HighestValue(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	@Override
	public Double getValue(int index) {
		int start = Math.max(0, index - timeFrame + 1);
		Double highest = (Double) indicator.getValue(start);
		for (int i = start + 1; i <= index; i++) {
			if (highest.doubleValue() < indicator.getValue(i).doubleValue())
				highest = (Double) indicator.getValue(i);
		}
		return highest;
	}

	@Override
	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
