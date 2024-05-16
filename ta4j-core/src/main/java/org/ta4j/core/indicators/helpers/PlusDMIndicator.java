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
package org.ta4j.core.indicators.helpers;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * +DM indicator.
 * +DM 指标。
 *
 * +DM指标，全称为“正向趋向指标”（Positive Directional Movement Indicator），是一种用于衡量股票或其他金融资产上升趋势强度的技术分析指标。
 *
 * +DM指标通常与-DM指标一起使用，以构建出Wilder的平均趋向指数（ADX）系统。这个系统旨在帮助交易者确定趋势的方向和强度。
 *
 * +DM指标的计算方法涉及比较前一天的最高价和当前交易日的最高价，如果当前交易日的最高价高于前一天的最高价，则+DM的值为当前交易日的最高价减去前一天的最高价；否则，+DM为0。然后，这些正向移动方向变化的值被累积，并进行平滑处理以得到平均的正向趋向指标。
 *
 * +DM指标通常用于与-DM指标一起计算ADX指标，从而帮助识别市场中的趋势以及趋势的强度。ADX指标的值范围从0到100，较高的数值表示趋势较强，而较低的数值则表示趋势较弱。
 *
 */
public class PlusDMIndicator extends CachedIndicator<Num> {

    public PlusDMIndicator(BarSeries series) {
        super(series);
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return numOf(0);
        }
        final Bar prevBar = getBarSeries().getBar(index - 1);
        final Bar currentBar = getBarSeries().getBar(index);

        final Num upMove = currentBar.getHighPrice().minus(prevBar.getHighPrice());
        final Num downMove = prevBar.getLowPrice().minus(currentBar.getLowPrice());
        if (upMove.isGreaterThan(downMove) && upMove.isGreaterThan(numOf(0))) {
            return upMove;
        } else {
            return numOf(0);
        }
    }
}
