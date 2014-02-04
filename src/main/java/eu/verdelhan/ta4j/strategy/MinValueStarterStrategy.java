package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Strategy;

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
}
