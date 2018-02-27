/**
 * 
 */
package org.ta4j.core.indicators;

import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;
import java.util.Random;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

/**
 * @author jtomkinson
 *
 */
class ChopIndicatorTest  {
	/**
	 * this will assert that choppiness is high if market price is not moving
	 */
	@Test
	void testChoppy() {
        BaseTimeSeries.SeriesBuilder timeSeriesBuilder = new BaseTimeSeries.SeriesBuilder();
        TimeSeries series = timeSeriesBuilder.withName("low volatility series").withNumTypeOf(DoubleNum::valueOf).build();
        for (int i = 0; i < 50; i++) {
            ZonedDateTime date = ZonedDateTime.now().minusSeconds(100000 - i);
            series.addBar(date, 21.5, 21.5+1, 21.5 - 1, 21.5);
        }
		ChopIndicator ci1 = new ChopIndicator( series, 14, 100 );
	    int HIGH_CHOPPINESS_VALUE = 85;
		assertTrue( ci1.getValue( series.getEndIndex() ).doubleValue() > HIGH_CHOPPINESS_VALUE );
	}
	
	/**
	 * this will assert that choppiness is low if market price is trending significantly
	 */
	@Test
	void testTradableTrend() {
        BaseTimeSeries.SeriesBuilder timeSeriesBuilder = new BaseTimeSeries.SeriesBuilder();
        TimeSeries series = timeSeriesBuilder.withName("low volatility series").withNumTypeOf(DoubleNum::valueOf).build();
        float value = 21.5f;
        for (int i = 0; i < 50; i++) {
            ZonedDateTime date = ZonedDateTime.now().minusSeconds(100000 - i);
            series.addBar(date, value, value+1, value - 1, value);
            value += 2.0f;
        }
		ChopIndicator ci1 = new ChopIndicator( series, 14, 100 );
		int LOW_CHOPPINESS_VALUE = 30;
	    assertTrue( ci1.getValue( series.getEndIndex() ).doubleValue() < LOW_CHOPPINESS_VALUE );
	}

}
