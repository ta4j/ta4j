package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.TimeSeries;
import java.math.BigDecimal;

public class AverageHighLow implements Indicator<BigDecimal> {

	private TimeSeries data;

	public AverageHighLow(TimeSeries data) {
		this.data = data;
	}

	@Override
	public BigDecimal getValue(int index) {
		return data.getTick(index).getMaxPrice().add(data.getTick(index).getMinPrice()).divide(BigDecimal.valueOf(2));
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
