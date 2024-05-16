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
 * 三只白兵。
 *
 * Three White Soldiers（三只白兵）是技术分析中的一种反转形态，通常出现在下降趋势的底部，预示着可能的趋势反转向上。它由三根连续的长阳线组成，每根阳线的收盘价接近或接近当日的最高价，而且每根阳线的开盘价都在前一根阳线的实体内。
 *
 * ### 特征
 * 1. **三根长阳线**：每根阳线的实体相对较长，显示买方力量较强。
 * 2. **收盘价接近前一根阳线的最高价**：每根阳线的收盘价接近或接近前一根阳线的最高价，显示连续的买压。
 * 3. **趋势确认**：出现 Three White Soldiers 之前，通常市场处于下降趋势中。
 *
 * ### 识别方法
 * 要识别 Three White Soldiers 形态，可以通过以下步骤：
 * 1. **确认下降趋势**：在形成 Three White Soldiers 形态之前，市场通常处于下降趋势中。
 * 2. **三根长阳线**：连续出现三根长阳线，每根阳线的实体相对较长。
 * 3. **收盘价接近前一根阳线的最高价**：每根阳线的收盘价接近或接近前一根阳线的最高价，显示连续的买压。
 *
 * ### 交易策略
 * 基于 Three White Soldiers 形态的反转信号，可以考虑以下交易策略：
 * 1. **入场点**：在第三根阳线收盘后或第四根蜡烛线开始时考虑买入或做多。
 * 2. **止损位**：将止损位设置在 Three White Soldiers 形态的最低点下方，以控制风险。
 * 3. **目标位**：目标价位可以设在前一个阻力位，或根据风险回报比设定。
 *
 * ### 注意事项
 * - Three White Soldiers 形态通常需要在下降趋势的底部出现，才具有较高的可靠性。
 * - 交易者应谨慎对待单一形态的信号，建议结合其他技术分析方法和风险管理策略。
 * - 单独使用 Three White Soldiers 形态进行交易可能会导致假信号，因此需要结合其他技术指标和趋势确认来提高准确性。
 *
 * ### 示例
 * 假设市场处于下降趋势中：
 * - **第一天**：出现一根长阳线，显示买方力量开始增强。
 * - **第二天**：又一根长阳线，收盘价接近前一根阳线的最高价，买方继续推动市场上涨。
 * - **第三天**：再次出现一根长阳线，形成 Three White Soldiers 形态，确认买方力量的加强。
 *
 * 在第三根阳线收盘后或第四根蜡烛线开始时，可以考虑买入或做多，止损位设在 Three White Soldiers 形态的最低点下方，目标价位设在前一个阻力位附近。
 *
 * ### 总结
 * Three White Soldiers 是一种强烈的反转信号，通常出现在下降趋势的底部，预示着可能的趋势反转向上。通过识别连续三根长阳线，每根阳线的收盘价接近前一根阳线的最高价，可以确认 Three White Soldiers 形态。交易者在使用该形态进行交易时，需要谨慎对待，并结合其他技术指标和趋势确认来提高准确性。
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
