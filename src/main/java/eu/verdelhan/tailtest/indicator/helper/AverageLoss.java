package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.Indicator;

public class AverageLoss implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final int timeFrame;

	public AverageLoss(Indicator<? extends Number> indicator, int timeFrame) {
		this.indicator = indicator;
		this.timeFrame = timeFrame;
	}

	@Override
	public Double getValue(int index) {
		double result = 0;
		for (int i = Math.max(1, index - timeFrame + 1); i <= index; i++) {
			if (indicator.getValue(i).doubleValue() < indicator.getValue(i - 1).doubleValue())
				result += indicator.getValue(i - 1).doubleValue() - indicator.getValue(i).doubleValue();
		}
		return result / Math.min(timeFrame, index + 1);
	}

	@Override
	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
