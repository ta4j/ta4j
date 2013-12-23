package eu.verdelhan.tailtest.indicator.oscilator;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.indicator.tracker.SMAIndicator;

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
