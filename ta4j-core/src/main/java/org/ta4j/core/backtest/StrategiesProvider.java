package org.ta4j.core.backtest;

import org.ta4j.core.Strategy;
import org.ta4j.core.TimeSeries;

import java.util.List;

@FunctionalInterface
public interface StrategiesProvider {

    List<Strategy> provide(TimeSeries timeSeries);
}
