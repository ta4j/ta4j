package net.sf.tail.indicator.oscilator;

import net.sf.tail.Indicator;
import net.sf.tail.indicator.cache.CachedIndicator;
import net.sf.tail.indicator.tracker.SMAIndicator;

public class AwesomeOscillatorIndicator extends CachedIndicator<Double> {

	private SMAIndicator sma5;

	private SMAIndicator sma34;

	public AwesomeOscillatorIndicator(Indicator<? extends Number> average, int timeFrameSma1, int timeFrameSma2) {
		this.sma5 = new SMAIndicator(average, timeFrameSma1);
		this.sma34 = new SMAIndicator(average, timeFrameSma2);
	}

	public AwesomeOscillatorIndicator(Indicator<? extends Number> average) {
		this.sma5 = new SMAIndicator(average, 5);
		this.sma34 = new SMAIndicator(average, 34);
	}

	@Override
	protected Double calculate(int index) {
		return sma5.getValue(index) - sma34.getValue(index);
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}
}
