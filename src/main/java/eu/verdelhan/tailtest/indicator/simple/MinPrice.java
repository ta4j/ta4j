package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;
import java.math.BigDecimal;

public class MinPrice implements Indicator<BigDecimal> {

	private TimeSeries data;

	public MinPrice(TimeSeries data) {
		this.data = data;
	}

	@Override
	public BigDecimal getValue(int index) {
		return data.getTick(index).getMinPrice();
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}
}
