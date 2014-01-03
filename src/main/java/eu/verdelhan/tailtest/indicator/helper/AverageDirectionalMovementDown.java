package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.cache.CachedIndicator;

public class AverageDirectionalMovementDown extends CachedIndicator<Double>{
	private final int timeFrame;

	private final DirectionalMovementDown dmdown;

	public AverageDirectionalMovementDown(TimeSeries series, int timeFrame) {
		this.timeFrame = timeFrame;
		dmdown = new DirectionalMovementDown(series);
	}

	@Override
	protected Double calculate(int index) {
		//TODO: Retornar 1 mesmo?
		if (index == 0)
			return 1d;
		return (getValue(index - 1) * (timeFrame - 1) / timeFrame) + (dmdown.getValue(index) / timeFrame);
	}

	@Override
	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
