/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.num.Num;

/**
 * Three black crows indicator.
 *
 * @see <a href="http://www.investopedia.com/terms/t/three_black_crows.asp">
 *      http://www.investopedia.com/terms/t/three_black_crows.asp</a>
 */
public class ThreeBlackCrowsIndicator extends CachedIndicator<Boolean> {

    /** Lower shadow. */
    private final LowerShadowIndicator lowerShadowInd;

    /** Average lower shadow. */
    private final SMAIndicator averageLowerShadowInd;

    /** Factor used when checking if a candle has a very short lower shadow. */
    private final Num factor;

    /**
     * Constructor.
     *
     * @param series   the bar series
     * @param barCount the number of bars used to calculate the average lower shadow
     * @param factor   the factor used when checking if a candle has a very short
     *                 lower shadow
     */
    public ThreeBlackCrowsIndicator(BarSeries series, int barCount, double factor) {
        super(series);
        this.lowerShadowInd = new LowerShadowIndicator(series);
        this.averageLowerShadowInd = new SMAIndicator(lowerShadowInd, barCount);
        this.factor = numOf(factor);
    }

    @Override
    protected Boolean calculate(int index) {
        if (getBarSeries().getBeginIndex() > (index - 3)) {
            // We need 4 candles: 1 white, 3 black
            return false;
        }
        int whiteCandleIndex = index - 3;
        return getBarSeries().getBar(whiteCandleIndex).isBullish() && isBlackCrow(index - 2, whiteCandleIndex)
                && isBlackCrow(index - 1, whiteCandleIndex) && isBlackCrow(index, whiteCandleIndex);
    }

    @Override
    public int getUnstableBars() {
        return 4;
    }

    /**
     * @param index the bar/candle index
     * @return true if the bar/candle has a very short lower shadow, false otherwise
     */
    private boolean hasVeryShortLowerShadow(int index, int whiteCandleIndex) {
        Num currentLowerShadow = lowerShadowInd.getValue(index);
        // We use the white candle index to remove to bias of the previous crows
        Num averageLowerShadow = averageLowerShadowInd.getValue(whiteCandleIndex);

        return currentLowerShadow.isLessThan(averageLowerShadow.multipliedBy(factor));
    }

    /**
     * @param index the current bar/candle index
     * @return true if the current bar/candle is declining, false otherwise
     */
    private boolean isDeclining(int index) {
        Bar prevBar = getBarSeries().getBar(index - 1);
        Bar currBar = getBarSeries().getBar(index);
        final Num prevOpenPrice = prevBar.getOpenPrice();
        final Num prevClosePrice = prevBar.getClosePrice();
        final Num currOpenPrice = currBar.getOpenPrice();
        final Num currClosePrice = currBar.getClosePrice();

        // Opens within the body of the previous candle
        return currOpenPrice.isLessThan(prevOpenPrice) && currOpenPrice.isGreaterThan(prevClosePrice)
        // Closes below the previous close price
                && currClosePrice.isLessThan(prevClosePrice);
    }

    /**
     * @param index the current bar/candle index
     * @return true if the current bar/candle is a black crow, false otherwise
     */
    private boolean isBlackCrow(int index, int whiteCandleIndex) {
        Bar prevBar = getBarSeries().getBar(index - 1);
        Bar currBar = getBarSeries().getBar(index);
        if (currBar.isBearish()) {
            if (prevBar.isBullish()) {
                // First crow case
                return hasVeryShortLowerShadow(index, whiteCandleIndex)
                        && currBar.getOpenPrice().isLessThan(prevBar.getHighPrice());
            } else {
                return hasVeryShortLowerShadow(index, whiteCandleIndex) && isDeclining(index);
            }
        }
        return false;
    }
}
