package ta4jexamples.indicators.math;

/**
 * A series of elements of type T.
 * T will usually be Num, although Boolean and other types might be useful.
 * 
 *  This simple interface is much easier to work with than Indictor<T>.
 *  The getBarSeries() and numOf() methods are awkward or impossible to implement.
 *  The real purpose of indicators is math and we could work with this much simpler interface most of the time.
 *  
 *  I would like to make this a supertype of Indicator<T>; it should be fine.
 *
 */
public interface Series<T> {
	T getValue(int index);
}
