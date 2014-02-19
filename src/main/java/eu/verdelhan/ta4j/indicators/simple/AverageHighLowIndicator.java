package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;


public class AverageHighLowIndicator implements Indicator<Double> {

	private TimeSeries data;

	public AverageHighLowIndicator(TimeSeries data) {
		this.data = data;
	}

	@Override
	public Double getValue(int index) {
		return (data.getTick(index).getMaxPrice() + data.getTick(index).getMinPrice()) / 2d;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
