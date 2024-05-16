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

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Gain indicator.
 * 增益指标。
 *
 * “Gain indicator”（收益指标）通常是指用于衡量资产或投资组合的收益水平或变化的指标。这种指标可以帮助投资者评估其投资的表现，并且在制定投资策略时提供重要参考。
 *
 * 收益指标可以有多种形式，具体取决于所关注的投资对象和目标。以下是一些常见的收益指标：
 *
 * 1. **绝对收益**：投资组合或资产在一定时间内的实际收益数额，通常以货币单位（例如美元）或百分比表示。
 *
 * 2. **相对收益**：投资组合或资产相对于某个参考标准（例如基准指数或同行业平均水平）的收益表现。
 *
 * 3. **年化收益率**：将投资组合或资产在一段时间内的收益率转换为年化收益率，以便更好地比较不同时间段或不同投资对象的表现。
 *
 * 4. **累计收益**：投资组合或资产从投资开始到目前为止的累计总收益。
 *
 * 5. **每单位风险的收益**：收益与风险之间的关系，通常使用指标如夏普比率（Sharpe Ratio）来衡量。
 *
 * 这些收益指标在投资管理和资产配置中是非常重要的，因为它们提供了对投资表现的量化评估，并且可以帮助投资者进行投资决策。然而，在使用这些指标时，投资者还应该考虑到其他因素，如风险、流动性和投资目标，以便做出更全面的评估。
 */
public class LossIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;

    public LossIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.indicator = indicator;
    }

    @Override
    protected Num calculate(int index) {
        if (index == 0) {
            return numOf(0);
        }
        if (indicator.getValue(index).isLessThan(indicator.getValue(index - 1))) {
            return indicator.getValue(index - 1).minus(indicator.getValue(index));
        } else {
            return numOf(0);
        }
    }
}
