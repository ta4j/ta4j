package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;

public class DirectionalMovementDownIndicator implements Indicator<Double>{

	private TimeSeries series;

	public DirectionalMovementDownIndicator(TimeSeries series) {
		this.series = series;
	}

	@Override
	public Double getValue(int index) {
		if(index == 0)
			return 0d;
		double yh = series.getTick(index - 1).getMaxPrice();
		double th = series.getTick(index).getMaxPrice();
		double yl = series.getTick(index - 1).getMinPrice();
		double tl = series.getTick(index).getMinPrice();
		
		if((yh >= th && yl <= tl) || th - yh >= yl - tl)
			return 0d;
		return yl - tl;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
