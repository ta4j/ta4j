package org.ta4j.core.indicators.dheeman;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.pivotpoints.TimeLevel;
import org.ta4j.core.indicators.range.OpeningRange;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.util.Comparator;
import java.util.List;

public class KingsCandleDownside extends OpeningRange {

  public KingsCandleDownside(BarSeries series, TimeLevel timeLevel, int limit) {
    super(series, timeLevel, limit);
  }

  @Override
  protected Num calculate(int index) {
    List<Bar> barsOfThePeriod = getBarsOfThePeriod(index);
    Num seriesLow = barsOfThePeriod.stream()
            .min(Comparator.comparing(Bar::getLowPrice)).get()
            .getLowPrice();
    for(int i = barsOfThePeriod.size() -2; i>0; i--) {
      boolean kingCandle = isKingCandle(barsOfThePeriod.get(i),
              barsOfThePeriod.get(i + 1),
              barsOfThePeriod.get(i - 1),
              seriesLow);
      if(kingCandle) {
        return barsOfThePeriod.get(i).getLowPrice();
      }
    }
    return DoubleNum.valueOf(seriesLow.doubleValue() * 0.9);
  }

  private boolean isKingCandle(Bar current, Bar next, Bar previous, Num seriesLow) {
    return current.getLowPrice().isLessThan(previous.getClosePrice())
            && current.getLowPrice().isLessThan(next.getClosePrice())
            && current.getLowPrice().isEqual(seriesLow);
  }
}
