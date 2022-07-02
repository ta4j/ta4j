package org.ta4j.core.indicators.range;

import junit.framework.TestCase;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.DecimalNum;

public class PercentageIndicatorTest extends TestCase {

  public void testCalculate() {
    ConstantIndicator val = new ConstantIndicator(new BaseBarSeries(), DecimalNum.valueOf(150));
    PercentageIndicator percent = new PercentageIndicator(val, DecimalNum.valueOf(2));
    assertEquals(percent.calculate(0), DecimalNum.valueOf(153));
  }

  public void testCalculateNegative() {
    ConstantIndicator val = new ConstantIndicator(new BaseBarSeries(), DecimalNum.valueOf(150));
    PercentageIndicator percent = new PercentageIndicator(val, DecimalNum.valueOf(-2));
    assertEquals(percent.calculate(0), DecimalNum.valueOf(147));
  }
}