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

import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Triple exponential moving average indicator.
 * 三重指数移动平均线指标。
 *
 * a.k.a TRIX
 * 又名TRIX
 *
 * TEMA needs "3 * period - 2" of data to start producing values in contrast to the period samples needed by a regular EMA.
 * TEMA 需要“3 个周期 - 2”的数据才能开始生成值，这与常规 EMA 所需的周期样本形成鲜明对比。
 *
 *
 *
 * 三重指数移动平均线（Triple Exponential Moving Average，TEMA）是一种基于指数移动平均的技术指标，用于平滑价格数据并识别价格的趋势。
 *
 * TEMA指标是对指数移动平均线的改进，它通过多次对价格数据进行指数平滑处理，从而更有效地捕捉价格的长期趋势。与普通的指数移动平均线相比，TEMA指标更加平滑，反应速度更慢，但更能准确地反映价格的长期变化。
 *
 * TEMA指标的计算过程相对复杂，需要多次对价格数据进行指数平滑处理。基本上，计算TEMA指标需要以下几个步骤：
 *
 * 1. 首先计算单重指数移动平均线（EMA），通常使用2个周期的指数平滑系数。
 * 2. 然后计算双重指数移动平均线，即对第一步得到的EMA再次进行指数平滑处理，使用同样的指数平滑系数。
 * 3. 最后再次对双重指数移动平均线进行指数平滑处理，使用同样的指数平滑系数，得到TEMA指标的数值。
 *
 * TEMA指标的数值通常在价格图上以一条平滑的曲线显示，它可以帮助交易者更清晰地识别价格的长期趋势，并据此制定买卖策略。
 *
 * 交易者通常使用TEMA指标来确认价格的长期趋势，并结合其他技术指标和价格模式来进行交易决策。例如，当价格上穿TEMA线时，可能暗示着价格的上升趋势开始，为买入信号；相反，当价格下穿TEMA线时，可能暗示着价格的下跌趋势开始，为卖出信号。
 *
 * 总的来说，TEMA指标是一种用于平滑价格数据并识别价格趋势的技术指标，可以帮助交易者更好地理解市场的价格动态并制定相应的交易策略。
 *
 * @see <a href=
 *      "https://en.wikipedia.org/wiki/Triple_exponential_moving_average">https://en.wikipedia.org/wiki/Triple_exponential_moving_average</a>
 * @see <a href=
 *      "https://www.investopedia.com/terms/t/triple-exponential-moving-average.asp">https://www.investopedia.com/terms/t/triple-exponential-moving-average.asp</a>
 */
public class TripleEMAIndicator extends CachedIndicator<Num> {

    private final int barCount;
    private final EMAIndicator ema;
    private final EMAIndicator emaEma;
    private final EMAIndicator emaEmaEma;

    /**
     * Constructor.
     *
     * @param indicator the indicator 指標
     * @param barCount  the time frame 時間範圍
     */
    public TripleEMAIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.barCount = barCount;
        this.ema = new EMAIndicator(indicator, barCount);
        this.emaEma = new EMAIndicator(ema, barCount);
        this.emaEmaEma = new EMAIndicator(emaEma, barCount);
    }

    @Override
    protected Num calculate(int index) {
        // trix = 3 * ( ema - emaEma ) + emaEmaEma
        // trix = 3 * ( ema - emaEma ) + emaEmaEma
        return numOf(3).multipliedBy(ema.getValue(index).minus(emaEma.getValue(index))).plus(emaEmaEma.getValue(index));
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " barCount: " + barCount;
    }
}
