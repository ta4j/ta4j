package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;


public class OpenPriceIndicator implements Indicator<Double> {

	private TimeSeries data;

	public OpenPriceIndicator(TimeSeries data) {
		this.data = data;
	}

	@Override
	public Double getValue(int index) {
		return data.getTick(index).getOpenPrice();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}