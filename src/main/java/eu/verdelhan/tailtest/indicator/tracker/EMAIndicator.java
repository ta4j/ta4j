package net.sf.tail.indicator.tracker;

import net.sf.tail.Indicator;
import net.sf.tail.indicator.cache.CachedIndicator;

//TODO: pequena explicacao
public class EMAIndicator extends CachedIndicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public EMAIndicator(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	private double multiplier() {
		return 2 / (double) (timeFrame + 1);
	}

	protected Double calculate(int index) {
		if (index + 1 < timeFrame)
			return new SMAIndicator(indicator, timeFrame).getValue(index);

		if(index == 0)
			return indicator.getValue(0).doubleValue();
		double emaPrev = getValue(index - 1).doubleValue();
		return ((indicator.getValue(index).doubleValue() - emaPrev) * multiplier()) + emaPrev;
	}

	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
