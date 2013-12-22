package net.sf.tail.indicator.tracker;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;
import net.sf.tail.indicator.helper.DirectionalDownIndicator;
import net.sf.tail.indicator.helper.DirectionalUpIndicator;

public class DirectionalMovementIndicator implements Indicator<Double>{

	
	private final int timeFrame;
	private final DirectionalUpIndicator dup;
	private final DirectionalDownIndicator ddown;
	public DirectionalMovementIndicator(TimeSeries series, int timeFrame) {
		this.timeFrame = timeFrame;
		dup = new DirectionalUpIndicator(series, timeFrame);
		ddown = new DirectionalDownIndicator(series, timeFrame);
	}
	public String getName() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}

	public Double getValue(int index) {
		double dupValue = dup.getValue(index);
		double ddownValue = ddown.getValue(index);
		
		return (Math.abs((dupValue - ddownValue)) / (dupValue + ddownValue)) * 100;
	}

}
