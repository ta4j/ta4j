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
package ta4jexamples.indicators.numeric;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.StopGainRule;
import org.ta4j.core.rules.StopLossRule;

import ta4jexamples.loaders.CsvTradesLoader;

public class Demo {

    public static void main(String[] args) {

        BarSeries bs = CsvTradesLoader.loadBitstampSeries();

        // Calculate median price; same as MedianPriceIndicator without caching
        // NumericIndicator methods like plus, minus, muliplyBy and divideBy create
        // simple function-like objects
        // using these methods simply defers the real calculation until later
        // A ConstantIndicator is created for the "2" used below; another function-like
        // object
        // Note how we avoid the temptation to abuse numOf(), like the
        // MedianPriceIndicator (probably) does

        Indicator<Num> high = new HighPriceIndicator(bs);
        Indicator<Num> low = new LowPriceIndicator(bs);
        Indicator<Num> medianPrice = NumericIndicator.of(high).plus(low).dividedBy(2);

        // for comparison
        Indicator<Num> medianPriceIndicator = new MedianPriceIndicator(bs);

        for (int i = 0; i < bs.getBarCount(); i++) {
            System.out.print(" high " + high.getValue(i));
            System.out.print(" low " + low.getValue(i));
            System.out.print(" median " + medianPrice.getValue(i));
            System.out.print(" medianPriceIndicator " + medianPriceIndicator.getValue(i));
            System.out.println();
        }

        // calculate MACD, signal and histogram; print statements omitted for brevity

        NumericIndicator close = NumericIndicator.of(new ClosePriceIndicator(bs));
        NumericIndicator macd = close.ema(12).minus(close.ema(26));
        NumericIndicator macdSignal = macd.ema(9);
        NumericIndicator macdHistogram = macd.minus(macdSignal);

        // Bollinger bands...
        // NumericIndicator methods like sma, ema, and stddev create cached ta4j
        // indicators;
        // we should reuse these objects
        // See the facades demo, where this is all wrapped up in a facade

        NumericIndicator stddev = close.stddev(20);
        NumericIndicator bbMiddle = close.sma(20);
        NumericIndicator bbUpper = bbMiddle.plus(stddev.multipliedBy(2));
        NumericIndicator bbLower = bbMiddle.minus(stddev.multipliedBy(2));
        NumericIndicator bbWidth = bbUpper.minus(bbLower).dividedBy(bbMiddle).multipliedBy(100);
        NumericIndicator percentB = close.minus(bbLower).dividedBy(bbUpper.minus(bbLower));

        // Keltner channels... adx... a large percentage of indicators can be done this
        // way
        // the code is clearer and there is much less caching
        // If there is any interest, I will show how we can selectively cache these
        // numeric indicator objects;
        // kinda like this: something.plus(somethingElse).sqrt().minus(....).cached()

        // Rules... NumericIndicator has a few methods to make this more fluent...
        // this code was inspired by the Quickstart example
        // the facade demos show

        NumericIndicator shortSma = close.sma(5);
        NumericIndicator longSma = close.sma(30);

        Rule buy = shortSma.crossedOver(longSma).or(close.crossedUnder(800));

        // stop loss and stop gain need a ClosePriceIndicator, it seems; I haven't
        // really looked at these much
        ClosePriceIndicator cp = new ClosePriceIndicator(bs);
        Rule stopLoss = new StopLossRule(cp, bs.numOf(3));
        Rule stopGain = new StopGainRule(cp, bs.numOf(2));

        Rule sell = shortSma.crossedUnder(longSma).or(stopLoss).or(stopGain);

        // etc...

        // caching - experimental; good concept but not quite there for all use cases

        Indicator<Num> median2 = NumericIndicator.of(high).plus(low).dividedBy(2).cached();
        for (int i = 0; i < bs.getBarCount(); i++) {
            System.out.print(" high " + high.getValue(i));
            System.out.print(" low " + low.getValue(i));
            System.out.print(" median " + medianPrice.getValue(i));
            System.out.print(" median2 " + median2.getValue(i));
            System.out.println();
        }
//    	
//    	// try the cached indicator again in reverse order.. broken
//    	for ( int i = bs.getEndIndex(); i >= bs.getBeginIndex(); i-- ) {
//        	System.out.println(median2.getValue(i));    		
//    	}
//

    }

}
