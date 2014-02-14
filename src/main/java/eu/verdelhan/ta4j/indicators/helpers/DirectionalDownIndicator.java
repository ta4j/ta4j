package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;

public class DirectionalDownIndicator implements Indicator<Double>{

	private final Indicator<Double> admdown;
	private final Indicator<Double> atr;
	private final int timeFrame;

	public DirectionalDownIndicator(TimeSeries series, int timeFrame) {
		this.admdown = new AverageDirectionalMovementDownIndicator(series, timeFrame);
		this.atr = new AverageTrueRangeIndicator(series, timeFrame);
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
