package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

public class DirectionalDownIndicator implements Indicator<Double>{

	private final Indicator<Double> admdown;
	private final Indicator<Double> atr;
	private final int timeFrame;

	public DirectionalDownIndicator(TimeSeries series, int timeFrame) {
		this.admdown = new AverageDirectionalMovementDownIndicator(series, timeFrame);
		this.atr = new AverageTrueRangeIndicator(series, timeFrame);
		this.timeFrame = timeFrame;
	}
	public String getName() {
		return getClass().getSimpleName() + " timeFrame: "+ timeFrame;
	}

	public Double getValue(int index) {
		
		return  admdown.getValue(index) / atr.getValue(index);
	}

}
