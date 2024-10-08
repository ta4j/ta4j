/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core;

import static org.ta4j.core.TestUtils.assertIndicatorEquals;
import static org.ta4j.core.TestUtils.assertIndicatorNotEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.TestUtils.assertNumNotEquals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class TestUtilsTest extends AbstractIndicatorTest<BarSeries, Num> {

    private static final String stringDouble = "1234567890.12345";
    private static final String diffStringDouble = "1234567890.12346";
    private static final BigDecimal bigDecimalDouble = new BigDecimal(stringDouble);
    private static final BigDecimal diffBigDecimalDouble = new BigDecimal(diffStringDouble);
    private static final int aInt = 1234567890;
    private static final int diffInt = 1234567891;
    private static final double aDouble = 1234567890.1234;
    private static final double diffDouble = 1234567890.1235;
    private static Num numStringDouble;
    private static Num diffNumStringDouble;
    private static Num numInt;
    private static Num diffNumInt;
    private static Num numDouble;
    private static Num diffNumDouble;
    private static Indicator<Num> indicator;
    private static Indicator<Num> diffIndicator;

    public TestUtilsTest(NumFactory numFactory) {
        super(numFactory);
        numStringDouble = numOf(bigDecimalDouble);
        diffNumStringDouble = numOf(diffBigDecimalDouble);
        numInt = numOf(aInt);
        diffNumInt = numOf(diffInt);
        numDouble = numOf(aDouble);
        diffNumDouble = numOf(diffDouble);
        BarSeries series = randomSeries();
        BarSeries diffSeries = randomSeries();
        indicator = new ClosePriceIndicator(series);
        diffIndicator = new ClosePriceIndicator(diffSeries);
    }

    private BarSeries randomSeries() {
        var series = new BaseBarSeriesBuilder().withNumFactory(numFactory).build();

        var time = Instant.parse("1970-01-01T01:01:01Z");
        double random;
        for (int i = 0; i < 1000; i++) {
            random = Math.random();
            time = time.plus(Duration.ofDays(i));
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(time)
                    .openPrice(random)
                    .closePrice(random)
                    .highPrice(random)
                    .lowPrice(random)
                    .amount(random)
                    .volume(random)
                    .trades(0)
                    .add();
        }
        return series;
    }

    @Test
    public void testStringNum() {
        assertNumEquals(stringDouble, numStringDouble);
        assertNumNotEquals(stringDouble, diffNumStringDouble);
        assertNumNotEquals(diffStringDouble, numStringDouble);
        assertNumEquals(diffStringDouble, diffNumStringDouble);
    }

    @Test
    public void testNumNum() {
        assertNumEquals(numStringDouble, numStringDouble);
        assertNumNotEquals(numStringDouble, diffNumStringDouble);
        assertNumNotEquals(diffNumStringDouble, numStringDouble);
        assertNumEquals(diffNumStringDouble, diffNumStringDouble);
    }

    @Test
    public void testIntNum() {
        assertNumEquals(aInt, numInt);
        assertNumNotEquals(aInt, diffNumInt);
        assertNumNotEquals(diffInt, numInt);
        assertNumEquals(diffInt, diffNumInt);
    }

    @Test
    public void testDoubleNum() {
        assertNumEquals(aDouble, numDouble);
        assertNumNotEquals(aDouble, diffNumDouble);
        assertNumNotEquals(diffDouble, numDouble);
        assertNumEquals(diffDouble, diffNumDouble);
    }

    @Test
    public void testIndicator() {
        assertIndicatorEquals(indicator, indicator);
        assertIndicatorNotEquals(indicator, diffIndicator);
        assertIndicatorNotEquals(diffIndicator, indicator);
        assertIndicatorEquals(diffIndicator, diffIndicator);
    }
}
