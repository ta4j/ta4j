package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;

public class LowestValueIndicator<T extends Number> implements Indicator<T> {

	private final Indicator<T> indicator;

	private final int timeFrame;

	public LowestValueIndicator(Indicator<T> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	@Override
	public T getValue(int index) {
		int start = Math.max(0, index - timeFrame + 1);
		T lowest = indicator.getValue(start);
		for (int i = start + 1; i <= index; i++) {
			if (lowest.doubleValue() > indicator.getValue(i).doubleValue())
				lowest = indicator.getValue(i);
		}
		return lowest;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
