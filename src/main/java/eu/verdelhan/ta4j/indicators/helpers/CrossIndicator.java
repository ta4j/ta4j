package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.Indicator;

public class CrossIndicator implements Indicator<Boolean> {

	private final Indicator<? extends Number> low;

	private final Indicator<? extends Number> up;

	public CrossIndicator(Indicator<? extends Number> up, Indicator<? extends Number> low) {
		this.up = up;
		this.low = low;
	}

	@Override
	public Boolean getValue(int index) {

		int i = index;
		if (i == 0 || up.getValue(i).doubleValue() >= (low.getValue(i).doubleValue()))
			return false;
		i = i - 1;
		if (up.getValue(i).doubleValue() > low.getValue(i).doubleValue())
			return true;

		else {

			while (i > 0 && up.getValue(i).doubleValue() == low.getValue(i).doubleValue())
				i = i - 1;
			if (i == 0)
				return false;
			if (up.getValue(i).doubleValue() > low.getValue(i).doubleValue())
				return true;
			return false;
		}
	}

	public Indicator<? extends Number> getLow() {
		return low;
	}

	public Indicator<? extends Number> getUp() {
		return up;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " " + low + " " + up;
	}
}
