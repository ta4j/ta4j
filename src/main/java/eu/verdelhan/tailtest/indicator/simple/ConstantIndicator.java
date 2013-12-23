package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;

public class ConstantIndicator<T extends Number> implements Indicator<T> {

	private T value;

	public ConstantIndicator(T t) {
		this.value = t;
	}

	public T getValue(int index) {
		return value;
	}

	public String getName() {
		return getClass().getSimpleName() + " Value: " + value;
	}
}
