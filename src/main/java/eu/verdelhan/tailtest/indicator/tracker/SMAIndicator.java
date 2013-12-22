package net.sf.tail.indicator.tracker;

import net.sf.tail.Indicator;
import net.sf.tail.indicator.cache.CachedIndicator;

public class SMAIndicator extends CachedIndicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public SMAIndicator(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	protected Double calculate(int index) {
		double sum = 0.0;
		for (int i = Math.max(0, index - timeFrame + 1); i <= index; i++) {
			sum += indicator.getValue(i).doubleValue();
		}

		return sum / Math.min(timeFrame, index + 1);
	}

	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}

}
