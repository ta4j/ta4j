package org.ta4j.core.trading.rules;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.DateTimeIndicator;
import org.ta4j.core.mocks.MockBarSeries;
import org.ta4j.core.num.Num;

public class DayOfWeekRuleTest extends AbstractIndicatorTest<Object,Object> {

    public DayOfWeekRuleTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }
    
	@Test
	public void isSatisfied() {
        final DateTimeFormatter dtf = DateTimeFormatter.ISO_ZONED_DATE_TIME;
        DateTimeIndicator dateTime = new DateTimeIndicator(new MockBarSeries(numFunction,
                new double[] {100, 100, 100, 100, 100, 100, 100},
                new ZonedDateTime[] {
                        ZonedDateTime.parse("2019-09-16T12:00:00-00:00", dtf), //Index=0, Mon
                        ZonedDateTime.parse("2019-09-17T12:00:00-00:00", dtf), //1, Tue
                        ZonedDateTime.parse("2019-09-18T12:00:00-00:00", dtf), //2, Wed
                        ZonedDateTime.parse("2019-09-19T12:00:00-00:00", dtf), //3, Thu
                        ZonedDateTime.parse("2019-09-20T12:00:00-00:00", dtf), //4, Fri
                        ZonedDateTime.parse("2019-09-21T12:00:00-00:00", dtf), //5, Sat
                        ZonedDateTime.parse("2019-09-22T12:00:00-00:00", dtf) //6, Sun
                    }
        ), Bar::getEndTime);
        DayOfWeekRule rule = new DayOfWeekRule(dateTime, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
        
        assertTrue(rule.isSatisfied(0, null));
        assertTrue(rule.isSatisfied(1, null));
        assertTrue(rule.isSatisfied(2, null));
        assertTrue(rule.isSatisfied(3, null));
        assertTrue(rule.isSatisfied(4, null));
        assertFalse(rule.isSatisfied(5, null));
        assertFalse(rule.isSatisfied(6, null));
	}
}
