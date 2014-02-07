package eu.verdelhan.ta4j.indicator.tracker;

import eu.verdelhan.ta4j.Indicator;

/**
 * Rate of change (ROC) indicator.
 * Aka. Momentum
 * The ROC calculation compares the current value with the value "n" periods ago.
 */
public class ROC implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public ROC(Indicator<? extends Number> indicator, int timeFrame) {
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