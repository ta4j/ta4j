package eu.verdelhan.ta4j.runners;

import eu.verdelhan.ta4j.Runner;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeriesSlicer;

public class ShortSellRunnerFactory implements RunnerFactory {

    @Override
    public Runner create(Strategy strategy, TimeSeriesSlicer slicer) {
        return new ShortSellRunner(slicer, strategy);
    }
}