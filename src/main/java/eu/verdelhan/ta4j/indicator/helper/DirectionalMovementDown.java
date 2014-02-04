package eu.verdelhan.ta4j.indicator.helper;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;

public class DirectionalMovementDown implements Indicator<Double>{

	private TimeSeries series;

	public DirectionalMovementDown(TimeSeries series) {
		this.series = series;
	}

	@Override
	public Double getValue(int index) {
		if(index == 0)
			return 0d;
		double yh = series.getTick(index - 1).getMaxPrice().doubleValue();
		double th = series.getTick(index).getMaxPrice().doubleValue();
		double yl = series.getTick(index - 1).getMinPrice().doubleValue();
		double tl = series.getTick(index).getMinPrice().doubleValue();
		
		if((yh >= th && yl <= tl) || th - yh >= yl - tl)
			return 0d;
		return yl - tl;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
