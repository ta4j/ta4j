package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;
import java.math.BigDecimal;

public class Volume implements Indicator<BigDecimal> {

	private TimeSeries data;

	public Volume(TimeSeries data) {
		this.data = data;
	}

	@Override
	public BigDecimal getValue(int index) {
		return data.getTick(index).getVolume();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}