package ta4jexamples.indicators.math;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.num.Num;

import ta4jexamples.loaders.CsvTradesLoader;

public class Demo {

	public static void main(String[] args) {

		// I don't know what's in this bar series, but my math should work
        BarSeries series = CsvTradesLoader.loadBitstampSeries();
        Num two = series.numOf(2);

        // need to wrap Indicator<T>  
		NumericIndicator high = new BasicNumericIndicator(new HighPriceIndicator(series));
		NumericIndicator low = new BasicNumericIndicator(new LowPriceIndicator(series));
		
		// create operation indicator to do a simple calculation (median price), if/when we are ready
		// myMedian will be an instance of Quotient
		// the internal Sum object will never be seen
		NumericIndicator myMedian = high.plus(low).dividedBy(two);
		
		System.out.println(myMedian.getValue(12));
		
		//compare
		MedianPriceIndicator medianIndicator = new MedianPriceIndicator(series);
		System.out.println(medianIndicator.getValue(12));
		
		// my result           : 806.995
		// MedianPriceIndicator: 806.995

		//let's create a MACD
		ClosePriceIndicator close = new ClosePriceIndicator(series);
		NumericIndicator ema12 = new BasicNumericIndicator(new EMAIndicator(close, 12));
		NumericIndicator ema26 = new BasicNumericIndicator(new EMAIndicator(close, 26));
		NumericIndicator myMacd = ema12.minus(ema26);
		
		System.out.println(myMacd.getValue(12));
		
		//compare
		
		MACDIndicator macxdIndicator = new MACDIndicator(close, 12, 26);
		System.out.println(macxdIndicator.getValue(12));
		
		// my result    :-0.05179428889075265430983599698
		//MACD indicator:-0.05179428889075265430983599698
		
		// I can create lots of things this way.  
		// Awesome, Accel/Decel, many oscillators are all S1 - S2
		// BBs, Keltner are just S1 + (s2 * k)
		
		// there are a number of simple math functions max(), min(), sqrt(), etc
		// I haven't added as convenience methods yet
		
		// I think ema12.minus(ema26).getValue(index) is a lot nicer to read
		// compare to: emaShort.gertValue(index).minus(emaLong.getValue(index))
		
	}

}
