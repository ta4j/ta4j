package org.ta4j.core.indicators.range;

import java.util.List;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.pivotpoints.TimeLevel;
import org.ta4j.core.num.Num;

public class NthCandleOfPeriod extends OpeningRange {

  public NthCandleOfPeriod(BarSeries series, TimeLevel timeLevel, int limit) {
    super(series, timeLevel, limit);
  }

  @Override
  protected Num calculate(int index) {
    List<Bar> barsOfThePeriod = getBarsOfThePeriod(index);
    return barsOfThePeriod.get(limit -1).getClosePrice();
  }
}
