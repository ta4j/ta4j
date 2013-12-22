package net.sf.tail.indicator.tracker;

import net.sf.tail.Indicator;
import net.sf.tail.indicator.helper.AverageGainIndicator;
import net.sf.tail.indicator.helper.AverageLossIndicator;

public class RSIIndicator implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public RSIIndicator(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	public Double getValue(int index) {
		return 100d - 100d / (1 + relativeStrength(index));
	}

	private Double relativeStrength(int index) {
		if (index == 0)
			return 0d;
		AverageGainIndicator averageGain = new AverageGainIndicator(indicator, timeFrame);
		AverageLossIndicator averageLoss = new AverageLossIndicator(indicator, timeFrame);
		return averageGain.getValue(index) / averageLoss.getValue(index);
	}

	public String getName() {
		return getClass().getName() + " timeFrame: " + timeFrame;
	}
}
