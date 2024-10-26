/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2024 Lukáš Kvídera
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.bool;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.bool.chandelier.ChandelierExitLongIndicator;
import org.ta4j.core.indicators.bool.chandelier.ChandelierExitShortIndicator;
import org.ta4j.core.indicators.helpers.previous.PreviousBooleanValueIndicator;
import org.ta4j.core.rules.BooleanIndicatorRule;

public abstract class BooleanIndicator implements Indicator<Boolean> {

  public BooleanIndicatorRule toRule() {
    return new BooleanIndicatorRule(this);
  }


  public PreviousBooleanValueIndicator previous(final int barCount) {
    return new PreviousBooleanValueIndicator(this, barCount);
  }


  public static ChandelierExitLongIndicator chandelierExitLong(
      final BarSeries series,
      final int barCount,
      final double coefficient
  ) {
    return new ChandelierExitLongIndicator(series, barCount, coefficient);
  }


  public static ChandelierExitShortIndicator chandelierExitShort(
      final BarSeries series,
      final int barCount,
      final double coefficient
  ) {
    return new ChandelierExitShortIndicator(series, barCount, coefficient);
  }
}
