package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Indicator;

/**
 * Rate of change (ROCIndicator) indicator.
 * Aka. Momentum
 * The ROCIndicator calculation compares the current value with the value "n" periods ago.
 */
public class ROCIndicator implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public ROCIndicator(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	@Override
	public Double getValue(int index) {
		int nIndex = Math.max(index - timeFrame, 0);
		double nPeriodsAgoValue = indicator.getValue(nIndex).doubleValue();
		double currentValue = indicator.getValue(index).doubleValue();
		return (currentValue - nPeriodsAgoValue) / nPeriodsAgoValue * 100d;
	}
}