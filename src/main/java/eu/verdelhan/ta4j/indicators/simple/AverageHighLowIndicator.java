package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import java.math.BigDecimal;

public class AverageHighLowIndicator implements Indicator<BigDecimal> {

	private TimeSeries data;

	public AverageHighLowIndicator(TimeSeries data) {
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
