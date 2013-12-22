package net.sf.tail.indicator.tracker.bollingerbands;

import net.sf.tail.Indicator;
import net.sf.tail.indicator.tracker.SMAIndicator;

/**
 * Buy - Occurs when the price line cross from down to up de Bollinger Band Low.
 * Sell - Occurs when the price line cross from up to down de Bollinger Band
 * High.
 * 
 * @author Marcio
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

	public Double getValue(int index) {
		return indicator.getValue(index).doubleValue();
	}

	public String getName() {
		return getClass().getSimpleName() + " deviation: " + indicator.getName();
	}
}
