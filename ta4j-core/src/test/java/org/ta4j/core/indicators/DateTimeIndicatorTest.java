package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.mocks.MockBar;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class DateTimeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    final static DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;

	public DateTimeIndicatorTest(Function<Number, Num> numFunction) {
		super(numFunction);
	}
    
    @Test
    public void test() {
    	ZonedDateTime expectedZonedDateTime = ZonedDateTime.parse("2019-09-17T00:04:00-00:00", DATE_TIME_FORMATTER);
        List<Bar> bars = Arrays.asList(new MockBar(expectedZonedDateTime, 1, numFunction));
        BarSeries series = new MockBarSeries(bars);
        DateTimeIndicator dateTimeIndicator = new DateTimeIndicator(series, Bar::getEndTime);
        assertEquals(expectedZonedDateTime, dateTimeIndicator.getValue(0));
    }
}
