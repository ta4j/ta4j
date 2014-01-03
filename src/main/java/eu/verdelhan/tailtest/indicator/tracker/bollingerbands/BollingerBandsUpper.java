package eu.verdelhan.tailtest.indicator.tracker.bollingerbands;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.indicator.helper.StandardDeviation;

/**
 * Buy - Occurs when the price line cross from down to up de Bollinger Band Low.
 * Sell - Occurs when the price line cross from up to down de Bollinger Band
 * High.
 * 
 * @author Marcio
 * 
 */
public class BollingerBandsUpper implements Indicator<Double> {
	private final Indicator<? extends Number> indicator;

	private final BollingerBandsMiddle bbm;

	public BollingerBandsUpper(BollingerBandsMiddle bbm, StandardDeviation standardDeviation) {
		this.bbm = bbm;
		this.indicator = standardDeviation;
	}

	public BollingerBandsUpper(BollingerBandsMiddle bbm, Indicator<? extends Number> indicator) {
		this.bbm = bbm;
		this.indicator = indicator;
	}

	@Override
	public Double getValue(int index) {
		return bbm.getValue(index).doubleValue() + 2 * indicator.getValue(index).doubleValue();
	}

	@Override
	public String getName() {
		return getClass().getSimpleName() + "deviation: " + indicator.getName() + "series" + bbm.getName();
	}
}
