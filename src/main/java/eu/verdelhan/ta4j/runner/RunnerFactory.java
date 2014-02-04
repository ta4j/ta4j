package eu.verdelhan.ta4j.runner;

import eu.verdelhan.ta4j.Runner;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeriesSlicer;

public interface RunnerFactory {
	Runner create(Strategy strategy,TimeSeriesSlicer slicer);
}
