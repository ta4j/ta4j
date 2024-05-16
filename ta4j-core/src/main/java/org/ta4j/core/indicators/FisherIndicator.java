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
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * The Fisher Indicator.
 * 费舍尔指标。
 *
 * Fisher指标是一种基于价格和体积的技术指标，旨在识别价格的趋势方向和市场的买卖信号。它是由约翰·埃勒斯（John Ehlers）开发的，具有相当的复杂性和独特性。
 *
 * Fisher指标的计算过程相对复杂，通常分为以下几个步骤：
 *
 * 1. 计算价格的中间价（Typical Price）：通常是最高价、最低价和收盘价的加权平均值。
 * 2. 计算价格的平滑系数：使用一种平滑系数来平滑价格的中间价，以减少价格的随机波动。
 * 3. 计算价格的变化率：计算价格的变化率，用于确定价格的趋势方向。
 * 4. 计算Fisher变换值：将价格的变化率转换为Fisher变换值，以便更好地识别价格的买卖信号。
 *
 * Fisher指标的数值通常波动在一个固定的范围内，正值表示上涨趋势，负值表示下跌趋势。交易者可以根据Fisher指标的数值变化来确定市场的短期和长期趋势，并据此制定交易策略。
 *
 * Fisher指标的优点之一是它对价格的平滑处理，能够有效减少价格数据中的噪音，并帮助交易者更准确地识别价格的趋势。然而，由于其相对复杂的计算过程，以及对参数设置的敏感性，交易者在使用Fisher指标时需要谨慎考虑，并结合其他技术指标和价格模式进行综合分析。
 *
 * @apiNote Minimal deviations in last Num places possible. During the  calculations this indicator converts {@link Num Num} to  {@link Double double}
 * @apiNote 最后 Num 个位置的偏差可能最小。 在计算过程中，该指标将 {@link Num Num} 转换为 {@link Double double}
 * @see <a href=
 *      "http://www.tradingsystemlab.com/files/The%20Fisher%20Transform.pdf">
 *      http://www.tradingsystemlab.com/files/The%20Fisher%20Transform.pdf</a>
 * @see <a href="https://www.investopedia.com/terms/f/fisher-transform.asp">
 *      https://www.investopedia.com/terms/f/fisher-transform.asp</a>
 */
public class FisherIndicator extends RecursiveCachedIndicator<Num> {

    private static final double ZERO_DOT_FIVE = 0.5;
    private static final double VALUE_MAX = 0.999;
    private static final double VALUE_MIN = -0.999;

    private final Indicator<Num> ref;
    private final Indicator<Num> intermediateValue;
    private final Num densityFactor;
    private final Num gamma;
    private final Num delta;

    /**
     * Constructor.
     *
     * @param series the series
     *               该系列
     */
    public FisherIndicator(BarSeries series) {
        this(new MedianPriceIndicator(series), 10);
    }

    /**
     * Constructor (with alpha 0.33, beta 0.67, gamma 0.5, delta 0.5).
     * * 构造函数（alpha 0.33，beta 0.67，gamma 0.5，delta 0.5）。
     *
     * @param price    the price indicator (usually {@link MedianPriceIndicator})
     *                 价格指标（通常是 {@link MedianPriceIndicator}）
     * @param barCount the time frame (usually 10)
     *                 时间范围（通常为 10）
     */
    public FisherIndicator(Indicator<Num> price, int barCount) {
        this(price, barCount, 0.33, 0.67, ZERO_DOT_FIVE, ZERO_DOT_FIVE, 1, true);
    }

    /**
     * Constructor (with gamma 0.5, delta 0.5).
     * 构造函数（gamma 0.5，delta 0.5）。
     *
     * @param price    the price indicator (usually {@link MedianPriceIndicator})
     *                 价格指标（通常是 {@link MedianPriceIndicator}）
     * @param barCount the time frame (usually 10)
     *                 时间范围（通常为 10）
     * @param alpha    the alpha (usually 0.33 or 0.5)
     *                 alpha（通常为 0.33 或 0.5）
     * @param beta     the beta (usually 0.67 0.5 or)
     *                 贝塔（通常为 0.67 0.5 或）
     */
    public FisherIndicator(Indicator<Num> price, int barCount, double alpha, double beta) {
        this(price, barCount, alpha, beta, ZERO_DOT_FIVE, ZERO_DOT_FIVE, 1, true);
    }

    /**
     * Constructor.
     *
     * @param price    the price indicator (usually {@link MedianPriceIndicator})
     *                 价格指标（通常是 {@link MedianPriceIndicator}）
     * @param barCount the time frame (usually 10)
     *                 时间范围（通常为 10）
     * @param alpha    the alpha (usually 0.33 or 0.5)
     *                 alpha（通常为 0.33 或 0.5）
     * @param beta     the beta (usually 0.67 or 0.5)
     *                 贝塔（通常为 0.67 或 0.5）
     * @param gamma    the gamma (usually 0.25 or 0.5)
     *                 伽玛（通常为 0.25 或 0.5）
     * @param delta    the delta (usually 0.5)
     *                 增量（通常为 0.5）
     */
    public FisherIndicator(Indicator<Num> price, int barCount, double alpha, double beta, double gamma, double delta) {
        this(price, barCount, alpha, beta, gamma, delta, 1, true);
    }

