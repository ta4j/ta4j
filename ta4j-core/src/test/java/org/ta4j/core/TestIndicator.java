package org.ta4j.core;

import java.time.Instant;

import org.ta4j.core.backtest.BacktestBarSeries;
import org.ta4j.core.indicators.Indicator;

/**
 * @author Lukáš Kvídera
 */
public class TestIndicator<T> implements Indicator<T> {
  private final BacktestBarSeries series;
  private final Indicator<T> indicator;


  public TestIndicator(final BacktestBarSeries series, final Indicator<T> indicator) {
    this.series = series;
    this.indicator = indicator;
  }


  @Override
  public T getValue() {
    return this.indicator.getValue();
  }


  @Override
  public void refresh(final Instant tick) {
    this.indicator.refresh(tick);
  }


  @Override
  public boolean isStable() {
    return this.indicator.isStable();
  }


  public BacktestBarSeries getBarSeries() {
    return this.series;
  }
}
