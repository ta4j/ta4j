package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;


public class ClosePriceIndicator implements Indicator<Double> {

	private TimeSeries data;

	public ClosePriceIndicator(TimeSeries data) {
		this.data = data;
	}

	@Override
	public Double getValue(int index) {
		return data.getTick(index).getClosePrice();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}

}
