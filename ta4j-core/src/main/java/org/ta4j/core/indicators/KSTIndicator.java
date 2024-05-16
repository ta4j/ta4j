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
 * Know Sure Thing (KST) RCMA1 = X1-Period SMA of Y1-Period Rate-of-Change RCMA2
 = X2-Period SMA of Y2-Period Rate-of-Change RCMA3 = X3-Period SMA of
  Y3-Period Rate-of-Change RCMA4 = X4-Period SMA of Y4-Period Rate-of-Change
  KST = (RCMA1 x 1) + (RCMA2 x 2) + (RCMA3 x 3) + (RCMA4 x 4)

 知道确定的事情
 （KST） RCMA1 = Y1 周期变化率的 X1 周期 SMA RCMA2 = Y2 周期的 SMA 变化率 RCMA3 = Y3 周期的 X3 周期 SMA 变化率 RCMA4 = X4 周期 Y4 周期的 SMA 变化率 KST = （RCMA1 x 1） + （RCMA2 x 2） + （RCMA3 x 3） + （RCMA4 x 4）
 *
 *
 * KST指标（Know Sure Thing Indicator）是由马丁·普林斯（Martin Pring）于1992年开发的一种技术指标，旨在识别市场的趋势转折点。它是一种复合指标，通过结合四个不同周期的平滑移动平均线和变动率来分析价格数据。
 *
 * KST指标的计算过程相对复杂，通常分为以下几个步骤：
 *
 * 1. 计算四个不同周期的平滑移动平均线（SMA）：这些周期通常是9、12、18和24个周期。
 * 2. 计算每个周期内的价格变动率：通过计算每个周期的价格变化百分比来确定价格的变动率。
 * 3. 对不同周期的价格变动率进行加权求和：将四个不同周期的价格变动率乘以不同的权重系数，并求和。
 * 4. 应用一个二次平滑移动平均线（SMA）来平滑加权和，以得到最终的KST指标值。
 *
 * KST指标的数值通常波动在零线上下，正值表示价格处于上升趋势，负值表示价格处于下降趋势。交易者可以根据KST指标的数值变化来确定价格的趋势转折点，并据此制定买卖策略。
 *
 * 总的来说，KST指标是一种综合性的技术指标，结合了不同周期的平滑移动平均线和价格变动率，能够较为准确地识别市场的趋势转折点。然而，由于其复杂的计算过程和较长的时间周期，交易者在使用KST指标时需要谨慎考虑，并结合其他技术指标和价格模式进行综合分析。
 *
 * @see <a href=
 *      "https://school.stockcharts.com/doku.php?id=technical_indicators:know_sure_thing_kst">
 *      https://school.stockcharts.com/doku.php?id=technical_indicators:know_sure_thing_kst
 *      </a>
 */
public class KSTIndicator extends CachedIndicator<Num> {
    private SMAIndicator RCMA1;
    private SMAIndicator RCMA2;
    private SMAIndicator RCMA3;
    private SMAIndicator RCMA4;

    /**
     *
     * @param indicator the indicator. Default parameters: RCMA1 = 10-Period SMA of
                       10-Period Rate-of-Change RCMA2 = 10-Period SMA of 15-Period
                      Rate-of-Change RCMA3 = 10-Period SMA of 20-Period
                        Rate-of-Change RCMA4 = 15-Period SMA of 30-Period
                       Rate-of-Change
                指标。 默认参数：RCMA1 = 10-Period SMA
                10 周期变化率 RCMA2 = 15 周期的 10 周期 SMA
                变化率 RCMA3 = 20 周期的 10 周期 SMA
                变化率 RCMA4 = 30 周期的 15 周期 SMA
                变化率
     */
    public KSTIndicator(Indicator<Num> indicator) {
        super(indicator);
        this.RCMA1 = new SMAIndicator(new ROCIndicator(indicator, 10), 10);
        this.RCMA2 = new SMAIndicator(new ROCIndicator(indicator, 15), 10);
        this.RCMA3 = new SMAIndicator(new ROCIndicator(indicator, 20), 10);
        this.RCMA4 = new SMAIndicator(new ROCIndicator(indicator, 30), 15);
    }

    /**
     *
     * @param indicator        the indicator.
     *                         指标。
     *
     * @param rcma1SMABarCount RCMA1 SMA period.
     *                         RCMA1 SMA 周期。
     *
     * @param rcma1ROCBarCount RCMA1 ROC period.
     *                         RCMA1 ROC 周期。
     *
     * @param rcma2SMABarCount RCMA2 SMA period.
     *                         RCMA2 SMA 周期。
     *
     * @param rcma2ROCBarCount RCMA2 ROC period.
     *                         RCMA2 ROC 周期。
     *
     * @param rcma3SMABarCount RCMA3 SMA period.
     *                         RCMA3 SMA 周期。
     *
     * @param rcma3ROCBarCount RCMA3 ROC period.
     *                         RCMA3 ROC 时期。
     *
     * @param rcma4SMABarCount RCMA4 SMA period.
     *                         RCMA4 SMA 周期。
     *
     * @param rcma4ROCBarCount RCMA4 ROC period.
     *                         RCMA4 ROC 时期。
     */
    public KSTIndicator(Indicator<Num> indicator, int rcma1SMABarCount, int rcma1ROCBarCount, int rcma2SMABarCount,
            int rcma2ROCBarCount, int rcma3SMABarCount, int rcma3ROCBarCount, int rcma4SMABarCount,
            int rcma4ROCBarCount) {
        super(indicator);
        this.RCMA1 = new SMAIndicator(new ROCIndicator(indicator, rcma1ROCBarCount), rcma1SMABarCount);
        this.RCMA2 = new SMAIndicator(new ROCIndicator(indicator, rcma2ROCBarCount), rcma2SMABarCount);
        this.RCMA3 = new SMAIndicator(new ROCIndicator(indicator, rcma3ROCBarCount), rcma3SMABarCount);
        this.RCMA4 = new SMAIndicator(new ROCIndicator(indicator, rcma4ROCBarCount), rcma4SMABarCount);
    }

    @Override
    protected Num calculate(int index) {
        Num RCMA1Multiplier = numOf(1);
        Num RCMA2Multiplier = numOf(2);
        Num RCMA3Multiplier = numOf(3);
        Num RCMA4Multiplier = numOf(4);

        return ((RCMA1.getValue(index).multipliedBy(RCMA1Multiplier))
                .plus(RCMA2.getValue(index).multipliedBy(RCMA2Multiplier))
                .plus(RCMA3.getValue(index).multipliedBy(RCMA3Multiplier))
                .plus(RCMA4.getValue(index).multipliedBy(RCMA4Multiplier)));
    }
}
