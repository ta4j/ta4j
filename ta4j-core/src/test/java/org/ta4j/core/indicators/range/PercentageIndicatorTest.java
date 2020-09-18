package org.ta4j.core.indicators.range;

import junit.framework.TestCase;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.PrecisionNum;

public class PercentageIndicatorTest extends TestCase {

  public void testCalculate() {
    ConstantIndicator val = new ConstantIndicator(new BaseBarSeries(), PrecisionNum.valueOf(150));
    PercentageIndicator percent = new PercentageIndicator(val, PrecisionNum.valueOf(2));
    assertEquals(percent.calculate(0), PrecisionNum.valueOf(153));
  }

  public void testCalculateNegative() {
    ConstantIndicator val = new ConstantIndicator(new BaseBarSeries(), PrecisionNum.valueOf(150));
    PercentageIndicator percent = new PercentageIndicator(val, PrecisionNum.valueOf(-2));
    assertEquals(percent.calculate(0), PrecisionNum.valueOf(147));
  }
}