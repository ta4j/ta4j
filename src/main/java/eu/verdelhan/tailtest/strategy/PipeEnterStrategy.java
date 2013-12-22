package net.sf.tail.strategy;

import net.sf.tail.Indicator;
import net.sf.tail.indicator.helper.CrossIndicator;

/**
 * PipeEnterStrategy baseia a compra em um {@link Indicator} lower, no momento
 * em que o {@link Indicator} value o ultrapassa em sentido ascendente, e baseia
 * a venda em um {@link Indicator}} upper, no momento em que o
 * {@link Indicator} value o ultrapassa em sentido descendente. Estratégia muito
 * comum para indicadores que criam "tubos" de suporte e resistência, como por
 * exemplo o Bollinger Bands.
 * 
 */
public class PipeEnterStrategy extends AbstractStrategy {

	private final Indicator<Boolean> crossUp;

	private final Indicator<Boolean> crossDown;

	public PipeEnterStrategy(Indicator<? extends Number> upper, Indicator<? extends Number> lower,
			Indicator<? extends Number> value) {
		crossUp = new CrossIndicator(value, upper);
		crossDown = new CrossIndicator(lower, value);
	}

	public boolean shouldEnter(int index) {
		if (crossDown.getValue(index))
			return true;
		return false;
	}

	public boolean shouldExit(int index) {
		if (crossUp.getValue(index))
			return true;
		return false;
	}

	public String getName() {
		return String.format("%s upper: %s lower: %s", this.getClass().getSimpleName(), crossUp.getName(), crossDown
				.getName());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((crossDown == null) ? 0 : crossDown.hashCode());
		result = prime * result + ((crossUp == null) ? 0 : crossUp.hashCode());
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
		final PipeEnterStrategy other = (PipeEnterStrategy) obj;
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
		return true;
	}

}
