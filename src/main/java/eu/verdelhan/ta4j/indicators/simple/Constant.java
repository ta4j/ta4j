package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.Indicator;

public class Constant<T extends Number> implements Indicator<T> {

	private T value;

	public Constant(T t) {
		this.value = t;
	}

	@Override
	public T getValue(int index) {
		return value;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " Value: " + value;
	}
}
