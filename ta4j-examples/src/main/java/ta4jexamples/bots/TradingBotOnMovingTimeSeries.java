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
import eu.verdelhan.ta4j.TADecimal;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import org.joda.time.DateTime;
import ta4jexamples.loaders.CsvTradesLoader;
import ta4jexamples.strategies.CCICorrectionStrategy;

/**
 * Dummy trading bot.
 * <p>
 */
public class TradingBotOnMovingTimeSeries {

    private static TADecimal LAST_TICK_CLOSE_PRICE;

    private static TimeSeries buildMovingTimeSeries() {
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();
        System.out.println("Initial tick count: " + series.getTickCount());
        // Limitating the number of ticks to 250
        series.setMaximumTickCount(250);
        System.out.println("Limited to 250");
        LAST_TICK_CLOSE_PRICE = series.getTick(series.getEnd()).getClosePrice();
        return series;
    }

    private static TADecimal randDecimal(TADecimal min, TADecimal max) {
        TADecimal randomDecimal = null;
        if (min != null && max != null && min.isLessThan(max)) {
            randomDecimal = max.minus(min).multipliedBy(TADecimal.valueOf(Math.random())).plus(min);
        }
        return randomDecimal;
    }

    /**
     * 
     */
    private static Tick generateRandomTick() {
        final TADecimal maxRange = TADecimal.valueOf("0.03"); // 3.0%
        TADecimal openPrice = LAST_TICK_CLOSE_PRICE;
        TADecimal minPrice = openPrice.minus(openPrice.multipliedBy(maxRange.multipliedBy(TADecimal.valueOf(Math.random()))));
        TADecimal maxPrice = openPrice.plus(openPrice.multipliedBy(maxRange.multipliedBy(TADecimal.valueOf(Math.random()))));
        TADecimal closePrice = randDecimal(minPrice, maxPrice);
        LAST_TICK_CLOSE_PRICE = closePrice;
        return new Tick(DateTime.now(), openPrice, maxPrice, minPrice, closePrice, TADecimal.ONE);
    }

    public static void main(String[] args) throws InterruptedException {
        // Getting the time series
        TimeSeries series = buildMovingTimeSeries();

        // Building the trading strategy
        Strategy strategy = CCICorrectionStrategy.buildStrategy(series);

        for (int i = 0; i < 300; i++) {
            System.out.println("Tick count: " + series.getTickCount());
            int currentIndex = series.getEnd() + i;
            if (strategy.shouldEnter(currentIndex)) {
                System.out.println("Strategy should enter on " + currentIndex);
            } else if (strategy.shouldExit(currentIndex)) {
                System.out.println("Strategy should exit on " + currentIndex);
            }
            
            // New tick
            Thread.sleep(10);
            Tick newTick = generateRandomTick();
            series.addTick(newTick);
        }
    }
}
