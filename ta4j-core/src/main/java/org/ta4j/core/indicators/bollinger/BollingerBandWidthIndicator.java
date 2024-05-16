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
package org.ta4j.core.indicators.bollinger;

import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Bollinger BandWidth indicator.
 * 博林格带宽度指标。
 *
 * Bollinger BandWidth（布林带宽）是一个技术分析指标，用于衡量布林带的宽度。布林带由约翰·布林格（John Bollinger）创建，是基于标准差的波动性指标。布林带宽有助于识别市场的波动性和潜在的趋势变化。
 *
 * ### 计算方法
 * 布林带由三条线组成：
 * 1. **中线（移动平均线，通常为20期简单移动平均线，SMA）**。
 * 2. **上轨线（中线加上两倍的标准差）**。
 * 3. **下轨线（中线减去两倍的标准差）**。
 *
 * 布林带宽的计算公式如下：
 * BandWidth= (上轨线 - 下轨线) / 中线 * 100
 *
 * ### 解读Bollinger BandWidth
 * 1. **高带宽**：表示市场波动性较大，价格可能有较大的波动范围。
 * 2. **低带宽**：表示市场波动性较小，价格可能处于盘整阶段。
 *
 * ### 使用策略
 * 1. **识别波动性变化**：
 *     - 当布林带宽度扩大时，通常表示市场进入高波动性阶段，可能出现大的价格变动。
 *     - 当布林带宽度收窄时，通常表示市场进入低波动性阶段，可能处于盘整或即将发生趋势变动。
 *
 * 2. **交易信号**：
 *     - **收窄的布林带宽**：如果布林带宽度达到较低值（通常称为“布林带收缩”），这可能预示着即将出现剧烈的价格波动，是潜在的交易机会。
 *     - **扩大的布林带宽**：如果布林带宽度达到较高值（通常称为“布林带扩张”），这可能表示当前的趋势已经过度延伸，市场可能会出现修正。
 *
 * 3. **结合其他指标**：
 *     - 布林带宽可以与其他技术指标结合使用，如相对强弱指数（RSI）或移动平均收敛散度（MACD），以确认交易信号的有效性。
 *
 * ### 示例
 * - **布林带收缩**：在布林带宽度较窄的情况下，价格通常会在布林带的上下轨之间窄幅波动。当布林带宽度进一步收窄到一个极端低值时，交易者可以准备捕捉即将到来的突破。
 * - **布林带扩张**：在布林带宽度较宽的情况下，价格通常会有较大幅度的波动。当布林带宽度达到一个极端高值时，交易者可以考虑市场可能的反转或回调。
 *
 * ### 注意事项
 * - **滞后性**：布林带和布林带宽度都是基于历史价格数据的指标，可能会滞后于市场的实际波动。
 * - **结合其他分析方法**：布林带宽度应与其他技术分析工具结合使用，以提高交易信号的准确性。
 * - **市场环境**：不同市场和不同时间框架下，布林带的参数和表现可能有所不同，需要根据具体情况调整。
 *
 * ### 总结
 * Bollinger BandWidth 指标通过衡量布林带的宽度，帮助交易者识别市场波动性的变化和潜在的趋势变动。它是一个有效的工具，尤其是在布林带收缩或扩张时，可以为交易者提供有价值的交易信号。结合其他技术指标使用，可以提高交易决策的准确性。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_width">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:bollinger_band_width</a>
 */
public class BollingerBandWidthIndicator extends CachedIndicator<Num> {

    private final BollingerBandsUpperIndicator bbu;
    private final BollingerBandsMiddleIndicator bbm;
    private final BollingerBandsLowerIndicator bbl;
    private final Num hundred;

    /**
     * Constructor.
     * 构造函数。
     *
     * @param bbu the upper band Indicator.
     *            上带指标。
     * @param bbm the middle band Indicator. Typically an SMAIndicator is used.
     *            中带指标。 通常使用 SMAIndicator。
     * @param bbl the lower band Indicator.
     *            低频段指标。
     */
    public BollingerBandWidthIndicator(BollingerBandsUpperIndicator bbu, BollingerBandsMiddleIndicator bbm,
            BollingerBandsLowerIndicator bbl) {
        super(bbm.getBarSeries());
        this.bbu = bbu;
        this.bbm = bbm;
        this.bbl = bbl;
        this.hundred = bbm.getBarSeries().numOf(100);
    }

    @Override
    protected Num calculate(int index) {
        return bbu.getValue(index).minus(bbl.getValue(index)).dividedBy(bbm.getValue(index)).multipliedBy(hundred);
    }
}
