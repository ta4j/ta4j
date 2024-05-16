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
package org.ta4j.core.indicators.pivotpoints;

import static org.ta4j.core.num.NaN.NaN;

import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.RecursiveCachedIndicator;
import org.ta4j.core.num.Num;

/**
 * DeMark Pivot Point indicator.
 * DeMark 枢轴点指标。
 *
 * DeMark Pivot Point指标是一种用于技术分析的指标，它用于确定资产价格可能发生反转的水平。这个指标是由技术分析家汤姆·德马克（Tom DeMark）开发的。
 *
 * DeMark Pivot Point指标通常包括四个水平：Pivot Point（支撑/阻力的中心）、R1（首个支撑/阻力）、S1（首个支撑/阻力）、R2（第二个支撑/阻力）、S2（第二个支撑/阻力）。这些水平可以帮助交易者识别资产价格可能反转的关键水平。
 *
 * 计算DeMark Pivot Point指标的方法因不同的版本而异，但通常使用前一个周期的高、低和收盘价来计算。下面是一种常见的计算方法：
 *
 * 1. Pivot Point（PP）=（前一个周期的高 + 前一个周期的低 + 前一个周期的收盘价）/ 3
 * 2. R1 =（2 * PP）- 前一个周期的最低价
 * 3. S1 =（2 * PP）- 前一个周期的最高价
 * 4. R2 = PP +（前一个周期的高 - 前一个周期的低）
 * 5. S2 = PP -（前一个周期的高 - 前一个周期的低）
 *
 * DeMark Pivot Point指标可以与其他技术指标结合使用，例如移动平均线、相对强弱指标等，以提供更全面的市场分析和交易信号。它通常用于短期交易和日内交易，以帮助确定进出场时机和设定止损位点。
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points">
 *      http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:pivot_points</a>
 */
public class DeMarkPivotPointIndicator extends RecursiveCachedIndicator<Num> {

    private final TimeLevel timeLevel;
    private final Num two;

    /**
     * Constructor.
     *
     * Calculates the deMark pivot point based on the time level parameter.
     * 根据时间级别参数计算 deMark 枢轴点。
     *
     * @param series      the bar series with adequate endTime of each bar for the  given time level.
     *                    对于给定时间级别，每个柱具有足够的 endTime 的柱系列。
     * @param timeLevelId the corresponding time level for pivot calculation:
     *                    枢轴计算的相应时间级别：
     *                    <ul>
                         <li>1-, 5-, 10- and 15-minute charts use the prior days
                        high, low and close: <b>timeLevelId</b> =
                           PIVOT_TIME_LEVEL_ID_DAY (= 1)</li>
                           <li>30- 60- and 120-minute charts use the prior week's
                         high, low, and close: <b>timeLevelId</b> =
                          PIVOT_TIME_LEVEL_ID_WEEK (= 2)</li>
                         <li>Pivot Points for daily charts use the prior month's
                         high, low and close: <b>timeLevelId</b> =
                         PIVOT_TIME_LEVEL_ID_MONTH (= 3)</li>
                         <li>Pivot Points for weekly and monthly charts use the
                         prior year's high, low and close: <b>timeLevelId</b> =
                         PIVOT_TIME_LEVEL_ID_YEAR (= 4)</li>
                         <li>If you want to use just the last bar data:
                         <b>timeLevelId</b> = PIVOT_TIME_LEVEL_ID_BARBASED (=
                         0)</li>
                         </ul>
                        <ul>
                        <li>1、5、10 和 15 分钟图表使用前几天
                        最高价、最低价和收盘价：<b>timeLevelId</b> =
                        PIVOT_TIME_LEVEL_ID_DAY (= 1)</li>
                        <li>30- 60 分钟和 120 分钟图表使用前一周的图表
                        最高价、最低价和收盘价：<b>timeLevelId</b> =
                        PIVOT_TIME_LEVEL_ID_WEEK (= 2)</li>
                        <li>日线图的枢轴点使用上个月的
                        最高价、最低价和收盘价：<b>timeLevelId</b> =
                        PIVOT_TIME_LEVEL_ID_MONTH (= 3)</li>
                        <li>每周和每月图表的枢轴点使用
                        上一年的最高价、最低价和收盘价：<b>timeLevelId</b> =
                        PIVOT_TIME_LEVEL_ID_YEAR (= 4)</li>
                        <li>如果您只想使用最后一根柱线数据：
                        <b>timeLevelId</b> = PIVOT_TIME_LEVEL_ID_BARBASED (=
                        0)</li>
                        </ul>

     *                    The user has to make sure that there are enough previous
                         bars to calculate correct pivots at the first bar that
                         matters. For example for PIVOT_TIME_LEVEL_ID_MONTH there
                         will be only correct pivot point values (and reversals)
                         after the first complete month
                    用户必须确保有足够的先前
                    条来计算第一个条的正确枢轴
                    很重要。 例如对于 PIVOT_TIME_LEVEL_ID_MONTH 那里
                    将仅是正确的枢轴点值（和反转）
                    第一个完整月后
     */
    public DeMarkPivotPointIndicator(BarSeries series, TimeLevel timeLevelId) {
        super(series);
        this.timeLevel = timeLevelId;
        this.two = numOf(2);
    }

