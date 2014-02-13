package eu.verdelhan.ta4j.indicators.trackers.bollingerbands;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.trackers.SMA;

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
