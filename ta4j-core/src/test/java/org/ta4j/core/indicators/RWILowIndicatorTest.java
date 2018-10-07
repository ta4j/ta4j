package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertIndicatorEquals;

import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.Num;

/**
 * Testing the RWILowIndicator
 */
public class RWILowIndicatorTest extends AbstractIndicatorTest<TimeSeries,Num>{

	/**
	 * TODO: Just graphically Excel-Sheet validation with hard coded results. Excel formula needed
	 */
	private ExternalIndicatorTest xls;

    public RWILowIndicatorTest(Function<Number, Num> numFunction) {
        super((data, params) -> new RWILowIndicator(data, (int) params[0]), numFunction);
        xls = new XLSIndicatorTest(this.getClass(), "RWIHL.xls", 9, numFunction);
    }

    @Test
    public void randomWalkIndexHigh() throws Exception{
    	TimeSeries series = xls.getSeries();
    	RWILowIndicator rwih = (RWILowIndicator) getIndicator(series, 20);
    	assertIndicatorEquals(getIndicator(series, 20), rwih);
    }
}
