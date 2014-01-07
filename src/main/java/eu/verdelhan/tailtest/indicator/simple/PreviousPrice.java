package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;
import java.math.BigDecimal;

public class PreviousPrice implements Indicator<BigDecimal> {

	private TimeSeries data;

	public PreviousPrice(TimeSeries data) {
		this.data = data;
	}

	@Override
	public BigDecimal getValue(int index) {
		return data.getTick(index).getPreviousPrice();
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}
}
