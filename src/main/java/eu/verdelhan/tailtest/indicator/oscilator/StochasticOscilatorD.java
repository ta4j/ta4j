package net.sf.tail.indicator.oscilator;

import net.sf.tail.Indicator;
import net.sf.tail.indicator.tracker.SMAIndicator;

/**
 * 
 * Receive StochasticOscilatorK and returns his SMA(3)
 * 
 * @author tgthies
 * 
 */
public class StochasticOscilatorD implements Indicator<Double> {

	private Indicator<? extends Number> indicator;

	public StochasticOscilatorD(StochasticOscilatorK k) {
		indicator = new SMAIndicator(k, 3);
	}

	public StochasticOscilatorD(Indicator<? extends Number> indicator) {
		this.indicator = indicator;
	}

	public Double getValue(int index) {

		return indicator.getValue(index).doubleValue();
	}

	public String getName() {
		return getClass().getSimpleName() + " " + indicator.getName();
	}
}
