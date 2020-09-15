package org.ta4j.core.indicators.range;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.indicators.pivotpoints.TimeLevel;
import org.ta4j.core.num.Num;

public abstract class OpeningRange extends RecursiveCachedIndicator<Num> {

  private final TimeLevel timeLevel;
  protected final int limit;


  public OpeningRange(BarSeries series, TimeLevel timeLevel, int limit) {
    super(series);
    this.timeLevel = timeLevel;
    this.limit = limit;
  }

  public List<Bar> getBarsOfThePeriod(int index) {
    Bar currentBar = getBarSeries().getBar(index);
    Function<ZonedDateTime, Integer> dateFunction = getDateFunction();
    return getBarSeries().getBarData().stream()
        .filter(bar -> barsInSamePeriod(bar, currentBar, dateFunction))
        .limit(limit)
        .collect(Collectors.toList());
  }

  protected Function<ZonedDateTime, Integer> getDateFunction() {
    switch (timeLevel) {
      case MONTH:
        return ZonedDateTime::getMonthValue;
      case YEAR:
        return ZonedDateTime::getYear;
      default:
        return ZonedDateTime::getDayOfMonth;
    }
  }

  protected boolean barsInSamePeriod(Bar currentBar, Bar reference,
      Function<ZonedDateTime, Integer> dateFunction) {
    return dateFunction.apply(currentBar.getEndTime()) == dateFunction.apply(reference.getEndTime());
  }
}
