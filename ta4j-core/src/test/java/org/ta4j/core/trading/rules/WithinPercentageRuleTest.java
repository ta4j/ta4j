package org.ta4j.core.trading.rules;

import junit.framework.TestCase;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

public class WithinPercentageRuleTest extends TestCase {

  @Test
  public void testWithinPercentage() {
    BarSeries series = new BaseBarSeries();
    Indicator<Num> one = new ConstantIndicator(series, PrecisionNum.valueOf(210));
    Indicator<Num> two = new ConstantIndicator(series, PrecisionNum.valueOf(206));
    WithinPercentageRule rule = new WithinPercentageRule(one, two, PrecisionNum.valueOf(2));
    assertTrue(rule.isSatisfied(0));
  }

}