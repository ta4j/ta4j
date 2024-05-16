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
 * Three black crows indicator.
 * 三只乌鸦指標。
 *
 * Three Black Crows（三只乌鸦）是技术分析中的一种反转形态，通常出现在上升趋势的顶部，预示着可能的趋势反转向下。它由三根连续的长阴线组成，每根阴线的收盘价接近或接近当日的最低价，而且每根阴线的开盘价都在前一根阴线的实体内。
 *
 * ### 特征
 * 1. **三根长阴线**：每根阴线的实体相对较长，显示卖方力量较强。
 * 2. **开盘价接近前一根阴线的最低价**：每根阴线的开盘价接近或接近前一根阴线的最低价，显示连续的卖压。
 * 3. **趋势确认**：出现 Three Black Crows 之前，通常市场处于上升趋势中。
 *
 * ### 识别方法
 * 要识别 Three Black Crows 形态，可以通过以下步骤：
 * 1. **确认上升趋势**：在形成 Three Black Crows 形态之前，市场通常处于上升趋势中。
 * 2. **三根长阴线**：连续出现三根长阴线，每根阴线的实体相对较长。
 * 3. **开盘价接近前一根阴线的最低价**：每根阴线的开盘价接近或接近前一根阴线的最低价，显示连续的卖压。
 *
 * ### 交易策略
 * 基于 Three Black Crows 形态的反转信号，可以考虑以下交易策略：
 * 1. **入场点**：在第三根阴线收盘后或第四根蜡烛线开始时考虑卖出或做空。
 * 2. **止损位**：将止损位设置在 Three Black Crows 形态的最高点上方，以控制风险。
 * 3. **目标位**：目标价位可以设在前一个支撑位，或根据风险回报比设定。
 *
 * ### 注意事项
 * - Three Black Crows 形态通常需要在上升趋势的顶部出现，才具有较高的可靠性。
 * - 交易者应谨慎对待单一形态的信号，建议结合其他技术分析方法和风险管理策略。
 * - 单独使用 Three Black Crows 形态进行交易可能会导致假信号，因此需要结合其他技术指标和趋势确认来提高准确性。
 *
 * ### 示例
 * 假设市场处于上升趋势中：
 * - **第一天**：出现一根长阴线，显示卖方力量开始增强。
 * - **第二天**：又一根长阴线，开盘价接近前一根阴线的最低价，卖方继续压制市场。
 * - **第三天**：再次出现一根长阴线，形成 Three Black Crows 形态，确认卖方力量的加强。
 *
 * 在第三根阴线收盘后或第四根蜡烛线开始时，可以考虑卖出或做空，止损位设在 Three Black Crows 形态的最高点上方，目标价位设在前一个支撑位附近。
 *
 * ### 总结
 * Three Black Crows 是一种强烈的反转信号，通常出现在上升趋势的顶部，预示着可能的趋势反转向下。通过识别连续三根长阴线，每根阴线的开盘价接近前一根阴线的最低价，可以确认 Three Black Crows 形态。交易者在使用该形态进行交易时，需要谨慎对待，并结合其他技术指标和趋势确认来提高准确性。
 *
 * @see <a href="http://www.investopedia.com/terms/t/three_black_crows.asp">
 *      http://www.investopedia.com/terms/t/three_black_crows.asp</a>
 */
public class ThreeBlackCrowsIndicator extends CachedIndicator<Boolean> {

    /**
     * Lower shadow
     * 下阴影
     */
    private final LowerShadowIndicator lowerShadowInd;
    /**
     * Average lower shadow
     * 平均下影线
     */
    private final SMAIndicator averageLowerShadowInd;
    /**
     * Factor used when checking if a candle has a very short lower shadow
     * 检查蜡烛是否有非常短的下影线时使用的因素
     */
    private final Num factor;

    private int whiteCandleIndex = -1;

    /**
     * Constructor.
     *
     * @param series   the bar series
     *                 酒吧系列
     * @param barCount the number of bars used to calculate the average lower shadow
     *                 用于计算平均下影线的柱数
     * @param factor   the factor used when checking if a candle has a very short  lower shadow
     *                 检查蜡烛是否有很短的下影线时使用的因素
     */
    public ThreeBlackCrowsIndicator(BarSeries series, int barCount, double factor) {
        super(series);
        lowerShadowInd = new LowerShadowIndicator(series);
        averageLowerShadowInd = new SMAIndicator(lowerShadowInd, barCount);
        this.factor = numOf(factor);
    }

    @Override
    protected Boolean calculate(int index) {
        if (index < 3) {
            // We need 4 candles: 1 white, 3 black
            //我们需要 4 支蜡烛：1 支白色，3 支黑色
            return false;
        }
        whiteCandleIndex = index - 3;
        return getBarSeries().getBar(whiteCandleIndex).isBullish() && isBlackCrow(index - 2) && isBlackCrow(index - 1)
                && isBlackCrow(index);
    }

    /**
     * @param index the bar/candle index
     *              柱/蜡烛指数
     * @return true if the bar/candle has a very short lower shadow, false otherwise
     *          如果柱/蜡烛的下影线很短，则为 true，否则为 false
     */
    private boolean hasVeryShortLowerShadow(int index) {
        Num currentLowerShadow = lowerShadowInd.getValue(index);
        // We use the white candle index to remove to bias of the previous crows
        // 我们使用白色蜡烛指数来消除先前乌鸦的偏见
        Num averageLowerShadow = averageLowerShadowInd.getValue(whiteCandleIndex);

        return currentLowerShadow.isLessThan(averageLowerShadow.multipliedBy(factor));
    }

    /**
     * @param index the current bar/candle index
     *              当前柱/蜡烛指数
     * @return true if the current bar/candle is declining, false otherwise
     * 如果当前柱/蜡烛正在下降，则为 true，否则为 false
     */
    private boolean isDeclining(int index) {
        Bar prevBar = getBarSeries().getBar(index - 1);
        Bar currBar = getBarSeries().getBar(index);
        final Num prevOpenPrice = prevBar.getOpenPrice();
        final Num prevClosePrice = prevBar.getClosePrice();
        final Num currOpenPrice = currBar.getOpenPrice();
        final Num currClosePrice = currBar.getClosePrice();

        // Opens within the body of the previous candle
        // 在前一根蜡烛的主体内开盘
        return currOpenPrice.isLessThan(prevOpenPrice) && currOpenPrice.isGreaterThan(prevClosePrice)
        // Closes below the previous close price
                // 收盘价低于前一个收盘价
                && currClosePrice.isLessThan(prevClosePrice);
    }

    /**
     * @param index the current bar/candle index
     *              当前柱/蜡烛指数
     * @return true if the current bar/candle is a black crow, false otherwise
     *          如果当前柱/蜡烛是黑色乌鸦，则为 true，否则为 false
     */
    private boolean isBlackCrow(int index) {
        Bar prevBar = getBarSeries().getBar(index - 1);
        Bar currBar = getBarSeries().getBar(index);
        if (currBar.isBearish()) {
            if (prevBar.isBullish()) {
                // First crow case
                // 第一个乌鸦案例
                return hasVeryShortLowerShadow(index) && currBar.getOpenPrice().isLessThan(prevBar.getHighPrice());
            } else {
                return hasVeryShortLowerShadow(index) && isDeclining(index);
            }
        }
        return false;
    }
}
