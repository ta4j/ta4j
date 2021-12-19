package ta4jexamples.indicators.numeric;

import java.util.function.IntToDoubleFunction;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.CMOIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.num.Num;

import ta4jexamples.loaders.CsvTradesLoader;

/**
 * This is a series of statements to illustrate ways in which the NumericIndicator can be used.
 * It is meant to be quickly read.  
 * Running this will simply print a series of numbers produced by the last example.
 *
 * I will remove some of this after review.
 */
public class NumericIndicatorDemo {

	public static void main(String[] args) {

		// as usual, we start with a bar series
        BarSeries bs = CsvTradesLoader.loadBitstampSeries();
        
        // and a simple indicator
        Indicator<Num> close = new ClosePriceIndicator(bs);
        
        // now we want to create a simple rule
        // we wrap the indicator so we can use fluent methods
     
        NumericIndicator fluentClose = NumericIndicator.of(close);
        Rule lowClosePrice = fluentClose.isLessThan(75.0);  
        
        // we need a couple of SMA indicators, maybe
        NumericIndicator sma1 = fluentClose.sma(50);
        NumericIndicator sma2 = fluentClose.sma(200);
        
        // do these guys cross?
        Rule smaCross = sma1.crossedOver(sma2);
        
        // a few more indicators; wrapped and ready this time
        
        NumericIndicator volume = NumericIndicator.of(new VolumeIndicator(bs));
        NumericIndicator smaVolume = volume.sma(125);
        
        // is volume more than 50% over average?
        Rule highVolume = volume.isGreaterThan(smaVolume.multipliedBy(1.5));
        
        // an "ad hoc" indicator ta4j does not have
        NumericIndicator stddev = NumericIndicator.of(new StandardDeviationIndicator(close, 20));
        NumericIndicator volatility = stddev.dividedBy(fluentClose.sma(20));
        
        // I have never actually calculated this one; let's do it now
        for ( int i = 0; i < 50; i++ ) {
        	System.out.println(volatility.getValue(i));
        }
        System.out.println();
        
        // needs scaling, all the values are like 0.00312...
        volatility = volatility.multipliedBy(100);
        
        for ( int i = 0; i < 50; i++ ) {
        	System.out.println(volatility.getValue(i));
        }
        System.out.println();
        // much better; values like 0.312...
        
        // almost any Num operation can be applied to a NumericIndicator
        // I'm sure this is a useless calculation, but it shows what can be done
        
        NumericIndicator silly = volatility.ema(12).sqrt()
        		.multipliedBy(new RSIIndicator(close, 12))
        		.max(new CMOIndicator(close, 18)).squared().minus(17).dividedBy(100);   
        // times the price of tea in London.. hehe

        for ( int i = 0; i < 50; i++ ) {
        	System.out.println(silly.getValue(i));
        }
        System.out.println();
        // hmmm.. values range nicely from 0 to 20 or so...
        // I am sure they are meaningless
        // but it works.. about as fast as the StandardDeviationIndicator, I would say
        
        // I think these are the right Donchian Channel formulas.. same as Aroon effectively
        NumericIndicator donchianUpper = NumericIndicator.of(
        		new HighestValueIndicator(new HighPriceIndicator(bs), 20));
        NumericIndicator donchianLower = NumericIndicator.of(
        		new LowestValueIndicator(new LowPriceIndicator(bs), 20));
        
        IntToDoubleFunction iToD = NumericIndicator.volume(bs);

        Widget widget = new Widget(iToD);
        widget.update(25);
        
	}

	static class Widget {
		private final IntToDoubleFunction function;

		public Widget(IntToDoubleFunction function) {
			super();
			this.function = function;
		}
		void update(int index) {
			System.out.println("setting widget value 2: " + function.applyAsDouble(index));
		}
		
	}
}
