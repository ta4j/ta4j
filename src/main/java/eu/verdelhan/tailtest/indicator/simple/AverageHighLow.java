package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;

public class AverageHighLow implements Indicator<Double> {

	private TimeSeries data;

	public AverageHighLow(TimeSeries data) {
		this.data = data;
	}
	
	public Double getValue(int index) {
		return (data.getTick(index).getMaxPrice() + data.getTick(index).getMinPrice()) / 2;
	}
	
	public String getName() {
		return getClass().getSimpleName();
	}
}
