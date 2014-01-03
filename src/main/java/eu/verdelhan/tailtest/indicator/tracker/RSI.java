package eu.verdelhan.tailtest.indicator.tracker;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.indicator.helper.AverageGain;
import eu.verdelhan.tailtest.indicator.helper.AverageLoss;

public class RSI implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public RSI(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	@Override
	public Double getValue(int index) {
		return 100d - 100d / (1 + relativeStrength(index));
	}

	@Override
	public String getName() {
		return getClass().getName() + " timeFrame: " + timeFrame;
	}

	/**
	 * @param index
	 * @return
	 */
	private Double relativeStrength(int index) {
		if (index == 0)
			return 0d;
		AverageGain averageGain = new AverageGain(indicator, timeFrame);
		AverageLoss averageLoss = new AverageLoss(indicator, timeFrame);
		return averageGain.getValue(index) / averageLoss.getValue(index);
	}
}
