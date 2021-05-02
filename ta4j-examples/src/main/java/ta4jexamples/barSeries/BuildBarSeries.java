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
package ta4jexamples.barSeries;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.ConvertibleBaseBarBuilder;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DoubleNum;

public class BuildBarSeries {

    /**
     * Calls different functions that shows how a BaseBarSeries could be created and
     * how Bars could be added
     *
     * @param args command line arguments (ignored)
     */
    @SuppressWarnings("unused")
    public static void main(String[] args) {
        BarSeries a = buildAndAddData();
        System.out.println("a: " + a.getBar(0).getClosePrice().getName());
        BaseBarSeriesBuilder.setDefaultFunction(DoubleNum::valueOf);
        a = buildAndAddData();
        System.out.println("a: " + a.getBar(0).getClosePrice().getName());
        BarSeries b = buildWithDouble();
        BarSeries c = buildWithBigDecimal();
        BarSeries d = buildManually();
        BarSeries e = buildManuallyDoubleNum();
        BarSeries f = buildManuallyAndAddBarManually();
        BarSeries g = buildAndAddBarsFromList();
        // Fix: Reset default function, such that this test case does not influence the
        // following test cases in a combined test run
        BaseBarSeriesBuilder.setDefaultFunction(DecimalNum::valueOf);
    }

    private static BarSeries buildAndAddData() {
        BarSeries series = new BaseBarSeriesBuilder().withName("mySeries").build();

        ZonedDateTime endTime = ZonedDateTime.now();
        series.addBar(endTime, 105.42, 112.99, 104.01, 111.42, 1337);
        series.addBar(endTime.plusDays(1), 111.43, 112.83, 107.77, 107.99, 1234);
        series.addBar(endTime.plusDays(2), 107.90, 117.50, 107.90, 115.42, 4242);
        // ...
        return series;
    }

    private static BarSeries buildWithDouble() {
        BarSeries series = new BaseBarSeriesBuilder().withName("mySeries").withNumTypeOf(DoubleNum.class).build();

        ZonedDateTime endTime = ZonedDateTime.now();
        series.addBar(endTime, 105.42, 112.99, 104.01, 111.42, 1337);
        series.addBar(endTime.plusDays(1), 111.43, 112.83, 107.77, 107.99, 1234);
        series.addBar(endTime.plusDays(2), 107.90, 117.50, 107.90, 115.42, 4242);
        // ...

        return series;
    }

    private static BarSeries buildWithBigDecimal() {
        BarSeries series = new BaseBarSeriesBuilder().withName("mySeries").withNumTypeOf(DecimalNum.class).build();

        ZonedDateTime endTime = ZonedDateTime.now();
        series.addBar(endTime, 105.42, 112.99, 104.01, 111.42, 1337);
        series.addBar(endTime.plusDays(1), 111.43, 112.83, 107.77, 107.99, 1234);
        series.addBar(endTime.plusDays(2), 107.90, 117.50, 107.90, 115.42, 4242);
        // ...

        return series;
    }

    private static BarSeries buildManually() {
        BarSeries series = new BaseBarSeries("mySeries"); // uses BigDecimalNum

        ZonedDateTime endTime = ZonedDateTime.now();
        series.addBar(endTime, 105.42, 112.99, 104.01, 111.42, 1337);
        series.addBar(endTime.plusDays(1), 111.43, 112.83, 107.77, 107.99, 1234);
        series.addBar(endTime.plusDays(2), 107.90, 117.50, 107.90, 115.42, 4242);
        // ...

        return series;
    }

    private static BarSeries buildManuallyDoubleNum() {
        BarSeries series = new BaseBarSeries("mySeries", DoubleNum::valueOf); // uses DoubleNum
        ZonedDateTime endTime = ZonedDateTime.now();
        series.addBar(endTime, 105.42, 112.99, 104.01, 111.42, 1337);
        series.addBar(endTime.plusDays(1), 111.43, 112.83, 107.77, 107.99, 1234);
        series.addBar(endTime.plusDays(2), 107.90, 117.50, 107.90, 115.42, 4242);
        // ...

        return series;
    }

    private static BarSeries buildManuallyAndAddBarManually() {
        BarSeries series = new BaseBarSeries("mySeries", DoubleNum::valueOf); // uses DoubleNum

        // create bars and add them to the series. The bars must have the same Num type
        // as the series
        ZonedDateTime endTime = ZonedDateTime.now();
        Bar b1 = BaseBar.builder(DoubleNum::valueOf, Double.class)
                .timePeriod(Duration.ofDays(1))
                .endTime(endTime)
                .openPrice(105.42)
                .highPrice(112.99)
                .lowPrice(104.01)
                .closePrice(111.42)
                .volume(1337.0)
                .build();
        Bar b2 = BaseBar.builder(DoubleNum::valueOf, Double.class)
                .timePeriod(Duration.ofDays(1))
                .endTime(endTime.plusDays(1))
                .openPrice(111.43)
                .highPrice(112.83)
                .lowPrice(107.77)
                .closePrice(107.99)
                .volume(1234.0)
                .build();
        Bar b3 = BaseBar.builder(DoubleNum::valueOf, Double.class)
                .timePeriod(Duration.ofDays(1))
                .endTime(endTime.plusDays(2))
                .openPrice(107.90)
                .highPrice(117.50)
                .lowPrice(107.90)
                .closePrice(115.42)
                .volume(4242.0)
                .build();
        // ...

        series.addBar(b1);
        series.addBar(b2);
        series.addBar(b3);

        return series;
    }

    private static BarSeries buildAndAddBarsFromList() {
        // Store Bars in a list and add them later. The bars must have the same Num type
        // as the series
        ZonedDateTime endTime = ZonedDateTime.now();
        Bar b1 = barBuilderFromString().timePeriod(Duration.ofDays(1))
                .endTime(endTime)
                .openPrice("105.42")
                .highPrice("112.99")
                .lowPrice("104.01")
                .closePrice("111.42")
                .volume("1337")
                .build();
        Bar b2 = barBuilderFromString().timePeriod(Duration.ofDays(1))
                .endTime(endTime.plusDays(1))
                .openPrice("111.43")
                .highPrice("112.83")
                .lowPrice("107.77")
                .closePrice("107.99")
                .volume("1234")
                .build();
        Bar b3 = barBuilderFromString().timePeriod(Duration.ofDays(1))
                .endTime(endTime.plusDays(2))
                .openPrice("107.90")
                .highPrice("117.50")
                .lowPrice("107.90")
                .closePrice("115.42")
                .volume("4242")
                .build();
        List<Bar> bars = Arrays.asList(b1, b2, b3);

        return new BaseBarSeriesBuilder().withName("mySeries")
                .withNumTypeOf(DoubleNum::valueOf)
                .withMaxBarCount(5)
                .withBars(bars)
                .build();
    }

    private static ConvertibleBaseBarBuilder<String> barBuilderFromString() {
        return BaseBar.builder(DoubleNum::valueOf, String.class);
    }
}
