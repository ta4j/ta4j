package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import java.math.BigDecimal;

public class MaxPrice implements Indicator<BigDecimal> {

	private TimeSeries data;

	public MaxPrice(TimeSeries data) {
		this.data = data;
	}

	@Override
	public BigDecimal getValue(int index) {
		return data.getTick(index).getMaxPrice();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
