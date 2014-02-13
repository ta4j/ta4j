package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Strategy;

/**
 * Enter: according to the provided {@link strategy}
 * Exit: when the {@link indicator} value is greater than or equal to the {@link resistance} threshold
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

    @Override
    public boolean shouldEnter(int index) {
        return strategy.shouldEnter(index);
    }

    @Override
    public boolean shouldExit(int index) {
        if (indicator.getValue(index).doubleValue() >= resistance) {
            return true;
        }
        return strategy.shouldExit(index);
    }

    @Override
    public String toString() {
        return String.format("%s resistance: %i strategy: %s", this.getClass().getSimpleName(), resistance,
                strategy);
    }
}
