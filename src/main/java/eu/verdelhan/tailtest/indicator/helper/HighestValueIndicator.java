package net.sf.tail.indicator.helper;

import net.sf.tail.Indicator;

public class HighestValueIndicator implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public HighestValueIndicator(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	public Double getValue(int index) {
		int start = Math.max(0, index - timeFrame + 1);
		Double highest = (Double) indicator.getValue(start);
		for (int i = start + 1; i <= index; i++) {
			if (highest.doubleValue() < indicator.getValue(i).doubleValue())
				highest = (Double) indicator.getValue(i);
		}
		return highest;
	}

	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
