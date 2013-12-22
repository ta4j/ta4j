package net.sf.tail.runner;

import net.sf.tail.Runner;
import net.sf.tail.Strategy;
import net.sf.tail.TimeSeriesSlicer;

public class ShortSellRunnerFactory implements RunnerFactory {

	public Runner create(Strategy strategy, TimeSeriesSlicer slicer) {
		return new ShortSellRunner(slicer,strategy);
	}

}
