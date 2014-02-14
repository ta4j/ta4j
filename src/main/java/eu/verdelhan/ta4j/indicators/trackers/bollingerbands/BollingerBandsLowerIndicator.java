package eu.verdelhan.ta4j.indicators.trackers.bollingerbands;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.indicators.helpers.StandardDeviationIndicator;

/**
 * Buy - Occurs when the price line cross from down to up de Bollinger Band Low.
 * Sell - Occurs when the price line cross from up to down de Bollinger Band
 * High.
 * 
 */
public class BollingerBandsLowerIndicator implements Indicator<Double> {

	private final Indicator<? extends Number> indicator;

	private final BollingerBandsMiddleIndicator bbm;

	public BollingerBandsLowerIndicator(BollingerBandsMiddleIndicator bbm, StandardDeviationIndicator standardDeviation) {
		this.bbm = bbm;
		this.indicator = standardDeviation;
	}

	public BollingerBandsLowerIndicator(BollingerBandsMiddleIndicator bbm, Indicator<? extends Number> indicator) {
		this.bbm = bbm;
		this.indicator = indicator;
	}

	@Override
	public Double getValue(int index) {
		return bbm.getValue(index).doubleValue() - 2 * indicator.getValue(index).doubleValue();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "deviation: " + indicator + "series: " + bbm;
	}
}
