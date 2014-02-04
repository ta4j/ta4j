package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.Indicator;

public class SimpleMultiplier implements Indicator<Double> {

	private Indicator<? extends Number> indicator;
	private double value;
	

	public SimpleMultiplier(Indicator<? extends Number> indicator, double value) {
		this.indicator = indicator;
		this.value = value;
	}

	@Override
	public Double getValue(int index) {
		return indicator.getValue(index).doubleValue() * value;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " Value: " + value;
	}
}
