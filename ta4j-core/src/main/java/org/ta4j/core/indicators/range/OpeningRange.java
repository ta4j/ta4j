package org.ta4j.core.indicators.range;

import java.time.ZonedDateTime;
import java.util.Arrays;
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
    List<Bar> barData = getBarSeries().getBarData();
    int startIndex = index - 200 < 0 ? 0 : index - 100;
    int endIndex = index + 200 > barData.size()? barData.size() -1 : index + 100;

    List<Bar> data = barData.subList(startIndex, endIndex);
    Bar currentBar = getBarSeries().getBar(index);
    List<Function<ZonedDateTime, Integer>> dateFunction = getDateFunction();
    return data.stream()
        .filter(bar -> barsInSamePeriod(bar.getEndTime(), currentBar.getEndTime(), dateFunction))
        .limit(limit)
        .collect(Collectors.toList());
  }

  protected List<Function<ZonedDateTime, Integer>> getDateFunction() {
    switch (timeLevel) {
      case MONTH:
        return Arrays.asList(ZonedDateTime::getMonthValue, ZonedDateTime::getYear);
      case YEAR:
        return Arrays.asList(ZonedDateTime::getYear);
      default:
        return Arrays.asList(ZonedDateTime::getDayOfYear, ZonedDateTime::getYear);
    }
  }

  protected static boolean barsInSamePeriod(ZonedDateTime currentBar, ZonedDateTime reference,
      List<Function<ZonedDateTime, Integer>> dateFunctions) {
    Boolean result = true;
    for (int i = 0; i < dateFunctions.size(); i++) {
      Function<ZonedDateTime, Integer> function = dateFunctions.get(i);
      result = result && function.apply(currentBar).equals(function
          .apply(reference));
    }
    return result;
  }
}
