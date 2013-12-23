package eu.verdelhan.tailtest.runner;

import eu.verdelhan.tailtest.Runner;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.TimeSeriesSlicer;

public interface RunnerFactory {
	Runner create(Strategy strategy,TimeSeriesSlicer slicer);
}
