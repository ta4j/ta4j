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
package ta4jexamples.analysis;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.criteria.AverageReturnPerBarCriterion;
import org.ta4j.core.analysis.criteria.BuyAndHoldReturnCriterion;
import org.ta4j.core.analysis.criteria.LinearTransactionCostCriterion;
import org.ta4j.core.analysis.criteria.MaximumDrawdownCriterion;
import org.ta4j.core.analysis.criteria.NumberOfBarsCriterion;
import org.ta4j.core.analysis.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.analysis.criteria.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.analysis.criteria.VersusBuyAndHoldCriterion;
import org.ta4j.core.analysis.criteria.WinningPositionsRatioCriterion;
import org.ta4j.core.analysis.criteria.pnl.GrossReturnCriterion;

import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.MovingMomentumStrategy;

/**
 * This class diplays analysis criterion values after running a trading strategy over a bar series.
 * * 此类显示在条形系列上运行交易策略后的分析标准值。
 */
public class StrategyAnalysis {

    public static void main(String[] args) {

        // Getting the bar series
        // 获取柱状系列
        BarSeries series = CsvTradesLoader.loadBitstampSeries();
        // Building the trading strategy
        // 构建交易策略
        Strategy strategy = MovingMomentumStrategy.buildStrategy(series);
        // Running the strategy
        // 运行策略
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);

        /*
         * Analysis criteria
         * * 分析标准
         */

        // Total profit
        // 总利润
        GrossReturnCriterion totalReturn = new GrossReturnCriterion();
        System.out.println("Total return 总回报: " + totalReturn.calculate(series, tradingRecord));
        // Number of bars 条数
        System.out.println("Number of bars 条数: " + new NumberOfBarsCriterion().calculate(series, tradingRecord));
        // Average profit (per bar) 平均回报（每根柱）
        System.out.println(
                "Average return (per bar) 平均回报（每根柱）: " + new AverageReturnPerBarCriterion().calculate(series, tradingRecord));
        // Number of positions  持仓数
        System.out.println("Number of positions  持仓数: " + new NumberOfPositionsCriterion().calculate(series, tradingRecord));
        // Profitable position ratio 赢得倉位比
        System.out.println(
                "Winning positions ratio 赢得倉位比: " + new WinningPositionsRatioCriterion().calculate(series, tradingRecord));
        // Maximum drawdown 最大回撤
        System.out.println("Maximum drawdown 最大回撤: " + new MaximumDrawdownCriterion().calculate(series, tradingRecord));
        // Reward-risk ratio 回报风险比
        System.out.println("Return over maximum drawdown 返回超过最大回撤: "
                + new ReturnOverMaxDrawdownCriterion().calculate(series, tradingRecord));
        // Total transaction cost 总交易成本（1000 美元起）
        System.out.println("Total transaction cost (from $1000) 总交易成本（1000 美元起）: "
                + new LinearTransactionCostCriterion(1000, 0.005).calculate(series, tradingRecord));
        // Buy-and-hold 买入并持有
        System.out.println("Buy-and-hold return 买入并持有回报: " + new BuyAndHoldReturnCriterion().calculate(series, tradingRecord));
        // Total profit vs buy-and-hold 总利润与买入并持有
        System.out.println("Custom strategy return vs buy-and-hold strategy return 自定义策略回报与买入并持有策略回报: "
                + new VersusBuyAndHoldCriterion(totalReturn).calculate(series, tradingRecord));
    }
}
