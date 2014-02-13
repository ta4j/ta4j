package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TAUtils;
import eu.verdelhan.ta4j.TimeSeries;
import java.math.BigDecimal;

public class TypicalPrice implements Indicator<BigDecimal> {

	private TimeSeries data;

	public TypicalPrice(TimeSeries data) {
		this.data = data;
	}

	@Override
	public BigDecimal getValue(int index) {
		BigDecimal maxPrice = data.getTick(index).getMaxPrice();
		BigDecimal minPrice = data.getTick(index).getMinPrice();
		BigDecimal closePrice = data.getTick(index).getClosePrice();
		return maxPrice.add(minPrice).add(closePrice).divide(BigDecimal.valueOf(3), TAUtils.MATH_CONTEXT);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
