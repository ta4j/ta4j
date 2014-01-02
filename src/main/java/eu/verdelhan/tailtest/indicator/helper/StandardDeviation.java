package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.indicator.tracker.SMA;

public class StandardDeviation implements Indicator<Double> {

	private Indicator<? extends Number> indicator;

	private int timeFrame;

	private SMA sma;

	public StandardDeviation(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
		sma = new SMA(indicator, timeFrame);
	}

	public Double getValue(int index) {
		double standardDeviation = 0.0;
		double average = sma.getValue(index);
		for (int i = Math.max(0, index - timeFrame + 1); i <= index; i++) {
			standardDeviation += Math.pow(indicator.getValue(i).doubleValue() - average, 2.0);
		}
		return Math.sqrt(standardDeviation);
	}

	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
