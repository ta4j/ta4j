package eu.verdelhan.tailtest.strategy;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.Strategy;

/**
 * A estratégia SupportStrategy compra quando o valor do {@link indicator} é
 * menor ou igual ao {@link support} e vende de acordo com a {@link strategy}.
 * 
 * @author marcio
 * 
 */
public class SupportStrategy extends AbstractStrategy {

	private final Strategy strategy;

	private final Indicator<? extends Number> indicator;

	private double support;

	public SupportStrategy(Indicator<? extends Number> indicator, Strategy strategy, double support) {
		this.strategy = strategy;
		this.support = support;
		this.indicator = indicator;
	}

	public boolean shouldEnter(int index) {
		if (indicator.getValue(index).doubleValue() <= support)
			return true;
		return strategy.shouldEnter(index);
	}

	public boolean shouldExit(int index) {
		return strategy.shouldExit(index);
	}

	public String getName() {
		return String
				.format("%s suport: %i strategy: %s", this.getClass().getSimpleName(), support, strategy.getName());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((indicator == null) ? 0 : indicator.hashCode());
		result = prime * result + ((strategy == null) ? 0 : strategy.hashCode());
		long temp;
		temp = Double.doubleToLongBits(support);
		result = prime * result + (int) (temp ^ (temp >>> 32));
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
		final SupportStrategy other = (SupportStrategy) obj;
		if (indicator == null) {
			if (other.indicator != null)
				return false;
		} else if (!indicator.equals(other.indicator))
			return false;
		if (strategy == null) {
			if (other.strategy != null)
				return false;
		} else if (!strategy.equals(other.strategy))
			return false;
		if (Double.doubleToLongBits(support) != Double.doubleToLongBits(other.support))
			return false;
		return true;
	}
}
