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
package ta4jexamples.strategies;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.StochasticOscillatorKIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import ta4jexamples.loaders.CsvTradesLoader;

/**
 * Moving momentum strategy.
 * 动量策略。
 *
 * @see <a href=
 *      "http://stockcharts.com/help/doku.php?id=chart_school:trading_strategies:moving_momentum">
 *      http://stockcharts.com/help/doku.php?id=chart_school:trading_strategies:moving_momentum</a>
 */
public class MovingMomentumStrategy {

    /**
     * @param series the bar series
     *               酒吧系列
     * @return the moving momentum strategy
     * @return 动量策略
     */
    public static Strategy buildStrategy(BarSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null 系列不能为空");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        // The bias is bullish when the shorter-moving average moves above the longer moving average.
        // 当较短的移动平均线高于较长的移动平均线时，偏向是看涨的。
        // The bias is bearish when the shorter-moving average moves below the longer moving average.
        // 当较短的移动平均线低于较长的移动平均线时，偏向是看跌的。
        EMAIndicator shortEma = new EMAIndicator(closePrice, 9);
        EMAIndicator longEma = new EMAIndicator(closePrice, 26);

        StochasticOscillatorKIndicator stochasticOscillK = new StochasticOscillatorKIndicator(series, 14);

        MACDIndicator macd = new MACDIndicator(closePrice, 9, 26);
        EMAIndicator emaMacd = new EMAIndicator(macd, 18);

        // Entry rule
        //进入规则
        Rule entryRule = new OverIndicatorRule(shortEma, longEma) // Trend // 趋势
                .and(new CrossedDownIndicatorRule(stochasticOscillK, 20)) // Signal 1 // 信号 1
                .and(new OverIndicatorRule(macd, emaMacd)); // Signal 2

        // Exit rule
        // 退出规则
        Rule exitRule = new UnderIndicatorRule(shortEma, longEma) // Trend / 趋势
                .and(new CrossedUpIndicatorRule(stochasticOscillK, 80)) // Signal 1 // 信号 1
                .and(new UnderIndicatorRule(macd, emaMacd)); // Signal 2 // 信号 2

        return new BaseStrategy(entryRule, exitRule);
    }

    public static void main(String[] args) {

        // Getting the bar series
        // 获取柱状系列
        BarSeries series = CsvTradesLoader.loadBitstampSeries();

        // Building the trading strategy
        // 构建交易策略
        Strategy strategy = buildStrategy(series);

        // Running the strategy
        // 运行策略
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);
        System.out.println("Number of positions for the strategy 策略的职位数: " + tradingRecord.getPositionCount());

        // Analysis
        // 分析
        System.out.println(
                "Total profit for the strategy 策略的总利润: " + new GrossReturnCriterion().calculate(series, tradingRecord));
    }
}
