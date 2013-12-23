package eu.verdelhan.tailtest.indicator.tracker.bollingerbands;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.indicator.helper.StandardDeviationIndicator;

/**
 * Buy - Occurs when the price line cross from down to up de Bollinger Band Low.
 * Sell - Occurs when the price line cross from up to down de Bollinger Band
 * High.
 * 
 * @author Marcio
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

	public Double getValue(int index) {
		return bbm.getValue(index).doubleValue() - 2 * indicator.getValue(index).doubleValue();
	}

	public String getName() {
		return getClass().getSimpleName() + "deviation: " + indicator.getName() + "series: " + bbm.getName();
	}
}
