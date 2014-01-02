package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.Indicator;

public class LowestValue implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public LowestValue(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	public Double getValue(int index) {
		int start = Math.max(0, index - timeFrame + 1);
		Double lowest = (Double) indicator.getValue(start);
		for (int i = start + 1; i <= index; i++) {
			if (lowest.doubleValue() > indicator.getValue(i).doubleValue())
				lowest = (Double) indicator.getValue(i);
		}
		return lowest;
	}

	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
