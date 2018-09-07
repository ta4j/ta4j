/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package ta4jexamples.timeSeries;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.BaseTimeSeries.SeriesBuilder;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.num.PrecisionNum;
import org.ta4j.core.num.DoubleNum;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

public class BuildTimeSeries {

    /**
     * Calls different functions that shows how a BaseTimeSeries
     * could be created and how Bars could be added
     * @param args command line arguments (ignored)
     */
    public static void main(String[] args){
        TimeSeries a = buildAndAddData();
        System.out.println("a: " + a.getBar(0).getClosePrice().getName());
        SeriesBuilder.setDefaultFunction(DoubleNum::valueOf);
        a = buildAndAddData();
        System.out.println("a: " + a.getBar(0).getClosePrice().getName());
        TimeSeries b = buildWithDouble();
        TimeSeries c = buildWithBigDecimal();
        TimeSeries d = buildManually();
        TimeSeries e = buildManuallyDoubleNum();
        TimeSeries f = buildManuallyAndaddBarManually();
        TimeSeries g = buildAndaddBarsFromList();
    }


    public static TimeSeries buildAndAddData(){
        TimeSeries series = new BaseTimeSeries.SeriesBuilder().withName("mySeries").build();

        ZonedDateTime endTime = ZonedDateTime.now();
        series.addBar(endTime, 105.42, 112.99, 104.01, 111.42, 1337);
        series.addBar(endTime.plusDays(1), 111.43, 112.83, 107.77, 107.99, 1234);
        series.addBar(endTime.plusDays(2), 107.90, 117.50, 107.90, 115.42, 4242);
        //...
        return series;
    }

    public static TimeSeries buildWithDouble(){
        TimeSeries series = new BaseTimeSeries.SeriesBuilder().withName("mySeries").withNumTypeOf(DoubleNum.class).build();

        ZonedDateTime endTime = ZonedDateTime.now();
        series.addBar(endTime, 105.42, 112.99, 104.01, 111.42, 1337);
        series.addBar(endTime.plusDays(1), 111.43, 112.83, 107.77, 107.99, 1234);
        series.addBar(endTime.plusDays(2), 107.90, 117.50, 107.90, 115.42, 4242);
        //...

        return series;
    }

    public static TimeSeries buildWithBigDecimal(){
        TimeSeries series = new BaseTimeSeries.SeriesBuilder().withName("mySeries").withNumTypeOf(PrecisionNum.class).build();

        ZonedDateTime endTime = ZonedDateTime.now();
        series.addBar(endTime, 105.42, 112.99, 104.01, 111.42, 1337);
        series.addBar(endTime.plusDays(1), 111.43, 112.83, 107.77, 107.99, 1234);
        series.addBar(endTime.plusDays(2), 107.90, 117.50, 107.90, 115.42, 4242);
        //...

        return series;
    }

    public static TimeSeries buildManually(){
        TimeSeries series = new BaseTimeSeries("mySeries"); // uses BigDecimalNum

        ZonedDateTime endTime = ZonedDateTime.now();
        series.addBar(endTime, 105.42, 112.99, 104.01, 111.42, 1337);
        series.addBar(endTime.plusDays(1), 111.43, 112.83, 107.77, 107.99, 1234);
        series.addBar(endTime.plusDays(2), 107.90, 117.50, 107.90, 115.42, 4242);
        //...

        return series;
    }

    public static TimeSeries buildManuallyDoubleNum(){
        TimeSeries series = new BaseTimeSeries("mySeries", DoubleNum::valueOf); // uses DoubleNum
        ZonedDateTime endTime = ZonedDateTime.now();
        series.addBar(endTime, 105.42, 112.99, 104.01, 111.42, 1337);
        series.addBar(endTime.plusDays(1), 111.43, 112.83, 107.77, 107.99, 1234);
        series.addBar(endTime.plusDays(2), 107.90, 117.50, 107.90, 115.42, 4242);
        //...

        return series;
    }

    public static TimeSeries buildManuallyAndaddBarManually(){
        TimeSeries series = new BaseTimeSeries("mySeries", DoubleNum::valueOf); // uses DoubleNum

        // create bars and add them to the series. The bars must have the same Num type as the series
        ZonedDateTime endTime = ZonedDateTime.now();
        Bar b1 = new BaseBar(endTime, 105.42, 112.99, 104.01, 111.42, 1337, DoubleNum::valueOf);
        Bar b2 = new BaseBar(endTime.plusDays(1), 111.43, 112.83, 107.77, 107.99, 1234,DoubleNum::valueOf);
        Bar b3 = new BaseBar(endTime.plusDays(2), 107.90, 117.50, 107.90, 115.42, 4242, DoubleNum::valueOf);
        //...

        series.addBar(b1);
        series.addBar(b2);
        series.addBar(b3);

        return series;
    }

    public static TimeSeries buildAndaddBarsFromList(){
        // Store Bars in a list and add them later. The bars must have the same Num type as the series
        ZonedDateTime endTime = ZonedDateTime.now();
        Bar b1 = new BaseBar(endTime, 105.42, 112.99, 104.01, 111.42, 1337, DoubleNum::valueOf);
        Bar b2 = new BaseBar(endTime.plusDays(1), 111.43, 112.83, 107.77, 107.99, 1234,DoubleNum::valueOf);
        Bar b3 = new BaseBar(endTime.plusDays(2), 107.90, 117.50, 107.90, 115.42, 4242, DoubleNum::valueOf);
        List<Bar> bars = Arrays.asList(b1, b2, b3);
        TimeSeries series = new BaseTimeSeries
                .SeriesBuilder()
                .withName("mySeries")
                .withNumTypeOf(DoubleNum::valueOf)
                .withMaxBarCount(5)
                .withBars(bars)
                .build();



        return series;
    }
}
