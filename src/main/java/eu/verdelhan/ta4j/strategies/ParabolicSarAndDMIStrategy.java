package eu.verdelhan.ta4j.strategies;

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
}
