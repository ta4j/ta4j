package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;


public class MinPriceIndicator implements Indicator<Double> {

	private TimeSeries data;

	public MinPriceIndicator(TimeSeries data) {
		this.data = data;
	}

	@Override
	public Double getValue(int index) {
		return data.getTick(index).getMinPrice();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
