package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Strategy;

/**
 * Enter: when the {@link indicator} value is less than or equal to the {@link support} threshold
 * Exit: according to the provided {@link strategy}
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

    @Override
    public boolean shouldEnter(int index) {
        if (indicator.getValue(index).doubleValue() <= support) {
            return true;
        }
        return strategy.shouldEnter(index);
    }

    @Override
    public boolean shouldExit(int index) {
        return strategy.shouldExit(index);
    }

    @Override
    public String toString() {
        return String
                .format("%s suport: %i strategy: %s", this.getClass().getSimpleName(), support, strategy);
    }
}
