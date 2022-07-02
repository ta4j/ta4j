package org.ta4j.core.indicators.dheeman;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.pivotpoints.TimeLevel;
import org.ta4j.core.indicators.range.OpeningRange;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class KingsCandleUpside extends OpeningRange {

  public KingsCandleUpside(BarSeries series, TimeLevel timeLevel, int limit) {
    super(series, timeLevel, limit);
  }

  @Override
  protected Num calculate(int index) {
    List<Bar> barsOfThePeriod = getBarsOfThePeriod(index);
    Num seriesHigh = barsOfThePeriod.stream()
            .max(Comparator.comparing(Bar::getHighPrice)).get()
            .getHighPrice();
    for(int i = barsOfThePeriod.size() -2; i>=1; i--) {
      boolean kingCandle = isKingCandle(barsOfThePeriod.get(i),
              barsOfThePeriod.get(i + 1),
              barsOfThePeriod.get(i - 1),
              seriesHigh);
      if(kingCandle) {
        return barsOfThePeriod.get(i).getHighPrice();
      }
    }
    return DoubleNum.valueOf(seriesHigh.doubleValue() * 1.1);
  }

  private boolean isKingCandle(Bar current, Bar next, Bar previous, Num seriesHigh) {
    return current.getHighPrice().isGreaterThan(previous.getClosePrice())
            && current.getHighPrice().isGreaterThan(next.getClosePrice())
            && current.getHighPrice().isEqual(seriesHigh);
  }
}
