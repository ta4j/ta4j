package eu.verdelhan.tailtest.runner;

import eu.verdelhan.tailtest.Runner;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.TimeSeriesSlicer;

public class HistoryRunnerFactory implements RunnerFactory {

	public Runner create(Strategy strategy, TimeSeriesSlicer slicer) {
		return new HistoryRunner(slicer,strategy);
	}

}
