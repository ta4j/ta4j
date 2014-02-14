package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

/**
 * Exponential moving average indicator.
 */
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
		if (index + 1 < timeFrame) {
			return new SMAIndicator(indicator, timeFrame).getValue(index);
		}
		if(index == 0) {
			return indicator.getValue(0).doubleValue();
		}
		double emaPrev = getValue(index - 1).doubleValue();
		return ((indicator.getValue(index).doubleValue() - emaPrev) * multiplier()) + emaPrev;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