    /**
     * Constructor (with alpha 0.33, beta 0.67, gamma 0.5, delta 0.5).
     * 构造函数（alpha 0.33，beta 0.67，gamma 0.5，delta 0.5）。
     *
     * @param ref              the indicator
     *                         指标
     * @param barCount         the time frame (usually 10)
     *                         时间范围（通常为 10）
     * @param isPriceIndicator use true, if "ref" is a price indicator
     *                         如果 "ref" 是价格指标，则使用 true
     */
    public FisherIndicator(Indicator<Num> ref, int barCount, boolean isPriceIndicator) {
        this(ref, barCount, 0.33, 0.67, ZERO_DOT_FIVE, ZERO_DOT_FIVE, 1, isPriceIndicator);
    }

    /**
     * Constructor (with alpha 0.33, beta 0.67, gamma 0.5, delta 0.5).
     * 构造函数（alpha 0.33，beta 0.67，gamma 0.5，delta 0.5）。
     *
     * @param ref              the indicator
     *                         指标
     * @param barCount         the time frame (usually 10)
     *                         时间范围（通常为 10）
     * @param densityFactor    the density factor (usually 1.0)
     *                         密度因子（通常为 1.0）
     * @param isPriceIndicator use true, if "ref" is a price indicator
     *                         如果 "ref" 是价格指标，则使用 true
     */
    public FisherIndicator(Indicator<Num> ref, int barCount, double densityFactor, boolean isPriceIndicator) {
        this(ref, barCount, 0.33, 0.67, ZERO_DOT_FIVE, ZERO_DOT_FIVE, densityFactor, isPriceIndicator);
    }

    /**
     * Constructor
     *
     * @param ref              the indicator
     *                         指标
     * @param barCount         the time frame (usually 10)
     *                         时间范围（通常为 10）
     * @param alphaD           the alpha (usually 0.33 or 0.5)
     *                         alpha（通常为 0.33 或 0.5）
     * @param betaD            the beta (usually 0.67 or 0.5)
     *                         贝塔（通常为 0.67 或 0.5）
     * @param gammaD           the gamma (usually 0.25 or 0.5)
     *                         伽玛（通常为 0.25 或 0.5）
     * @param deltaD           the delta (usually 0.5)
     *                         增量（通常为 0.5）
     * @param densityFactorD   the density factor (usually 1.0)
     *                         密度因子（通常为 1.0）
     * @param isPriceIndicator use true, if "ref" is a price indicator
     *                         如果 "ref" 是价格指标，则使用 true
     */
    public FisherIndicator(Indicator<Num> ref, int barCount, final double alphaD, final double betaD,
            final double gammaD, final double deltaD, double densityFactorD, boolean isPriceIndicator) {
        super(ref);
        this.ref = ref;
        this.gamma = numOf(gammaD);
        this.delta = numOf(deltaD);
        this.densityFactor = numOf(densityFactorD);

        Num alpha = numOf(alphaD);
        Num beta = numOf(betaD);
        final Indicator<Num> periodHigh = new HighestValueIndicator(
                isPriceIndicator ? new HighPriceIndicator(ref.getBarSeries()) : ref, barCount);
        final Indicator<Num> periodLow = new LowestValueIndicator(
                isPriceIndicator ? new LowPriceIndicator(ref.getBarSeries()) : ref, barCount);

        intermediateValue = new RecursiveCachedIndicator<Num>(ref) {

            @Override
            protected Num calculate(int index) {
                if (index <= 0) {
                    return numOf(0);
                }

                // Value = (alpha * 2 * ((ref - MinL) / (MaxH - MinL) - 0.5) + beta * priorValue) / densityFactor
                // 值 = (alpha * 2 * ((ref - MinL) / (MaxH - MinL) - 0.5) + beta * priorValue) / densityFactor
                Num currentRef = FisherIndicator.this.ref.getValue(index);
                Num minL = periodLow.getValue(index);
                Num maxH = periodHigh.getValue(index);
                Num term1 = currentRef.minus(minL).dividedBy(maxH.minus(minL)).minus(numOf(ZERO_DOT_FIVE));
                Num term2 = alpha.multipliedBy(numOf(2)).multipliedBy(term1);
                Num term3 = term2.plus(beta.multipliedBy(getValue(index - 1)));
                return term3.dividedBy(FisherIndicator.this.densityFactor);
            }
        };
    }

    @Override
    protected Num calculate(int index) {
        if (index <= 0) {
            return numOf(0);
        }

        Num value = intermediateValue.getValue(index);

        if (value.isGreaterThan(numOf(VALUE_MAX))) {
            value = numOf(VALUE_MAX);
        } else if (value.isLessThan(numOf(VALUE_MIN))) {
            value = numOf(VALUE_MIN);
        }

        // Fisher = gamma * Log((1 + Value) / (1 - Value)) + delta * priorFisher
        // Fisher = gamma * Log((1 + Value) / (1 - Value)) + delta * priorFisher
        Num term1 = numOf((Math.log(numOf(1).plus(value).dividedBy(numOf(1).minus(value)).doubleValue())));
        Num term2 = getValue(index - 1);
        return gamma.multipliedBy(term1).plus(delta.multipliedBy(term2));
    }

}
