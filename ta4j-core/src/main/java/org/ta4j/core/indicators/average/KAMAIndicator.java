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
package org.ta4j.core.indicators.average;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.Indicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.helpers.RunningTotalIndicator;
import org.ta4j.core.indicators.helpers.TransformIndicator;
import org.ta4j.core.num.Num;

/**
 * The Kaufman's Adaptive Moving Average (KAMA) Indicator.
 *
 * @see <a href=
 *     "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:kaufman_s_adaptive_moving_average">
 *     http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:kaufman_s_adaptive_moving_average</a>
 */
public class KAMAIndicator extends AbstractIndicator<Num> {

  private final Indicator<Num> price;
  private final int barCountEffectiveRatio;
  private final Num fastest;
  private final Num slowest;
  private final RunningTotalIndicator previousVolatilities;
  private final PreviousValueIndicator priceAtStartOfRange;
  private int barsPassed;
  private ZonedDateTime currentTick = ZonedDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  private Num value;


  /**
   * Constructor.
   *
   * @param price the price
   * @param barCountEffectiveRatio the time frame of the effective ratio (usually
   *     10)
   * @param barCountFast the time frame fast (usually 2)
   * @param barCountSlow the time frame slow (usually 30)
   */
  public KAMAIndicator(
      final Indicator<Num> price,
      final int barCountEffectiveRatio,
      final int barCountFast,
      final int barCountSlow
  ) {
    super(price.getBarSeries());
    this.price = price;
    this.barCountEffectiveRatio = barCountEffectiveRatio;
    this.priceAtStartOfRange = new PreviousValueIndicator(price, barCountEffectiveRatio);
    this.previousVolatilities = new RunningTotalIndicator(TransformIndicator.abs(new DifferenceIndicator(price)), barCountEffectiveRatio);
    final var numFactory = getBarSeries().numFactory();
    final var two = numFactory.two();
    this.fastest = two.dividedBy(numFactory.numOf(barCountFast + 1));
    this.slowest = two.dividedBy(numFactory.numOf(barCountSlow + 1));
  }


  /**
   * Constructor with:
   *
   * <ul>
   * <li>{@code barCountEffectiveRatio} = 10
   * <li>{@code barCountFast} = 2
   * <li>{@code barCountSlow} = 30
   * </ul>
   *
   * @param price the priceindicator
   */
  public KAMAIndicator(final Indicator<Num> price) {
    this(price, 10, 2, 30);
  }


  protected Num calculate() {
    final Num currentPrice = this.price.getValue();

    /*
     * Efficiency Ratio (ER) ER = Change/Volatility
     * Change = ABS(Close - Close (10 * periods ago))
     * Volatility = Sum10(ABS(Close - Prior Close))
     * Volatility is the sum of the absolute value of the last ten price changes (Close - Prior Close).
     */
    final Num change = currentPrice.minus(this.priceAtStartOfRange.getValue()).abs();
    final Num er = change.dividedBy(this.previousVolatilities.getValue());
    /*
     * Smoothing Constant (SC) SC = [ER x (fastest SC - slowest SC) + slowest SC]2
     * SC = [ER x (2/(2+1) - 2/(30+1)) + 2/(30+1)]2
     */
    final Num sc = er.multipliedBy(this.fastest.minus(this.slowest)).plus(this.slowest).pow(2);


    final Num priorKAMA = this.value;
    if (this.barsPassed <= this.barCountEffectiveRatio) {
      return currentPrice;
    }

    /*
     * KAMA Current KAMA = Prior KAMA + SC x (Price - Prior KAMA)
     */
    return priorKAMA.plus(sc.multipliedBy(currentPrice.minus(priorKAMA)));
  }


  @Override
  public Num getValue() {
    return this.value;
  }


  @Override
  public void refresh(final ZonedDateTime tick) {
    if (tick.isAfter(this.currentTick)) {
      ++this.barsPassed;
      this.price.refresh(tick);
      this.priceAtStartOfRange.refresh(tick);
      this.previousVolatilities.refresh(tick);
      this.value = calculate();
      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.barsPassed >= this.barCountEffectiveRatio;
  }
}
