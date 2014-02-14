package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.CachedIndicator;

public class AverageDirectionalMovementIndicator extends CachedIndicator<Double>{

	private final int timeFrame;
	private final DirectionalMovementIndicator dm;

	public AverageDirectionalMovementIndicator(TimeSeries series, int timeFrame) {
		this.timeFrame = timeFrame;
		this.dm = new DirectionalMovementIndicator(series, timeFrame);
	}

	@Override
	protected Double calculate(int index) {
		if(index == 0)
			return 1d;
		return (getValue(index - 1) * (timeFrame - 1) / timeFrame) + (dm.getValue(index) / timeFrame);
		
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}

}
