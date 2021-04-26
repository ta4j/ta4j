package ta4jexamples.indicators.math;

import java.util.function.BinaryOperator;

import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class SeriesDemo {

	public static void main(String[] args) {

		// a trivial series; 0. 1. 2... 
		// if we start form 0; the series has no concept of beginning or end
		
		Series<Num> s1 = new Series<Num>() {
			public Num getValue(int index) {
				return numOf(index);
			}
		};
		
		// its very easy to create price and volume Series<Num> from a bar series
		// A simple list backed implementation works well
		// I will simply create another anonymous one
		// 10, 9, 8 if we start at 0
		
		Series<Num> s2 = new Series<Num>() {
			public Num getValue(int index) {
				return numOf(10 - index);
			}
		};
		
		// we can iterate over s1 and s2 and add (multiple, whatever) their elements
		// let's abstract that;  this odd-looking function defines addition over Series<Num>
		// Although BiFunctions seem to work better for andThen composition, which I won't show here
		// I will use a BinaryOperator for syntactic brevity
		
		BinaryOperator<Series<Num>> seriesAddition = new BinaryOperator<Series<Num>>() {
			public Series<Num> apply(Series<Num> s1, Series<Num> s2) {
				return new Series<Num>() {
					public Num getValue(int index) {
						return s1.getValue(index).plus(s2.getValue(index));
					}
				};
			}
		};
		
		Series<Num> sum = seriesAddition.apply(s1, s2);
		
		for ( int i = 0; i < 10; i++ ) {
			System.out.println(sum.getValue(i));  // 10, 10, 10...  poor choice of input series
		}
		
		// I will point out I have yet to use an actual class
		
		// With a few classes, we can use the "pointwise operator"  (Num::plus in this case)
		// as an instance variable; the numeric indicator examples 
		// show a somewhat awkward way of doing that
		
		// Apologies if I have been pedantic; I was a teacher a long time ago

	}
	
	private static Num numOf(Number n) {
		// Nums are a bit awkward to create so I hide that here
		// I will use DoubleNum; it doesn't matter
		return DoubleNum.valueOf(n);
	}

}
