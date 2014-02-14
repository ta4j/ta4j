package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.helpers.DirectionalDownIndicator;
import eu.verdelhan.ta4j.indicators.helpers.DirectionalUpIndicator;

public class DirectionalMovementIndicator implements Indicator<Double>{

	
	private final int timeFrame;
	private final DirectionalUpIndicator dup;
	private final DirectionalDownIndicator ddown;
	public DirectionalMovementIndicator(TimeSeries series, int timeFrame) {
		this.timeFrame = timeFrame;
		dup = new DirectionalUpIndicator(series, timeFrame);
		ddown = new DirectionalDownIndicator(series, timeFrame);
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
