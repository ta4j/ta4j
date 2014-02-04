package eu.verdelhan.ta4j.strategy;

public class ParabolicSarAndDMIStrategy extends AbstractStrategy {

    private IndicatorCrossedIndicatorStrategy parabolicStrategy;
    private IndicatorOverIndicatorStrategy dmiStrategy;

    public ParabolicSarAndDMIStrategy(IndicatorCrossedIndicatorStrategy indicatorCrossedIndicatorStrategy,
            IndicatorOverIndicatorStrategy indicatorOverIndicatorStrategy) {
        parabolicStrategy = indicatorCrossedIndicatorStrategy;
        dmiStrategy = indicatorOverIndicatorStrategy;
    }

    @Override
    public boolean shouldEnter(int index) {
        return parabolicStrategy.shouldEnter(index);
    }

    @Override
    public boolean shouldExit(int index) {
        return dmiStrategy.and(parabolicStrategy).shouldExit(index);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((dmiStrategy == null) ? 0 : dmiStrategy.hashCode());
        result = (prime * result) + ((parabolicStrategy == null) ? 0 : parabolicStrategy.hashCode());
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
        final ParabolicSarAndDMIStrategy other = (ParabolicSarAndDMIStrategy) obj;
        if (dmiStrategy == null) {
            if (other.dmiStrategy != null) {
                return false;
            }
        } else if (!dmiStrategy.equals(other.dmiStrategy)) {
            return false;
        }
        if (parabolicStrategy == null) {
            if (other.parabolicStrategy != null) {
                return false;
            }
        } else if (!parabolicStrategy.equals(other.parabolicStrategy)) {
            return false;
        }
        return true;
    }

}
