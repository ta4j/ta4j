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
package ta4jexamples.bots;

import java.time.Duration;
import java.time.ZonedDateTime;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

import ta4jexamples.loaders.CsvTradesLoader;

/**
 * This class is an example of a dummy trading bot using ta4j.
 * * 此类是使用 ta4j 的虚拟交易机器人的示例。
 * <p/>
 */
public class TradingBotOnMovingBarSeries {

    /**
     * Close price of the last bar
     * * 最后一根柱的收盘价
     */
    private static Num LAST_BAR_CLOSE_PRICE;

    /**
     * Builds a moving bar series (i.e. keeping only the maxBarCount last bars)
     * * 构建一个移动柱系列（即只保留 maxBarCount 最后一根柱）
     *
     * @param maxBarCount the number of bars to keep in the bar series (at maximum)
     *                    要保留在条形系列中的条数（最大）
     * @return a moving bar series
     * * @return 一个移动的条形系列
     */
    private static BarSeries initMovingBarSeries(int maxBarCount) {
        BarSeries series = CsvTradesLoader.loadBitstampSeries();
        System.out.print("Initial bar count 初始条数: " + series.getBarCount());
        // Limitating the number of bars to maxBarCount
        series.setMaximumBarCount(maxBarCount);
        LAST_BAR_CLOSE_PRICE = series.getBar(series.getEndIndex()).getClosePrice();
        System.out.println(" (limited to 仅限于 " + maxBarCount + "), close price  收盘价= " + LAST_BAR_CLOSE_PRICE);
        return series;
    }

    /**
     * @param series a bar series 酒吧系列
     * @return a dummy strategy @return 一个虚拟策略
     */
    private static Strategy buildStrategy(BarSeries series) {
        if (series == null) {
            throw new IllegalArgumentException("Series cannot be null 系列不能为空");
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator sma = new SMAIndicator(closePrice, 12);

        // Signals
        // 信号
        // Buy when SMA goes over close price
        // 当 SMA 超过收盘价时买入
        // Sell when close price goes over SMA
        // 当收盘价超过 SMA 时卖出
        Strategy buySellSignals = new BaseStrategy(new OverIndicatorRule(sma, closePrice),
                new UnderIndicatorRule(sma, closePrice));
        return buySellSignals;
    }

    /**
     * Generates a random decimal number between min and max.
     * * 生成一个介于 min 和 max 之间的随机十进制数。
     *
     * @param min the minimum bound
     *            最小界限
     * @param max the maximum bound
     *            最大界限
     * @return a random decimal number between min and max
     * * @return 一个介于 min 和 max 之间的随机十进制数
     */
    private static Num randDecimal(Num min, Num max) {
        Num randomDecimal = null;
        if (min != null && max != null && min.isLessThan(max)) {
            Num range = max.minus(min);
            Num position = range.multipliedBy(DecimalNum.valueOf(Math.random()));
            randomDecimal = min.plus(position);
        }
        return randomDecimal;
    }

    /**
     * Generates a random bar.
     * * 生成一个随机条。
     *
     * @return a random bar
     * * @return 随机条
     */
    private static Bar generateRandomBar() {
        final Num maxRange = DecimalNum.valueOf("0.03"); // 3.0%
        Num openPrice = LAST_BAR_CLOSE_PRICE;
        Num lowPrice = openPrice.minus(maxRange.multipliedBy(DecimalNum.valueOf(Math.random())));
        Num highPrice = openPrice.plus(maxRange.multipliedBy(DecimalNum.valueOf(Math.random())));
        Num closePrice = randDecimal(lowPrice, highPrice);
        LAST_BAR_CLOSE_PRICE = closePrice;
        return new BaseBar(Duration.ofDays(1), ZonedDateTime.now(), openPrice, highPrice, lowPrice, closePrice,
                DecimalNum.valueOf(1), DecimalNum.valueOf(1));
    }

    public static void main(String[] args) throws InterruptedException {

        System.out.println("********************** Initialization 初始化 **********************");
        // Getting the bar series
        // 获取柱状系列
        BarSeries series = initMovingBarSeries(20);

        // Building the trading strategy
        // 构建交易策略
        Strategy strategy = buildStrategy(series);

        // Initializing the trading history
        // 初始化交易历史
        TradingRecord tradingRecord = new BaseTradingRecord();
        System.out.println("************************************************************");

        /*
         * We run the strategy for the 50 next bars.
         * * 我们为接下来的 50 个柱运行策略。
         */
        for (int i = 0; i < 50; i++) {

            // New bar
            // 新栏
            Thread.sleep(30); // I know... // 我知道...
            Bar newBar = generateRandomBar();
            System.out.println("------------------------------------------------------\n" + "Bar " + i
                    + " added, close price 补充，收盘价 = " + newBar.getClosePrice().doubleValue());
            series.addBar(newBar);

            int endIndex = series.getEndIndex();
            if (strategy.shouldEnter(endIndex)) {
                // Our strategy should enter
                // 我们的策略应该进入
                System.out.println("Strategy should ENTER on 策略应进入 " + endIndex);
                boolean entered = tradingRecord.enter(endIndex, newBar.getClosePrice(), DecimalNum.valueOf(10));
                if (entered) {
                    Trade entry = tradingRecord.getLastEntry();
                    System.out.println("Entered on  输入时间" + entry.getIndex() + " (price= （价格=" + entry.getNetPrice().doubleValue()
                            + ", amount= 金额=" + entry.getAmount().doubleValue() + ")");
                }
            } else if (strategy.shouldExit(endIndex)) {
                // Our strategy should exit
                // 我们的策略应该退出
                System.out.println("Strategy should EXIT on  策略应退出" + endIndex);
                boolean exited = tradingRecord.exit(endIndex, newBar.getClosePrice(), DecimalNum.valueOf(10));
                if (exited) {
                    Trade exit = tradingRecord.getLastExit();
                    System.out.println("Exited on 退出时间" + exit.getIndex() + " (price= 价格=" + exit.getNetPrice().doubleValue()
                            + ", amount= 金额=" + exit.getAmount().doubleValue() + ")");
                }
            }
        }
    }
}
