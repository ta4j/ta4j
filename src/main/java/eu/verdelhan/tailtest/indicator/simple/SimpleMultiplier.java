package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;

public class SimpleMultiplier implements Indicator<Double> {

	private Indicator<? extends Number> indicator;
	private double value;
	

	public SimpleMultiplier(Indicator<? extends Number> indicator, double value) {
		this.indicator = indicator;
		this.value = value;
	}

	public Double getValue(int index) {
		return indicator.getValue(index).doubleValue() * value;
	}

	public String getName() {
		return getClass().getSimpleName() + " Value: " + value;
	}
}
