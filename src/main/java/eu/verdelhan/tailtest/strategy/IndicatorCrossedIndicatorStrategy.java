package net.sf.tail.strategy;

import net.sf.tail.Indicator;
import net.sf.tail.indicator.helper.CrossIndicator;

/**
 * 
 * Strategy that buy when upper is above lower and cross and sell when lower is
 * above upper and cross
 * 
 * @author tgthies
 * 
 */
public class IndicatorCrossedIndicatorStrategy extends AbstractStrategy {

	private final Indicator<Boolean> crossUp;

	private final Indicator<Boolean> crossDown;

	private Indicator<? extends Number> upper;

	private Indicator<? extends Number> lower;

	public IndicatorCrossedIndicatorStrategy(Indicator<? extends Number> upper, Indicator<? extends Number> lower) {
		this.upper = upper;
		this.lower = lower;
		crossUp = new CrossIndicator(upper, lower);
		crossDown = new CrossIndicator(lower, upper);
	}

	public boolean shouldEnter(int index) {
		return crossUp.getValue(index);
	}

	public boolean shouldExit(int index) {
		return crossDown.getValue(index);
	}

	public String getName() {
		return String.format("Cross %s over %s", upper.getName(), lower.getName());

	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((crossDown == null) ? 0 : crossDown.hashCode());
		result = prime * result + ((crossUp == null) ? 0 : crossUp.hashCode());
		result = prime * result + ((lower == null) ? 0 : lower.hashCode());
		result = prime * result + ((upper == null) ? 0 : upper.hashCode());
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
		final IndicatorCrossedIndicatorStrategy other = (IndicatorCrossedIndicatorStrategy) obj;
		if (crossDown == null) {
			if (other.crossDown != null)
				return false;
		} else if (!crossDown.equals(other.crossDown))
			return false;
		if (crossUp == null) {
			if (other.crossUp != null)
				return false;
		} else if (!crossUp.equals(other.crossUp))
			return false;
		if (lower == null) {
			if (other.lower != null)
				return false;
		} else if (!lower.equals(other.lower))
			return false;
		if (upper == null) {
			if (other.upper != null)
				return false;
		} else if (!upper.equals(other.upper))
			return false;
		return true;
	}
}
