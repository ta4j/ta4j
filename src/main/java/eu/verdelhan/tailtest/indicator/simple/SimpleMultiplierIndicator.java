package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;

public class SimpleMultiplierIndicator implements Indicator<Double> {

	private Indicator<? extends Number> indicator;
	private double value;
	

	public SimpleMultiplierIndicator(Indicator<? extends Number> indicator, double value) {
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
