package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;

public class DirectionalUpIndicator implements Indicator<Double>{

	private final Indicator<Double> admup;
	private final Indicator<Double> atr;
	private int timeFrame;

	public DirectionalUpIndicator(TimeSeries series, int timeFrame) {
		this.admup = new AverageDirectionalMovementUpIndicator(series, timeFrame);
		this.atr = new AverageTrueRangeIndicator(series, timeFrame);
		this.timeFrame = timeFrame;
	}

	@Override
	public Double getValue(int index) {
		
		return  admup.getValue(index) / atr.getValue(index);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " timeFrame: " + timeFrame;
	}
}
