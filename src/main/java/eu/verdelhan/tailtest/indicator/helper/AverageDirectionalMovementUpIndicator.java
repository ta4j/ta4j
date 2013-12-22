package net.sf.tail.indicator.helper;

import net.sf.tail.TimeSeries;
import net.sf.tail.indicator.cache.CachedIndicator;

public class AverageDirectionalMovementUpIndicator extends CachedIndicator<Double> {

	private final int timeFrame;

	private final DirectionalMovementUpIndicator dmup;

	public AverageDirectionalMovementUpIndicator(TimeSeries series, int timeFrame) {
		this.timeFrame = timeFrame;
		dmup = new DirectionalMovementUpIndicator(series);
	}

	public String getName() {
		return getClass().getSimpleName()+ " timeFrame: " + timeFrame;
	}

	@Override
	protected Double calculate(int index) {
		//TODO: Retornar 1 mesmo?
		if (index == 0)
			return 1d;
		return (getValue(index - 1) * (timeFrame - 1) / timeFrame) + (dmup.getValue(index) * 1 / timeFrame);
	}

}
