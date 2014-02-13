package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import java.math.BigDecimal;

public class Amount implements Indicator<BigDecimal> {

	private TimeSeries data;

	public Amount(TimeSeries data) {
		this.data = data;
	}

	@Override
	public BigDecimal getValue(int index) {
		return data.getTick(index).getAmount();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}