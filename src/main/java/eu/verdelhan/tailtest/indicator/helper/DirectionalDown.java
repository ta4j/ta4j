package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

public class DirectionalDown implements Indicator<Double>{

	private final Indicator<Double> admdown;
	private final Indicator<Double> atr;
	private final int timeFrame;

	public DirectionalDown(TimeSeries series, int timeFrame) {
		this.admdown = new AverageDirectionalMovementDown(series, timeFrame);
		this.atr = new AverageTrueRange(series, timeFrame);
		this.timeFrame = timeFrame;
	}

	@Override
	public Double getValue(int index) {
		
		return  admdown.getValue(index) / atr.getValue(index);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timeFrame: "+ timeFrame;
	}
}
