package org.ta4j.core.indicators.range;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.pivotpoints.TimeLevel;
import org.ta4j.core.num.Num;

public class OpeningRangeHigh extends OpeningRange {

  public OpeningRangeHigh(BarSeries series, TimeLevel timeLevel, int limit) {
    super(series, timeLevel, limit);
  }

  @Override
  protected Num calculate(int index) {
    List<Bar> barsOfThePeriod = getBarsOfThePeriod(index);
    Optional<Bar> max = barsOfThePeriod.stream().max(Comparator.comparing(Bar::getHighPrice));
    return max.get().getHighPrice();
  }
}
