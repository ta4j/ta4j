package org.ta4j.core;

public interface StrategyFactory {
  /**
   * @param series that will be strategized
   * @return strategy related to specific series
   */
  Strategy createStrategy(BarSeries series);
}
