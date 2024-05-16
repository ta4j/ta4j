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
package org.ta4j.core.indicators.statistics;

import static org.ta4j.core.num.NaN.NaN;

import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

/**
 * Periodical Growth Rate indicator.
 * 周期性增长率指标。
 *
 * 周期增长率指标是用于衡量某一指标在一段时间内的增长情况的指标。它通常用于分析某项指标（如收入、销售额、利润等）在不同时间段内的增长率，以便评估增长的速度和趋势。
 *
 * 周期增长率可以根据具体的需求和时间段选择不同的计算方式，常见的计算方法包括简单增长率和复合增长率。
 *
 * 1. 简单增长率：简单增长率是指在两个时间点之间的增长比率，计算公式如下：
 *
 * 简单增长率 = (结束值 - 开始值) / 开始值 * 100%
 *
 * 2. 复合增长率：复合增长率是指在一段时间内的平均年增长率，计算公式如下：
 *
 *  复合增长率  =  (结束值 / 开始值)* 1 / n  - 1
 *
 * 其中，\( n \) 表示时间段的长度（通常以年为单位）。
 *
 * 周期增长率指标可以用于分析公司业绩、市场趋势以及经济增长等方面。通过比较不同时间段内的增长率，可以帮助分析者评估发展趋势，并做出相应的决策。
 *
 * In general the 'Growth Rate' is useful for comparing the average returns of investments in stocks or funds and can be used to compare the performance e.g. comparing the historical returns of stocks with bonds.
 * 一般来说，“增长率”对于比较股票或基金投资的平均回报非常有用，并且可以用来比较绩效，例如：比较股票和债券的历史回报。
 *
 *
 * This indicator has the following characteristics: - the calculation is
  timeframe dependendant. The timeframe corresponds to the number of trading
  events in a period, e. g. the timeframe for a US trading year for end of day
  bars would be '251' trading days - the result is a step function with a
  constant value within a timeframe - NaN values while index is smaller than
  timeframe, e.g. timeframe is year, than no values are calculated before a
  full year is reached - NaN values for incomplete timeframes, e.g. timeframe
  is a year and your timeseries contains data for 11,3 years, than no values
  are calculated for the remaining 0,3 years - the method 'getTotalReturn'
  calculates the total return over all returns of the coresponding timeframes

 该指标具有以下特征： - 计算取决于时间范围。时间范围对应于一段时间内的交易事件数量，例如。
    G。美国交易年收盘柱的时间范围为“251”个交易日 - 结果是一个时间范围内具有恒定值的阶跃函数 - NaN 值，而指数小于时间范围，例如时间范围是年，在达到全年之前不会计算任何值 - 不完整时间范围的 NaN 值，例如时间范围是一年，您的时间序列包含 11,
        3 年的数据，而不计算剩余 0,3 年的任何值 - 方法“getTotalReturn”计算相应时间范围的所有回报的总回报
 *
 *
 * Further readings: Good sumary on 'Rate of Return':
 * 进一步阅读：关于“回报率”的良好总结：
 * https://en.wikipedia.org/wiki/Rate_of_return Annual return / CAGR:
 * http://www.investopedia.com/terms/a/annual-return.asp Annualized Total
 * Return: http://www.investopedia.com/terms/a/annualized-total-return.asp
 * Annualized Return vs. Cumulative Return:
 * 年化回报与累积回报：
 * http://www.fool.com/knowledge-center/2015/11/03/annualized-return-vs-cumulative-return.aspx
 *
 */
public class PeriodicalGrowthRateIndicator extends CachedIndicator<Num> {

    private final Indicator<Num> indicator;
    private final int barCount;
    private final Num one;

    /**
     * Constructor. Example: use barCount = 251 and "end of day"-bars for annual behaviour in the US (http://tradingsim.com/blog/trading-days-in-a-year/).
     * * 构造函数。 示例：使用 barCount = 251 和“结束日”-bars 表示美国的年度行为 (http://tradingsim.com/blog/trading-days-in-a-year/)。
     * 
     * @param indicator the indicator 指標
     * @param barCount  the time frame 時間範圍
     */
    public PeriodicalGrowthRateIndicator(Indicator<Num> indicator, int barCount) {
        super(indicator);
        this.indicator = indicator;
        this.barCount = barCount;
        one = numOf(1);
    }

    /**
     * Gets the TotalReturn from the calculated results of the method 'calculate'.
     * 从方法 'calculate' 的计算结果中获取 TotalReturn。
     * For a barCount = number of trading days within a year (e. g. 251 days in the US) and "end of day"-bars you will get the 'Annualized Total Return'.
     * * 对于 barCount = 一年内的交易天数（例如美国 251 天）和“日终”-bar，您将获得“年化总回报”。
     * Only complete barCounts are taken into the calculation.
     * 仅将完整的 barCounts 纳入计算。
     * 
     * @return the total return from the calculated results of the method  'calculate'
     * @return 方法“计算”的计算结果的总回报
     */
    public Num getTotalReturn() {

        Num totalProduct = one;
        int completeTimeFrames = (getBarSeries().getBarCount() / barCount);

        for (int i = 1; i <= completeTimeFrames; i++) {
            int index = i * barCount;
            Num currentReturn = getValue(index);

            // Skip NaN at the end of a series
            // 在序列结束时跳过 NaN
            if (currentReturn != NaN) {
                currentReturn = currentReturn.plus(one);
                totalProduct = totalProduct.multipliedBy(currentReturn);
            }
        }

        return totalProduct.pow(one.dividedBy(numOf(completeTimeFrames)));
    }

    @Override
    protected Num calculate(int index) {

        Num currentValue = indicator.getValue(index);

        int helpPartialTimeframe = index % barCount;
        // TODO: implement Num.floor()
        Num helpFullTimeframes = numOf(
                Math.floor(numOf(indicator.getBarSeries().getBarCount()).dividedBy(numOf(barCount)).doubleValue()));
        Num helpIndexTimeframes = numOf(index).dividedBy(numOf(barCount));

        Num helpPartialTimeframeHeld = numOf(helpPartialTimeframe).dividedBy(numOf(barCount));
        Num partialTimeframeHeld = (helpPartialTimeframeHeld.isZero()) ? one : helpPartialTimeframeHeld;

        // Avoid calculations of returns:
        // 避免计算收益：
        // a.) if index number is below timeframe
        // a.) 如果索引号低于时间范围
        // e.g. timeframe = 365, index = 5 => no calculation
        // 例如 时间范围 = 365，指数 = 5 => 没有计算
        // b.) if at the end of a series incomplete timeframes would remain
        // b.) 如果在系列结束时仍会保留不完整的时间框架
        Num timeframedReturn = NaN;
        if ((index >= barCount) /* (a) */ && (helpIndexTimeframes.isLessThan(helpFullTimeframes)) /* (b) */) {
            Num movingValue = indicator.getValue(index - barCount);
            Num movingSimpleReturn = (currentValue.minus(movingValue)).dividedBy(movingValue);

            timeframedReturn = one.plus(movingSimpleReturn).pow(one.dividedBy(partialTimeframeHeld)).minus(one);
        }

        return timeframedReturn;

    }
}
