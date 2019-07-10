package org.ta4j.core;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;

public class BaseBarBuilderTest extends AbstractIndicatorTest<TimeSeries, Num> {

    public BaseBarBuilderTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void testBuildBar() {
        final ZonedDateTime beginTime = ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault());
        final ZonedDateTime endTime = ZonedDateTime.of(2014, 6, 25, 1, 0, 0, 0, ZoneId.systemDefault());
        final Duration duration = Duration.between(beginTime, endTime);

        final BaseBar bar = new BaseBarBuilder().timePeriod(duration)
                .endTime(endTime)
                .openPrice(numOf(101))
                .highPrice(numOf(103))
                .lowPrice(numOf(100))
                .closePrice(numOf(102))
                .trades(4)
                .volume(numOf(40))
                .amount(numOf(4020))
                .build();

        assertEquals(duration, bar.getTimePeriod());
        assertEquals(beginTime, bar.getBeginTime());
        assertEquals(endTime, bar.getEndTime());
        assertEquals(numOf(101), bar.getOpenPrice());
        assertEquals(numOf(103), bar.getHighPrice());
        assertEquals(numOf(100), bar.getLowPrice());
        assertEquals(numOf(102), bar.getClosePrice());
        assertEquals(4, bar.getTrades());
        assertEquals(numOf(40), bar.getVolume());
        assertEquals(numOf(4020), bar.getAmount());
    }
}