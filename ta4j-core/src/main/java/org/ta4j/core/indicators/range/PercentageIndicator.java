package org.ta4j.core.indicators.range;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

public class PercentageIndicator extends CachedIndicator<Num> {

  private Num percentage;
  private Indicator<Num> ref;

  public PercentageIndicator(Indicator<Num> ref, Num percentage) {
    super(ref);
    this.percentage = percentage;
    this.ref = ref;
  }

  @Override
  protected Num calculate(int index) {
    PrecisionNum hundred = PrecisionNum.valueOf(100.0);
    Num percentVal = hundred.plus(percentage).dividedBy(hundred);
    return percentVal.multipliedBy(ref.getValue(index));
  }
}
