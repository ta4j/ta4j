package org.ta4j.core.indicators;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * @author Lukáš Kvídera
 */
public class CachedIndicator implements Indicator<Num> {

  private static final ConcurrentMap<Indicator<Num>, ConcurrentMap<Bar, Num>> cache = new ConcurrentHashMap<>();
  private final Indicator<Num> delegate;


  public CachedIndicator(final Indicator<Num> delegate) {
    this.delegate = delegate;
  }


  @Override
  public Num getValue() {
    return cache.get(delegate).get(getBarSeries().getBar());
  }


  @Override
  public BarSeries getBarSeries() {
    return this.delegate.getBarSeries();
  }


  @Override
  public void refresh() {
    cache.computeIfAbsent(this.delegate, k -> new ConcurrentHashMap<>())
        .computeIfAbsent(getBarSeries().getBar(), k -> {
          this.delegate.refresh();
          return this.delegate.getValue();
        });
  }


  @Override
  public boolean isStable() {
    final var barCalculationCache = cache.get(this.delegate);
    if (barCalculationCache == null) {
      return false;
    }

    final var num = barCalculationCache.get(getBarSeries().getBar());
    if (num == null) {
      return false;
    }

    return this.delegate.isStable();
  }
}
