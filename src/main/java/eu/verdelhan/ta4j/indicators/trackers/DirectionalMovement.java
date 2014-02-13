package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.helpers.DirectionalDown;
import eu.verdelhan.ta4j.indicators.helpers.DirectionalUp;

public class DirectionalMovement implements Indicator<Double>{

	
	private final int timeFrame;
	private final DirectionalUp dup;
	private final DirectionalDown ddown;
	public DirectionalMovement(TimeSeries series, int timeFrame) {
		this.timeFrame = timeFrame;
		dup = new DirectionalUp(series, timeFrame);
		ddown = new DirectionalDown(series, timeFrame);
	}

	@Override
	public Double getValue(int index) {
		double dupValue = dup.getValue(index);
		double ddownValue = ddown.getValue(index);
		
		return (Math.abs((dupValue - ddownValue)) / (dupValue + ddownValue)) * 100;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
