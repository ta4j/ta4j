package eu.verdelhan.tailtest.strategy;

import eu.verdelhan.tailtest.Strategy;

public class CombinedBuyAndSellStrategy extends AbstractStrategy {

    private Strategy buyStrategy;

    private Strategy sellStrategy;

    public CombinedBuyAndSellStrategy(Strategy buyStrategy, Strategy sellStrategy) {
        this.buyStrategy = buyStrategy;
        this.sellStrategy = sellStrategy;
    }

    @Override
    public boolean shouldEnter(int index) {
        return buyStrategy.shouldEnter(index);
    }

    @Override
    public boolean shouldExit(int index) {
        return sellStrategy.shouldExit(index);
    }

    @Override
    public String getName() {
        return String.format("Combined strategy using buy strategy %s and sell strategy %s", buyStrategy.getName(),
                sellStrategy.getName());
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((buyStrategy == null) ? 0 : buyStrategy.hashCode());
        result = (prime * result) + ((sellStrategy == null) ? 0 : sellStrategy.hashCode());
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
        final CombinedBuyAndSellStrategy other = (CombinedBuyAndSellStrategy) obj;
        if (buyStrategy == null) {
            if (other.buyStrategy != null) {
                return false;
            }
        } else if (!buyStrategy.equals(other.buyStrategy)) {
            return false;
        }
        if (sellStrategy == null) {
            if (other.sellStrategy != null) {
                return false;
            }
        } else if (!sellStrategy.equals(other.sellStrategy)) {
            return false;
        }
        return true;
    }
}
