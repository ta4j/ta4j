package eu.verdelhan.tailtest.indicator.tracker.bollingerbands;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.indicator.tracker.SMA;

/**
 * Buy - Occurs when the price line cross from down to up de Bollinger Band Low.
 * Sell - Occurs when the price line cross from up to down de Bollinger Band
 * High.
 * 
 */
public class BollingerBandsMiddle implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	public BollingerBandsMiddle(SMA smaIndicator) {
		this.indicator = smaIndicator;
	}

	public BollingerBandsMiddle(Indicator<? extends Number> indicator) {
		this.indicator = indicator;
	}

	@Override
	public Double getValue(int index) {
		return indicator.getValue(index).doubleValue();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " deviation: " + indicator;
	}
}
