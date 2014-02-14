package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.TimeSeries;
import java.math.BigDecimal;

public class PriceVariationIndicator implements Indicator<BigDecimal> {

	private TimeSeries data;

	public PriceVariationIndicator(TimeSeries data) {
		this.data = data;
	}

	@Override
	public BigDecimal getValue(int index) {
		BigDecimal previousTickClosePrice = data.getTick(Math.max(0, index - 1)).getClosePrice();
		BigDecimal currentTickClosePrice = data.getTick(index).getClosePrice();
		return currentTickClosePrice.divide(previousTickClosePrice);
	}

	@Override
	public String toString() {
		return getClass().getSimpleName();
	}
}
