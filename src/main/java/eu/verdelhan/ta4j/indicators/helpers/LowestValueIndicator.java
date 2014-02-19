package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;

public class LowestValueIndicator implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public LowestValueIndicator(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	@Override
	public Double getValue(int index) {
		int start = Math.max(0, index - timeFrame + 1);
		double lowest = indicator.getValue(start).doubleValue();
		for (int i = start + 1; i <= index; i++) {
			if (lowest > indicator.getValue(i).doubleValue()) {
				lowest = indicator.getValue(i).doubleValue();
			}
		}
		return lowest;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
