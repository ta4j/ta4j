package eu.verdelhan.tailtest.indicator.tracker;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.cache.CachedIndicator;

public class AverageDirectionalMovement extends CachedIndicator<Double>{

	private final int timeFrame;
	private final DirectionalMovement dm;

	public AverageDirectionalMovement(TimeSeries series, int timeFrame) {
		this.timeFrame = timeFrame;
		this.dm = new DirectionalMovement(series, timeFrame);
	}
	@Override
	protected Double calculate(int index) {
		if(index == 0)
			return 1d;
		return (getValue(index - 1) * (timeFrame - 1) / timeFrame) + (dm.getValue(index) / timeFrame);
		
	}

	@Override
	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}

}
