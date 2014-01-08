package eu.verdelhan.tailtest.strategy;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.Strategy;

/**
 * MinValueStarterStrategy baseia a compra em uma {@link Strategy} enviada como
 * parâmetro desde que o valor atual esteja acima do {@link start}, e baseia a
 * venda nessa mesma {@link Strategy}
 */
public class MinValueStarterStrategy extends AbstractStrategy {

    private Strategy strategy;

    private Indicator<? extends Number> indicator;

    private double start;

    public MinValueStarterStrategy(Indicator<? extends Number> indicator, Strategy strategy, double start) {
        this.strategy = strategy;
        this.start = start;
        this.indicator = indicator;
    }

    @Override
    public boolean shouldEnter(int index) {
        return (indicator.getValue(index).doubleValue() > start);
    }

    @Override
    public boolean shouldExit(int index) {
        return strategy.shouldExit(index);
    }

    @Override
    public String toString() {
        return String.format("%s start: %i", this.getClass().getSimpleName(), start);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((indicator == null) ? 0 : indicator.hashCode());
        long temp;
        temp = Double.doubleToLongBits(start);
        result = (prime * result) + (int) (temp ^ (temp >>> 32));
        result = (prime * result) + ((strategy == null) ? 0 : strategy.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final MinValueStarterStrategy other = (MinValueStarterStrategy) obj;
        if (indicator == null) {
            if (other.indicator != null) {
                return false;
            }
        } else if (!indicator.equals(other.indicator)) {
            return false;
        }
        if (Double.doubleToLongBits(start) != Double.doubleToLongBits(other.start)) {
            return false;
        }
        if (strategy == null) {
            if (other.strategy != null) {
                return false;
            }
        } else if (!strategy.equals(other.strategy)) {
            return false;
        }
        return true;
    }
}
