package eu.verdelhan.ta4j.indicator.helper;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicator.tracker.SMA;

public class StandardDeviation implements Indicator<Double> {

	private Indicator<? extends Number> indicator;

	private int timeFrame;

	private SMA sma;

	public StandardDeviation(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
		sma = new SMA(indicator, timeFrame);
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
