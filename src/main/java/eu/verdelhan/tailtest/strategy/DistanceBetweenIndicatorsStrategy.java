package eu.verdelhan.tailtest.strategy;

import eu.verdelhan.tailtest.Indicator;

/**
 * 
 * Estratégia que recebe dois indicadores com uma diferença comum entre eles e
 * toma a decisão de compra do indicador que está representando a ação inferior
 * caso a distance entre elas esteja abaixo de difference, e a decisão de venda
 * caso a distance entre elas esteja acima de difference.
 * 
 * @author marcio
 * 
 */
public class DistanceBetweenIndicatorsStrategy extends AbstractStrategy {

	Indicator<? extends Number> upper;

	Indicator<? extends Number> lower;

	double distance;

	double difference;

	public DistanceBetweenIndicatorsStrategy(Indicator<? extends Number> upper, Indicator<? extends Number> lower,
			double distance, double difference) {
		this.upper = upper;
		this.lower = lower;
		this.distance = distance;
		this.difference = difference;
	}

	public boolean shouldEnter(int index) {
		if ((upper.getValue(index).doubleValue() - lower.getValue(index).doubleValue()) >= (difference + 1.0)
				* distance) {
			return true;
		}
		return false;
	}

	public boolean shouldExit(int index) {
		if ((upper.getValue(index).doubleValue() - lower.getValue(index).doubleValue()) <= (1.0 - difference)
				* distance) {
			return true;
		}
		return false;
	}

	public String getName() {
		return String.format("%s upper: %s lower: %s", this.getClass().getSimpleName(), upper.getName(), lower
				.getName());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(difference);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(distance);
		result = prime * result + (int) (temp ^ (temp >>> 32));
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
		final DistanceBetweenIndicatorsStrategy other = (DistanceBetweenIndicatorsStrategy) obj;
		if (Double.doubleToLongBits(difference) != Double.doubleToLongBits(other.difference))
			return false;
		if (Double.doubleToLongBits(distance) != Double.doubleToLongBits(other.distance))
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
