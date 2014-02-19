package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;

public class HighestValueIndicator implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public HighestValueIndicator(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	@Override
	public Double getValue(int index) {
		int start = Math.max(0, index - timeFrame + 1);
		double highest = indicator.getValue(start).doubleValue();
		for (int i = start + 1; i <= index; i++) {
			if (highest < indicator.getValue(i).doubleValue())
				highest = indicator.getValue(i).doubleValue();
		}
		return highest;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
