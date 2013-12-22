package net.sf.tail.strategy;

import net.sf.tail.Indicator;
import net.sf.tail.Strategy;

/**
 * MinValueStopperStrategy baseia a compra em uma {@link Strategy} enviada como
 * parâmetro, registrando o valor do {@link Indicator} enviado como parâmetro no
 * mesmo índice de compra, e baseia a venda nessa mesma {@link Strategy} desde
 * que o valor registrado do {@link Indicator} na compra não tenha um decréscimo
 * maior que loss %
 * 
 */
public class StopGainStrategy extends AbstractStrategy {

	private Strategy strategy;

	private double loss;

	private Indicator<? extends Number> indicator;

	private double value;

	public StopGainStrategy(Indicator<? extends Number> indicator, Strategy strategy, int loss) {
		this.strategy = strategy;
		this.loss = (double) loss;
		this.indicator = indicator;
	}

	public boolean shouldEnter(int index) {
		if(strategy.shouldEnter(index)) {
			value = indicator.getValue(index).doubleValue();
			return true;
		}
		return false;
	}

	public boolean shouldExit(int index) {
		if (value + (value * (loss / 100)) < indicator.getValue(index).doubleValue()) {
			return true;
		}
		return strategy.shouldExit(index);
	}

	public String getName() {
		return String.format("%s stoper: %s over %s", this.getClass().getSimpleName(), ""+ loss, strategy.getName());
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((indicator == null) ? 0 : indicator.hashCode());
		long temp;
		temp = Double.doubleToLongBits(loss);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		result = prime * result + ((strategy == null) ? 0 : strategy.hashCode());
		temp = Double.doubleToLongBits(value);
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
		final StopGainStrategy other = (StopGainStrategy) obj;
		if (indicator == null) {
			if (other.indicator != null)
				return false;
		} else if (!indicator.equals(other.indicator))
			return false;
		if (Double.doubleToLongBits(loss) != Double.doubleToLongBits(other.loss))
			return false;
		if (strategy == null) {
			if (other.strategy != null)
				return false;
		} else if (!strategy.equals(other.strategy))
			return false;
		if (Double.doubleToLongBits(value) != Double.doubleToLongBits(other.value))
			return false;
		return true;
	}
}
