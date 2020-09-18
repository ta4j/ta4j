package org.ta4j.core.indicators.range;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.pivotpoints.TimeLevel;
import org.ta4j.core.num.Num;

public class LastCandleOfPeriod extends OpeningRange {

  public LastCandleOfPeriod(BarSeries series, TimeLevel timeLevel, int limit) {
    super(series, timeLevel, limit);
  }

  @Override
  protected Num calculate(int index) {
    List<Bar> barsOfThePeriod = getBarsOfThePeriod(index);
    return barsOfThePeriod.get(barsOfThePeriod.size() -1).getClosePrice();
  }

  public List<Bar> getBarsOfThePeriod(int index) {
    Bar currentBar = getBarSeries().getBar(index);
    List<Function<ZonedDateTime, Integer>> dateFunction = getDateFunction();
    return getBarSeries().getBarData().stream()
        .filter(bar -> barsInSamePeriod(bar.getEndTime(), currentBar.getEndTime(), dateFunction))
        .sorted(Comparator.comparing(Bar::getEndTime).reversed())
        .limit(limit)
        .collect(Collectors.toList());
  }
}
