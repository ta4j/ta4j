/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
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
package org.ta4j.core.indicators.bollinger;

import java.time.Instant;

import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.num.Num;

/**
 * Bollinger BandWidth indicator.
 *
 * @see <a href=
 *     "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_width">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_width</a>
 */
public class BollingerBandWidthIndicator extends NumericIndicator {

  private final BollingerBandsUpperIndicator bbu;
  private final BollingerBandsMiddleIndicator bbm;
  private final BollingerBandsLowerIndicator bbl;
  private Instant currentTick = Instant.EPOCH;
  private Num value;


  /**
   * Constructor.
   *
   * @param bbu the upper band Indicator.
   * @param bbm the middle band Indicator. Typically an {@code SMAIndicator} is
   *     used.
   * @param bbl the lower band Indicator.
   */
  public BollingerBandWidthIndicator(
      final BollingerBandsUpperIndicator bbu,
      final BollingerBandsMiddleIndicator bbm,
      final BollingerBandsLowerIndicator bbl
  ) {
    super(bbm.getNumFactory());
    this.bbu = bbu;
    this.bbm = bbm;
    this.bbl = bbl;
  }


  protected Num calculate() {
    return this.bbu.getValue()
        .minus(this.bbl.getValue())
        .dividedBy(this.bbm.getValue())
        .multipliedBy(getNumFactory().hundred());
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.bbl.refresh(tick);
      this.bbm.refresh(tick);
      this.bbu.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.bbl.isStable() && this.bbu.isStable() && this.bbm.isStable();
  }
}