    @Override
    protected Num calculate(int index) {
        return calcPivotPoint(getBarsOfPreviousPeriod(index));
    }

    private Num calcPivotPoint(List<Integer> barsOfPreviousPeriod) {
        if (barsOfPreviousPeriod.isEmpty())
            return NaN;
        Bar bar = getBarSeries().getBar(barsOfPreviousPeriod.get(0));
        Num open = getBarSeries().getBar(barsOfPreviousPeriod.get(barsOfPreviousPeriod.size() - 1)).getOpenPrice();
        Num close = bar.getClosePrice();
        Num high = bar.getHighPrice();
        Num low = bar.getLowPrice();

        for (int i : barsOfPreviousPeriod) {
            high = (getBarSeries().getBar(i).getHighPrice()).max(high);
            low = (getBarSeries().getBar(i).getLowPrice()).min(low);
        }

        Num x;
        if (close.isLessThan(open)) {
            x = high.plus(two.multipliedBy(low)).plus(close);
        } else if (close.isGreaterThan(open)) {
            x = two.multipliedBy(high).plus(low).plus(close);
        } else {
            x = high.plus(low).plus(two.multipliedBy(close));
        }

        return x.dividedBy(numOf(4));
    }

    /**
     * Calculates the indices of the bars of the previous period
     * * 计算上一周期柱线的指数
     *
     * @param index index of the current bar
     *              当前柱的索引
     * @return list of indices of the bars of the previous period
     * @return 上一周期柱的索引列表
     */
    public List<Integer> getBarsOfPreviousPeriod(int index) {
        List<Integer> previousBars = new ArrayList<>();

        if (timeLevel == TimeLevel.BARBASED) {
            previousBars.add(Math.max(0, index - 1));
            return previousBars;
        }
        if (index == 0) {
            return previousBars;
        }

        final Bar currentBar = getBarSeries().getBar(index);

        // step back while bar-1 in same period (day, week, etc):
        // 当 bar-1 在同一时期（日、周等）时后退：
        while (index - 1 > getBarSeries().getBeginIndex()
                && getPeriod(getBarSeries().getBar(index - 1)) == getPeriod(currentBar)) {
            index--;
        }

        // index = last bar in same period, index-1 = first bar in previous period
        // index = 同一时期的最后一根柱线，index-1 = 上一时期的第一根柱线
        long previousPeriod = getPreviousPeriod(currentBar, index - 1);
        while (index - 1 > getBarSeries().getBeginIndex()
                && getPeriod(getBarSeries().getBar(index - 1)) == previousPeriod) { // while bar-n in previous period // while bar-n 在上一周期
            index--;
            previousBars.add(index);
        }
        return previousBars;
    }

    private long getPreviousPeriod(Bar bar, int indexOfPreviousBar) {
        switch (timeLevel) {
        case DAY: // return previous day // 返回前一天
            int prevCalendarDay = bar.getEndTime().minusDays(1).getDayOfYear();
            // skip weekend and holidays:
            // 跳过周末和节假日：
            while (getBarSeries().getBar(indexOfPreviousBar).getEndTime().getDayOfYear() != prevCalendarDay
                    && indexOfPreviousBar > 0) {
                prevCalendarDay--;
            }
            return prevCalendarDay;
        case WEEK: // return previous week // 返回前一周
            return bar.getEndTime().minusWeeks(1).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        case MONTH: // return previous month // 返回上个月
            return bar.getEndTime().minusMonths(1).getMonthValue();
        default: // return previous year // 返回上一年
            return bar.getEndTime().minusYears(1).getYear();
        }
    }

    private long getPeriod(Bar bar) {
        switch (timeLevel) {
        case DAY: // return previous day // 返回前一天
            return bar.getEndTime().getDayOfYear();
        case WEEK: // return previous week // 返回前一周
            return bar.getEndTime().get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        case MONTH: // return previous month // 返回上个月
            return bar.getEndTime().getMonthValue();
        default: // return previous year  // 返回上一年
            return bar.getEndTime().getYear();
        }
    }

}
