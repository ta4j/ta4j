package eu.verdelhan.ta4j.indicators.trackers.bollingerbands;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;

/**
 * Buy - Occurs when the price line cross from down to up de Bollinger Band Low.
 * Sell - Occurs when the price line cross from up to down de Bollinger Band
 * High.
 * 
 */
public class BollingerBandsMiddleIndicator implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	public BollingerBandsMiddleIndicator(SMAIndicator smaIndicator) {
		this.indicator = smaIndicator;
	}

	public BollingerBandsMiddleIndicator(Indicator<? extends Number> indicator) {
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
