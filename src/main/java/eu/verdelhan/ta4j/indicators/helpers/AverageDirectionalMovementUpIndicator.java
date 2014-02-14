package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

public class AverageDirectionalMovementUpIndicator extends CachedIndicator<Double> {

	private final int timeFrame;

	private final DirectionalMovementUpIndicator dmup;

	public AverageDirectionalMovementUpIndicator(TimeSeries series, int timeFrame) {
		this.timeFrame = timeFrame;
		dmup = new DirectionalMovementUpIndicator(series);
	}

	@Override
	protected Double calculate(int index) {
		//TODO: Retornar 1 mesmo?
		if (index == 0)
			return 1d;
		return (getValue(index - 1) * (timeFrame - 1) / timeFrame) + (dmup.getValue(index) * 1 / timeFrame);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName()+ " timeFrame: " + timeFrame;
	}
}
