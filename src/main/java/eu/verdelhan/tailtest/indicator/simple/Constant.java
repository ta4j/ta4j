package eu.verdelhan.tailtest.indicator.simple;

import eu.verdelhan.tailtest.Indicator;

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
	public String getName() {
		return getClass().getSimpleName() + " Value: " + value;
	}
}
