package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.Strategy;

public class NotSoFastStrategy extends AbstractStrategy {

    private Strategy strategy;

    private int numberOfTicks;

    private int tickIndex;

    public NotSoFastStrategy(Strategy strategy, int numberOfTicks) {
        this.strategy = strategy;
        this.numberOfTicks = numberOfTicks;
        tickIndex = 0;
    }

    @Override
    public boolean shouldEnter(int index) {
        if (strategy.shouldEnter(index)) {
            tickIndex = index;
            return true;
        }
        return false;
    }

    @Override
    public boolean shouldExit(int index) {
        return (strategy.shouldExit(index) && ((index - tickIndex) > numberOfTicks));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " over strategy: " + strategy + " number of ticks: "
                + numberOfTicks;
    }
}
