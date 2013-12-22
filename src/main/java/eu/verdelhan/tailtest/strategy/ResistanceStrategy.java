package net.sf.tail.strategy;

import net.sf.tail.Indicator;
import net.sf.tail.Strategy;

/**
 * A estratégia ResistanceStrategy vende quando o valor do {@link indicator} é
 * maior ou igual ao {@link resistance} e compra de acordo com a
 * {@link strategy}.
 * 
 * @author marcio
 * 
 */
public class ResistanceStrategy extends AbstractStrategy {

	private final Strategy strategy;

	private final Indicator<? extends Number> indicator;

	private double resistance;

	public ResistanceStrategy(Indicator<? extends Number> indicator, Strategy strategy, double resistance) {
		this.strategy = strategy;
		this.resistance = resistance;
		this.indicator = indicator;
	}

	public boolean shouldEnter(int index) {
		return strategy.shouldEnter(index);
	}

	public boolean shouldExit(int index) {
		if (indicator.getValue(index).doubleValue() >= resistance)
			return true;
		return strategy.shouldExit(index);
	}

	public String getName() {
		return String.format("%s resistance: %i strategy: %s", this.getClass().getSimpleName(), resistance, strategy
				.getName());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((indicator == null) ? 0 : indicator.hashCode());
		long temp;
		temp = Double.doubleToLongBits(resistance);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((strategy == null) ? 0 : strategy.hashCode());
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
		final ResistanceStrategy other = (ResistanceStrategy) obj;
		if (indicator == null) {
			if (other.indicator != null)
				return false;
		} else if (!indicator.equals(other.indicator))
			return false;
		if (Double.doubleToLongBits(resistance) != Double.doubleToLongBits(other.resistance))
			return false;
		if (strategy == null) {
			if (other.strategy != null)
				return false;
		} else if (!strategy.equals(other.strategy))
			return false;
		return true;
	}

}
