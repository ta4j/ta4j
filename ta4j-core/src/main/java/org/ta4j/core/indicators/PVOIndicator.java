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
import org.ta4j.core.indicators.helpers.VolumeIndicator;

/**
 * Percentage Volume Oscillator (PVO): ((12-day EMA of Volume - 26-day EMA of Volume)/26-day EMA of Volume) x 100
 * * 成交量振荡器百分比 (PVO): ((12 天成交量均线 - 26 天成交量均线)/26 天成交量均线) x 100
 *
 * 百分比成交量振荡器（Percentage Volume Oscillator，PVO）是一种技术指标，用于衡量成交量的趋势和波动。它通过计算两个不同期间的成交量指数加权移动平均线（EMA）之间的百分比差异来确定成交量的变化情况。
 *
 * PVO的计算公式如下所示：
 *
 * ((12-day EMA of Volume - 26-day EMA of Volume)/26-day EMA of Volume) x 100
 * ((12 天成交量均线 - 26 天成交量均线)/26 天成交量均线) x 100
 *
 * 其中：
 * - "12日成交量EMA" 表示12个交易周期的成交量指数加权移动平均线。
 * - "26日成交量EMA" 表示26个交易周期的成交量指数加权移动平均线。
 *
 * 计算PVO时，首先计算出12日和26日的成交量指数加权移动平均线，然后计算它们之间的差异，并将其除以26日的成交量指数加权移动平均线，最后乘以100得到百分比差异。
 *
 * PVO指标的数值通常波动在零线上下，正值表示较短期成交量高于较长期成交量，可能暗示着成交量的增加；负值表示较短期成交量低于较长期成交量，可能暗示着成交量的减少。
 *
 * 交易者通常使用PVO来识别成交量的趋势变化和制定买卖信号。例如，当PVO线上穿零线时，可能暗示着成交量的增加，为买入信号；相反，当PVO线下穿零线时，可能暗示着成交量的减少，为卖出信号。
 *
 * 总的来说，PVO是一种用于衡量成交量趋势和波动的技术指标，可以帮助交易者更好地理解市场的成交量动态，但仍建议将其与其他技术指标和价格模式结合使用，以增强交易决策的准确性。
 *
 * @see <a href=
 *      "https://school.stockcharts.com/doku.php?id=technical_indicators:percentage_volume_oscillator_pvo">
 *      https://school.stockcharts.com/doku.php?id=technical_indicators:percentage_volume_oscillator_pvo
 *      </a>
 */
public class PVOIndicator extends PPOIndicator {

    /**
     * @param series the bar series {@link BarSeries}. Will use PPO default  constructor with shortBarCount "12" and longBarCount "26".
     *               酒吧系列{@link BarSeries}。 将使用带有 shortBarCount "12" 和 longBarCount "26" 的 PPO 默认构造函数。
     */
    public PVOIndicator(BarSeries series) {
        super(new VolumeIndicator(series));
    }

    /**
     * @param series         the bar series {@link BarSeries}.
     *                       酒吧系列{@link BarSeries}。
     * @param volumeBarCount Volume Indicator bar count. Will use PPO default   constructor with shortBarCount "12" and longBarCount  "26".
     *                       音量指示条计数。 将使用带有 shortBarCount "12" 和 longBarCount "26" 的 PPO 默认构造函数。
     */
    public PVOIndicator(BarSeries series, int volumeBarCount) {
        super(new VolumeIndicator(series, volumeBarCount));
    }

    /**
     * @param series        the bar series {@link BarSeries}.
     *                      酒吧系列{@link BarSeries}。
     *
     * @param shortBarCount PPO short time frame.
     *                      PPO 短时间(範圍)框架。
     *
     * @param longBarCount  PPO long time frame.
     *                      PPO 长時間(範圍)框架。
     */
    public PVOIndicator(BarSeries series, int shortBarCount, int longBarCount) {
        super(new VolumeIndicator(series), shortBarCount, longBarCount);
    }

    /**
     * @param series         the bar series {@link BarSeries}.
     *                       酒吧系列{@link BarSeries}。
     *
     * @param volumeBarCount Volume Indicator bar count.
     *                       |音量指示条计数。
     *
     * @param shortBarCount  PPO short time frame.
     *                       PPO 短时间(範圍)框架。
     * @param longBarCount   PPO long time frame.
     *                       PPO 長時間範圍
     */
    public PVOIndicator(BarSeries series, int volumeBarCount, int shortBarCount, int longBarCount) {
        super(new VolumeIndicator(series, volumeBarCount), shortBarCount, longBarCount);
    }

}
