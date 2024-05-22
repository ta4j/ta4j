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
package org.ta4j.core.indicators.candles;

import java.time.Instant;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.indicators.average.SMAIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.CircularBarArray;
import org.ta4j.core.utils.CircularIndicatorArray;

/**
 * Three black crows indicator.
 *
 * @see <a href="http://www.investopedia.com/terms/t/three_black_crows.asp">
 *     http://www.investopedia.com/terms/t/three_black_crows.asp</a>
 */
public class ThreeBlackCrowsIndicator extends AbstractIndicator<Boolean> {

  /** Lower shadow. */
  private final CircularIndicatorArray lowerShadows = new CircularIndicatorArray(3);

  /** Average lower shadow. */
  private final PreviousValueIndicator averageLowerShadowInd;

  /** Factor used when checking if a candle has a very short lower shadow. */
  private final Num factor;

  private final CircularBarArray bars = new CircularBarArray(4);
  private Instant currentTick = Instant.EPOCH;
  private Boolean value;


  /**
   * Constructor.
   *
   * @param series the bar series
   * @param barCount the number of bars used to calculate the average lower shadow
   * @param factor the factor used when checking if a candle has a very short
   *     lower shadow
   */
  public ThreeBlackCrowsIndicator(final BarSeries series, final int barCount, final double factor) {
    super(series);
    final var lowerShadowIndicator = new LowerShadowIndicator(series);
    this.lowerShadows.addLast(new PreviousValueIndicator(lowerShadowIndicator, 3));
    this.lowerShadows.addLast(new PreviousValueIndicator(lowerShadowIndicator, 2));
    this.lowerShadows.addLast(new PreviousValueIndicator(lowerShadowIndicator, 1));
    this.averageLowerShadowInd = new PreviousValueIndicator(new SMAIndicator(lowerShadowIndicator, barCount), 4);
    this.factor = getBarSeries().numFactory().numOf(factor);
  }


  protected Boolean calculate() {
    this.bars.addLast(getBarSeries().getBar());

    if (this.bars.isNotFull()) {
      // We need 4 candles: 1 white, 3 black
      return false;
    }

    final var index = this.bars.getCurrentIndex();
    final int whiteCandleIndex = index - 3;
    return this.bars.get(whiteCandleIndex).isBullish()
           && isBlackCrow(index - 2)
           && isBlackCrow(index - 1)
           && isBlackCrow(index);
  }


  /**
   * @return true if the bar/candle has a very short lower shadow, false otherwise
   */
  private boolean hasVeryShortLowerShadow(final int index) {
    final Num currentLowerShadow = this.lowerShadows.get(index).getValue();
    // We use the white candle index to remove to bias of the previous crows
    final Num averageLowerShadow = this.averageLowerShadowInd.getValue();

    return currentLowerShadow.isLessThan(averageLowerShadow.multipliedBy(this.factor));
  }


  /**
   * @param index the current bar/candle index
   *
   * @return true if the current bar/candle is declining, false otherwise
   */
  private boolean isDeclining(final int index) {
    final Bar prevBar = this.bars.get(index - 1);
    final Bar currBar = this.bars.get(index);
    final Num prevOpenPrice = prevBar.openPrice();
    final Num prevClosePrice = prevBar.closePrice();
    final Num currOpenPrice = currBar.openPrice();
    final Num currClosePrice = currBar.closePrice();

    // Opens within the body of the previous candle
    return currOpenPrice.isLessThan(prevOpenPrice) && currOpenPrice.isGreaterThan(prevClosePrice)
           // Closes below the previous close price
           && currClosePrice.isLessThan(prevClosePrice);
  }


  /**
   * @param index the current bar/candle index
   *
   * @return true if the current bar/candle is a black crow, false otherwise
   */
  private boolean isBlackCrow(final int index) {
    final Bar prevBar = this.bars.get(index - 1);
    final Bar currBar = this.bars.get(index);
    if (currBar.isBearish()) {
      if (prevBar.isBullish()) {
        // First crow case
        return hasVeryShortLowerShadow(index)
               && currBar.openPrice().isLessThan(prevBar.highPrice());
      } else {
        return hasVeryShortLowerShadow(index) && isDeclining(index);
      }
    }
    return false;
  }


  @Override
  public Boolean getValue() {
    return this.value;
  }


  @Override
  public void refresh(final Instant tick) {
    if (tick.isAfter(this.currentTick)) {
      this.averageLowerShadowInd.refresh(tick);
      this.lowerShadows.refresh(tick);
      this.value = calculate();

      this.currentTick = tick;
    }
  }


  @Override
  public boolean isStable() {
    return this.averageLowerShadowInd.isStable();
  }
}
