package org.ta4j.core.indicators.range;

import java.util.List;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.pivotpoints.TimeLevel;
import org.ta4j.core.num.Num;

public class TimeOfCandle extends RecursiveCachedIndicator<Num> {

  public TimeOfCandle(BarSeries series) {
    super(series);
  }

  @Override
  protected Num calculate(int index) {
    int hour = getBarSeries().getBar(index).getEndTime().getHour();
    int min = getBarSeries().getBar(index).getEndTime().getMinute();
    return numOf(hour + ( min / 100));
  }
}
