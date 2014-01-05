package eu.verdelhan.tailtest.strategy;

import eu.verdelhan.tailtest.Strategy;

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
    public String getName() {
        return getClass().getSimpleName() + " over strategy: " + strategy.getName() + " number of ticks: "
                + numberOfTicks;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + numberOfTicks;
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
        final NotSoFastStrategy other = (NotSoFastStrategy) obj;
        if (numberOfTicks != other.numberOfTicks) {
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
