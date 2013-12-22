package net.sf.tail.runner;

import net.sf.tail.Runner;
import net.sf.tail.Strategy;
import net.sf.tail.TimeSeriesSlicer;

public interface RunnerFactory {
	Runner create(Strategy strategy,TimeSeriesSlicer slicer);
}
