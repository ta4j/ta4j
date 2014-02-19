package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;


public class PriceVariationIndicator implements Indicator<Double> {

	private TimeSeries data;

	public PriceVariationIndicator(TimeSeries data) {
		this.data = data;
	}

	@Override
	public Double getValue(int index) {
		double previousTickClosePrice = data.getTick(Math.max(0, index - 1)).getClosePrice();
		double currentTickClosePrice = data.getTick(index).getClosePrice();
		return currentTickClosePrice / previousTickClosePrice;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
