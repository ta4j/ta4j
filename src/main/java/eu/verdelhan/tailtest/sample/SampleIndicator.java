package eu.verdelhan.tailtest.sample;

import eu.verdelhan.tailtest.Indicator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A sample indicator.
 * @param <T>
 */
public class SampleIndicator<T> implements Indicator<T> {

	private List<T> values = new ArrayList<T>();

	public SampleIndicator(T[] values) {
		this.values.addAll(Arrays.asList(values));
	}

	public void addValue(T value) {
		this.values.add(value);
	}

	@Override
	public T getValue(int index) {
		return values.get(index);
	}

	public String getName() {
		return toString();
	}
}
