package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

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
		double yh = series.getTick(index - 1).getMaxPrice();
		double th = series.getTick(index).getMaxPrice();
		double yl = series.getTick(index - 1).getMinPrice();
		double tl = series.getTick(index).getMinPrice();
		
		if((yh >= th && yl <= tl) || th - yh == yl - tl)
			return 0d;
		if(th - yh > yl - tl)
			return th - yh;
		return 0d;				
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}
}
