package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

public class StandardDeviationIndicator implements Indicator<Double> {

	private Indicator<? extends Number> indicator;

	private int timeFrame;

	private SMAIndicator sma;

	public StandardDeviationIndicator(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
		sma = new SMAIndicator(indicator, timeFrame);
	}

	@Override
	public Double getValue(int index) {
		double standardDeviation = 0.0;
		double average = sma.getValue(index);
		for (int i = Math.max(0, index - timeFrame + 1); i <= index; i++) {
			standardDeviation += Math.pow(indicator.getValue(i).doubleValue() - average, 2.0);
		}
		return Math.sqrt(standardDeviation);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
