package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.Indicator;

public class HighestValue<T extends Number> implements Indicator<T> {

	private final Indicator<T> indicator;

	private final int timeFrame;

	public HighestValue(Indicator<T> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	@Override
	public T getValue(int index) {
		int start = Math.max(0, index - timeFrame + 1);
		T highest = indicator.getValue(start);
		for (int i = start + 1; i <= index; i++) {
			if (highest.doubleValue() < indicator.getValue(i).doubleValue())
				highest = indicator.getValue(i);
		}
		return highest;
	}

	@Override
	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
