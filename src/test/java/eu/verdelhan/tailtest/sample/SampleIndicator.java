package eu.verdelhan.tailtest.sample;

import java.util.ArrayList;
import java.util.List;

import eu.verdelhan.tailtest.Indicator;

public class SampleIndicator implements Indicator<Double> {

	private List<Double> values;

	public SampleIndicator(double[] values) {
		this.values = new ArrayList<Double>();
		for (double d : values) {
			this.values.add(d);
		}
	}

	public void addValue(double value) {
		this.values.add(value);
	}

	public Double getValue(int index) {
		return values.get(index);
	}

	public String getName() {
		return toString();
	}
}
