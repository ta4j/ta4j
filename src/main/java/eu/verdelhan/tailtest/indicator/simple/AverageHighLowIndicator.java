package net.sf.tail.indicator.simple;

import net.sf.tail.Indicator;
import net.sf.tail.TimeSeries;

public class AverageHighLowIndicator implements Indicator<Double> {

	private TimeSeries data;

	public AverageHighLowIndicator(TimeSeries data) {
		this.data = data;
	}
	
	public Double getValue(int index) {
		return (data.getTick(index).getMaxPrice() + data.getTick(index).getMinPrice()) / 2;
	}
	
	public String getName() {
		return getClass().getSimpleName();
	}
}
