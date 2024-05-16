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
package org.ta4j.core.indicators;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.num.Num;

/**
 * The Stochastic RSI Indicator.
 * 随机 RSI 指标。
 *
 * Stochastic RSI指标是基于相对强弱指数（Relative Strength Index，RSI）和随机振荡器（Stochastic Oscillator）的一种变体，结合了它们两者的特点，用于衡量价格的超买和超卖情况。
 *
 * Stochastic RSI指标的计算基于RSI指标和随机振荡器K线的数值。通常情况下，计算Stochastic RSI指标需要以下几个步骤：
 *
 * 1. 首先计算RSI指标的数值，用于衡量价格的相对强弱。
 * 2. 然后计算RSI指标数值在一段时间内的移动平均值，通常选择3至5个周期的移动平均。
 * 3. 接着计算RSI指标数值相对于其移动平均值的位置，得到%K线的数值。
 * 4. 最后对%K线进行平滑处理，通常使用简单移动平均线或指数移动平均线，得到Stochastic RSI指标的数值。
 *
 * Stochastic RSI指标的数值通常在0到100之间波动。与传统的Stochastic Oscillator相比，Stochastic RSI指标更加平滑，因为它是基于RSI指标的移动平均值计算得出的。
 *
 * 交易者通常使用Stochastic RSI指标来识别价格的超买和超卖情况，并据此制定买卖策略。例如，当Stochastic RSI指标从超卖区域上升至超买区域时，可能暗示着价格即将下跌，为卖出信号；相反，当Stochastic RSI指标从超买区域下降至超卖区域时，可能暗示着价格即将上涨，为买入信号。
 *
 * 总的来说，Stochastic RSI指标是一种结合了RSI指标和随机振荡器的特点，用于识别价格的超买和超卖情况的技术指标，可以帮助交易者更好地理解市场的价格动态并制定相应的交易策略。
 * 
 * Stoch RSI = (RSI - MinimumRSIn) / (MaximumRSIn - MinimumRSIn)
 * 随机 RSI = (RSI - 最小 RSIn) / (最大 RAIn - 最小 RAIn)
 */
public class StochasticRSIIndicator extends CachedIndicator<Num> {

    private final RSIIndicator rsi;
    private final LowestValueIndicator minRsi;
    private final HighestValueIndicator maxRsi;

    /**
     * Constructor. In most cases, this should be used to avoid confusion over what Indicator parameters should be used.
     * * 构造函数。 在大多数情况下，这应该用来避免混淆应该使用哪些指标参数。
     * 
     * @param series   the series 序列
     * @param barCount the time frame 時間範圍
     */
    public StochasticRSIIndicator(BarSeries series, int barCount) {
        this(new ClosePriceIndicator(series), barCount);
    }

    /**
     * Constructor.
     * 
     * @param indicator the Indicator, in practice is always a ClosePriceIndicator.
     *                  指标，在实践中始终是 ClosePriceIndicator。
     * @param barCount  the time frame  時間範圍
     */
    public StochasticRSIIndicator(Indicator<Num> indicator, int barCount) {
        this(new RSIIndicator(indicator, barCount), barCount);
    }

    /**
     * Constructor.
     * 
     * @param rsiIndicator the rsi indicator  rsi 指标
     * @param barCount     the time frame 時間指標
     */
    public StochasticRSIIndicator(RSIIndicator rsiIndicator, int barCount) {
        super(rsiIndicator);
        this.rsi = rsiIndicator;
        minRsi = new LowestValueIndicator(rsiIndicator, barCount);
        maxRsi = new HighestValueIndicator(rsiIndicator, barCount);
    }

    @Override
    protected Num calculate(int index) {
        Num minRsiValue = minRsi.getValue(index);
        return rsi.getValue(index).minus(minRsiValue).dividedBy(maxRsi.getValue(index).minus(minRsiValue));
    }

}
