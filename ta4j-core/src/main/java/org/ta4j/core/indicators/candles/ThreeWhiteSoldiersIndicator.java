/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
 * Three white soldiers indicator.
 * 三名白人士兵指标。
 *
 * @see <a href="http://www.investopedia.com/terms/t/three_white_soldiers.asp">
 *      http://www.investopedia.com/terms/t/three_white_soldiers.asp</a>
 */
public class ThreeWhiteSoldiersIndicator extends CachedIndicator<Boolean> {

    /**
     * Upper shadow
     * 上阴影
     */
    private final UpperShadowIndicator upperShadowInd;
    /**
     * Average upper shadow
     * 平均上影线
     */
    private final SMAIndicator averageUpperShadowInd;
    /**
     * Factor used when checking if a candle has a very short upper shadow
     * 检查蜡烛是否有很短的上影线时使用的因素
     */
    private final Num factor;

    private int blackCandleIndex = -1;

    /**
     * Constructor. 构造器
     *
     * @param series   the bar series
     *                  bar 系列
     * @param barCount the number of bars used to calculate the average upper shadow
     *                 用于计算平均上影线的柱数
     * @param factor   the factor used when checking if a candle has a very short   upper shadow
     *                 检查蜡烛是否有很短的上影线时使用的因素
     */
    public ThreeWhiteSoldiersIndicator(BarSeries series, int barCount, Num factor) {
        super(series);
        upperShadowInd = new UpperShadowIndicator(series);
        averageUpperShadowInd = new SMAIndicator(upperShadowInd, barCount);
        this.factor = factor;
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < 3) {
            // We need 4 candles: 1 black, 3 white
            // 我们需要 4 支蜡烛：1 支黑色，3 支白色
            return false;
        }
        blackCandleIndex = index - 3;
        return getBarSeries().getBar(blackCandleIndex).isBearish() && isWhiteSoldier(index - 2)
                && isWhiteSoldier(index - 1) && isWhiteSoldier(index);
    }

    /**
     * @param index the bar/candle index
     *              柱/蜡烛指数
     * @return true if the bar/candle has a very short upper shadow, false otherwise
     *          如果柱/蜡烛的上影线很短，则为 true，否则为 false
     */
    private boolean hasVeryShortUpperShadow(int index) {
        Num currentUpperShadow = upperShadowInd.getValue(index);
        // We use the black candle index to remove to bias of the previous soldiers
        // 我们使用黑蜡烛指数来消除对先前士兵的偏见
        Num averageUpperShadow = averageUpperShadowInd.getValue(blackCandleIndex);

        return currentUpperShadow.isLessThan(averageUpperShadow.multipliedBy(factor));
    }

    /**
     * @param index the current bar/candle index
     *              当前柱/蜡烛指数
     * @return true if the current bar/candle is growing, false otherwise
     *          如果当前柱/蜡烛正在增长，则为 true，否则为 false
     */
    private boolean isGrowing(int index) {
        Bar prevBar = getBarSeries().getBar(index - 1);
        Bar currBar = getBarSeries().getBar(index);
        final Num prevOpenPrice = prevBar.getOpenPrice();
        final Num prevClosePrice = prevBar.getClosePrice();
        final Num currOpenPrice = currBar.getOpenPrice();
        final Num currClosePrice = currBar.getClosePrice();

        // Opens within the body of the previous candle
        // 在前一根蜡烛的主体内开盘
        return currOpenPrice.isGreaterThan(prevOpenPrice) && currOpenPrice.isLessThan(prevClosePrice)
        // Closes above the previous close price
                // 收盘价高于前一个收盘价
                && currClosePrice.isGreaterThan(prevClosePrice);
    }

    /**
     * @param index the current bar/candle index
     *              当前柱/蜡烛指数
     * @return true if the current bar/candle is a white soldier, false otherwise
     *          如果当前柱/蜡烛是白人士兵，则为 true，否则为 false
     */
    private boolean isWhiteSoldier(int index) {
        Bar prevBar = getBarSeries().getBar(index - 1);
        Bar currBar = getBarSeries().getBar(index);
        if (currBar.isBullish()) {
            if (prevBar.isBearish()) {
                // First soldier case
                // 第一个士兵案例
                return hasVeryShortUpperShadow(index) && currBar.getOpenPrice().isGreaterThan(prevBar.getLowPrice());
            } else {
                return hasVeryShortUpperShadow(index) && isGrowing(index);
            }
        }
        return false;
    }
}
