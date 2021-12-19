package ta4jexamples.indicators.numeric;

import java.util.List;
import java.util.stream.Collectors;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.AroonDownIndicator;
import org.ta4j.core.indicators.AroonOscillatorIndicator;
import org.ta4j.core.indicators.AroonUpIndicator;
import org.ta4j.core.indicators.numeric.facades.Aroon;
import org.ta4j.core.num.Num;

import ta4jexamples.loaders.CsvTradesLoader;

/**
 * A simple demo of the Aroon facade.
 *
 */
public class AroonDemo {

	public static void main(String[] args) {

        BarSeries bs = CsvTradesLoader.loadBitstampSeries();

        // the Aroon indicators have "pretty" toString() now
		System.out.println(new AroonUpIndicator(bs, 25));
		System.out.println(new AroonDownIndicator(bs, 25));
		System.out.println(new AroonOscillatorIndicator(bs, 25));
		System.out.println();
		
		//the Aroon facade makes it a little easier to create and use the indicators
		Aroon aroon = new Aroon(bs, 25);
		Rule consolidation = aroon.up().isLessThan(20)
							.and(aroon.down().isLessThan(20));
		
		Rule anotherRule = aroon.oscillator().crossedOver(0);
		// same as aroon.up().crossedOver(aroon.down())
		
		// The objects accessed through the facade 
		// are not quite the same as the ones we printed above,
		// although they calculate exactly the same value
		// 
		// You can't directly create rules using "raw" Indicator<Num>
		// so the facade wraps them as NumericIndicator objects
		//
		// The facade's oscillator doesn't pretty print correctly yet
		// it's really an instance of BinaryOperation
		// it could be made to pretty print itself as 
		//     AroonUp(25) - AroonDown(25)
		
		System.out.println(aroon.up());
		System.out.println(aroon.down());
		System.out.println(aroon.oscillator());		
		System.out.println();
		
		
		// a few values...  
		List<Indicator<Num>> indicators = List.of(aroon.up(), aroon.down());
		for ( int i = 0; i < 10; i++ ) {
			for ( Indicator<Num> ind : indicators ) {
				System.out.print(" " + ind + "=" + ind.getValue(i));
			}
			System.out.println();
		}
		
	}

}
