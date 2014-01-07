package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;
import java.math.BigDecimal;

public class OpenPrice implements Indicator<BigDecimal> {

	private TimeSeries data;

	public OpenPrice(TimeSeries data) {
		this.data = data;
	}

	@Override
	public BigDecimal getValue(int index) {
		return data.getTick(index).getOpenPrice();
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}
}