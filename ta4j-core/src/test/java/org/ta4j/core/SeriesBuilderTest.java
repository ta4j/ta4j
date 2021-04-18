/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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

import static junit.framework.TestCase.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.TestUtils.assertNumNotEquals;

import java.time.ZonedDateTime;
import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

public class SeriesBuilderTest extends AbstractIndicatorTest<BarSeries, Num> {

    public SeriesBuilderTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    private final BaseBarSeriesBuilder seriesBuilder = new BaseBarSeriesBuilder().withNumTypeOf(numFunction);

    @Test
    public void testBuilder() {
        BarSeries defaultSeries = seriesBuilder.build(); // build a new empty unnamed bar series
        BarSeries defaultSeriesName = seriesBuilder.withName("default").build(); // build a new empty bar series using
                                                                                 // BigDecimal as delegate
        BarSeries doubleSeries = seriesBuilder.withMaxBarCount(100)
                .withNumTypeOf(DoubleNum.class)
                .withName("useDoubleNum")
                .build();
        BarSeries precisionSeries = seriesBuilder.withMaxBarCount(100)
                .withNumTypeOf(DecimalNum.class)
                .withName("usePrecisionNum")
                .build();

        for (int i = 1000; i >= 0; i--) {
            defaultSeries.addBar(ZonedDateTime.now().minusSeconds(i), i, i, i, i, i);
            defaultSeriesName.addBar(ZonedDateTime.now().minusSeconds(i), i, i, i, i, i);
            doubleSeries.addBar(ZonedDateTime.now().minusSeconds(i), i, i, i, i, i);
            precisionSeries.addBar(ZonedDateTime.now().minusSeconds(i), i, i, i, i, i);
        }

        assertNumEquals(0, defaultSeries.getBar(1000).getClosePrice());
        assertNumEquals(1000, defaultSeries.getBar(0).getClosePrice());
        assertEquals(defaultSeriesName.getName(), "default");
        assertNumEquals(99, doubleSeries.getBar(0).getClosePrice());
        assertNumEquals(99, precisionSeries.getBar(0).getClosePrice());
    }

    @Test
    public void testNumFunctions() {
        BarSeries series = seriesBuilder.withNumTypeOf(DoubleNum.class).build();
        assertNumEquals(series.numOf(12), DoubleNum.valueOf(12));

        BarSeries seriesB = seriesBuilder.withNumTypeOf(DecimalNum.class).build();
        assertNumEquals(seriesB.numOf(12), DecimalNum.valueOf(12));
    }

    @Test
    public void testWrongNumType() {
        BarSeries series = seriesBuilder.withNumTypeOf(DecimalNum.class).build();
        assertNumNotEquals(series.numOf(12), DoubleNum.valueOf(12));
    }
}
