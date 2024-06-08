package org.ta4j.core;

import org.ta4j.core.backtest.BacktestStrategy;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.IndicatorContext;

/**
 * @author Lukáš Kvídera
 */
public class MockStrategy extends BacktestStrategy {
  public MockStrategy(final IndicatorContext indicatorContext) {
    super("mock strategy", Rule.NOOP, Rule.NOOP, indicatorContext);
  }


  public MockStrategy(final Indicator<?>... mockIndicators) {
    this(IndicatorContext.of(mockIndicators));
  }
}
