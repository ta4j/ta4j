package org.ta4j.core;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

@RunWith(Parameterized.class)
public class ConvertibleBaseBarBuilderTest extends AbstractIndicatorTest<TimeSeries, Num> {

    public ConvertibleBaseBarBuilderTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Test
    public void testBuildBigDecimal() {
        new ConvertibleBaseBarBuilder<BigDecimal>(PrecisionNum::valueOf);

        final ZonedDateTime beginTime = ZonedDateTime.of(2014, 6, 25, 0, 0, 0, 0, ZoneId.systemDefault());
        final ZonedDateTime endTime = ZonedDateTime.of(2014, 6, 25, 1, 0, 0, 0, ZoneId.systemDefault());
        final Duration duration = Duration.between(beginTime, endTime);

        final BaseBar bar = new ConvertibleBaseBarBuilder<BigDecimal>(this::numOf).timePeriod(duration)
                .endTime(endTime)
                .openPrice(BigDecimal.valueOf(101.0))
                .highPrice(BigDecimal.valueOf(103))
                .lowPrice(BigDecimal.valueOf(100))
                .closePrice(BigDecimal.valueOf(102))
                .trades(4)
                .volume(BigDecimal.valueOf(40))
                .amount(BigDecimal.valueOf(4020))
                .build();

        assertEquals(duration, bar.getTimePeriod());
        assertEquals(beginTime, bar.getBeginTime());
        assertEquals(endTime, bar.getEndTime());
        assertNumEquals(numOf(101.0), bar.getOpenPrice());
        assertNumEquals(numOf(103), bar.getHighPrice());
        assertNumEquals(numOf(100), bar.getLowPrice());
        assertNumEquals(numOf(102), bar.getClosePrice());
        assertEquals(4, bar.getTrades());
        assertNumEquals(numOf(40), bar.getVolume());
        assertNumEquals(numOf(4020), bar.getAmount());
    }
}