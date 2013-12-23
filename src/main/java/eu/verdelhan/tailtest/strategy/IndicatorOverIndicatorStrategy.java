package eu.verdelhan.tailtest.strategy;

import eu.verdelhan.tailtest.Indicator;

public class IndicatorOverIndicatorStrategy extends AbstractStrategy {

	private Indicator<Double> first;

	private Indicator<Double> second;

	public IndicatorOverIndicatorStrategy(Indicator<Double> first, Indicator<Double> second) {
		this.first = first;
		this.second = second;
	}

	public boolean shouldEnter(int index) {
		if (first.getValue(index).compareTo(second.getValue(index)) < 0) {
			return true;
		}
		return false;
	}

	public boolean shouldExit(int index) {
		if (first.getValue(index).compareTo(second.getValue(index)) > 0) {
			return true;
		}
		return false;
	}

	public String getName() {
		return String.format("%s upper: %s lower: %s", this.getClass().getSimpleName(), first.getName(), second
				.getName());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((first == null) ? 0 : first.hashCode());
		result = prime * result + ((second == null) ? 0 : second.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final IndicatorOverIndicatorStrategy other = (IndicatorOverIndicatorStrategy) obj;
		if (first == null) {
			if (other.first != null)
				return false;
		} else if (!first.equals(other.first))
			return false;
		if (second == null) {
			if (other.second != null)
				return false;
		} else if (!second.equals(other.second))
			return false;
		return true;
	}

}
