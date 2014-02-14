package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.helpers.AverageGainIndicator;
import eu.verdelhan.ta4j.indicators.helpers.AverageLossIndicator;

public class RSIIndicator implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public RSIIndicator(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	@Override
	public Double getValue(int index) {
		return 100d - 100d / (1 + relativeStrength(index));
	}

	@Override
	public String toString() {
		return getClass().getName() + " timeFrame: " + timeFrame;
	}

	/**
	 * @param index
	 * @return
	 */
	private Double relativeStrength(int index) {
		if (index == 0)
			return 0d;
		AverageGainIndicator averageGain = new AverageGainIndicator(indicator, timeFrame);
		AverageLossIndicator averageLoss = new AverageLossIndicator(indicator, timeFrame);
		return averageGain.getValue(index) / averageLoss.getValue(index);
	}
}
