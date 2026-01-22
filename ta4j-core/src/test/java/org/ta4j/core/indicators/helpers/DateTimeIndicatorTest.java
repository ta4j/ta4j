/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.junit.Assert.assertEquals;

import java.time.Instant;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DateTimeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public DateTimeIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void test() {
        Instant expectedDateTime = Instant.parse("2019-09-17T00:04:00Z");
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().endTime(expectedDateTime).add();
        DateTimeIndicator dateTimeIndicator = new DateTimeIndicator(series, Bar::getEndTime);
        assertEquals(expectedDateTime, dateTimeIndicator.getValue(0));
    }
}
