package ta4jexamples.indicators.math;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;

/**
 * Wraps around an Indicator<Num>, providing convenience methods to do mathematical operators on indicators.
 * Also provides convenience methods for creating indicator-based rules.
 *
 */
public class BasicNumericIndicator extends AbstractNumericIndicator {
	
	private final Indicator<Num> delegate;
	
	public BasicNumericIndicator(Indicator<Num> delegate) {
		this.delegate = delegate;
	}
	
	public Indicator<Num> getDelegate() {
		return delegate;
	}

	@Override
	public Num getValue(int index) {
		return delegate.getValue(index);
	}

	@Override
	public BarSeries getBarSeries() {
		return delegate.getBarSeries();
	}
	
	


}
