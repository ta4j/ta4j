package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import java.math.BigDecimal;

public class PreviousPrice implements Indicator<BigDecimal> {

	private TimeSeries data;

	public PreviousPrice(TimeSeries data) {
		this.data = data;
	}

	@Override
	public BigDecimal getValue(int index) {
		return data.getTick(Math.max(0, index - 1)).getClosePrice();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
