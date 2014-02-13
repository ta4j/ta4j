package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;

public class DirectionalMovementUp implements Indicator<Double>
{
	private TimeSeries series;

	public DirectionalMovementUp(TimeSeries series) {
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
		
		if((yh >= th && yl <= tl) || th - yh == yl - tl)
			return 0d;
		if(th - yh > yl - tl)
			return th - yh;
		return 0d;				
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
