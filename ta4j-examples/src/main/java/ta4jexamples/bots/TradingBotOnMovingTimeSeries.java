/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2015 Marc de Verdelhan & respective authors
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

import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Decimal;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import org.joda.time.DateTime;
import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.CCICorrectionStrategy;

/**
 * This class is an example of a dummy trading bot using ta4j.
 * <p>
 */
public class TradingBotOnMovingTimeSeries {

    /** Close price of the last tick */
    private static Decimal LAST_TICK_CLOSE_PRICE;

    /**
     * Builds a moving time series (i.e. keeping only the maxTickCount last ticks)
     * @param maxTickCount the number of ticks to keep in the time series (at maximum)
     * @return a moving time series
     */
    private static TimeSeries buildMovingTimeSeries(int maxTickCount) {
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();
        System.out.println("Initial tick count: " + series.getTickCount());
        // Limitating the number of ticks to 250
        series.setMaximumTickCount(maxTickCount);
        System.out.println("Limited to " + maxTickCount);
        LAST_TICK_CLOSE_PRICE = series.getTick(series.getEnd()).getClosePrice();
        return series;
    }

    /**
     * Generates a random decimal number between min and max.
     * @param min the minimum bound
     * @param max the maximum bound
     * @return a random decimal number between min and max
     */
    private static Decimal randDecimal(Decimal min, Decimal max) {
        Decimal randomDecimal = null;
        if (min != null && max != null && min.isLessThan(max)) {
            randomDecimal = max.minus(min).multipliedBy(Decimal.valueOf(Math.random())).plus(min);
        }
        return randomDecimal;
    }

    /**
     * Generates a random tick.
     * @return a random tick
     */
    private static Tick generateRandomTick() {
        final Decimal maxRange = Decimal.valueOf("0.03"); // 3.0%
        Decimal openPrice = LAST_TICK_CLOSE_PRICE;
        Decimal minPrice = openPrice.minus(openPrice.multipliedBy(maxRange.multipliedBy(Decimal.valueOf(Math.random()))));
        Decimal maxPrice = openPrice.plus(openPrice.multipliedBy(maxRange.multipliedBy(Decimal.valueOf(Math.random()))));
        Decimal closePrice = randDecimal(minPrice, maxPrice);
        LAST_TICK_CLOSE_PRICE = closePrice;
        return new Tick(DateTime.now(), openPrice, maxPrice, minPrice, closePrice, Decimal.ONE);
    }

    public static void main(String[] args) throws InterruptedException {
        /**
         * Getting the time series
         */
        TimeSeries series = buildMovingTimeSeries(250);

        /**
         * Building the trading strategy
         */
        Strategy strategy = CCICorrectionStrategy.buildStrategy(series);

        /**
         * We run the strategy for the 300 next ticks.
         */
        for (int i = 0; i < 300; i++) {
            System.out.println("Tick count: " + series.getTickCount());

            // Starting from the end of the series
            int currentIndex = series.getEnd() + i;
            if (strategy.shouldEnter(currentIndex)) {
                // Our strategy should enter
                System.out.println("Strategy should enter on " + currentIndex);
            } else if (strategy.shouldExit(currentIndex)) {
                // Our strategy should exit
                System.out.println("Strategy should exit on " + currentIndex);
            }
            
            // New tick
            Thread.sleep(20);
            Tick newTick = generateRandomTick();
            series.addTick(newTick);
        }
    }
}
