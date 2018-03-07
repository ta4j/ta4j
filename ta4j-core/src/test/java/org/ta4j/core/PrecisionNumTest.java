package org.ta4j.core;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.BigDecimalNum;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.TestUtils.assertIndicatorEquals;

public class PrecisionNumTest {

    final static int NUMBARS = 10000;
    final static int NUMLOOPS = 1;

    // 120 digit precision
    final static String superPrecisionString =
            "1.234567890" + // 10 
            "1234567890" + // 20
            "1234567890" + // 30
            "1234567890" + // 40
            "1234567890" + // 50
            "1234567890" + // 60
            "1234567890" + // 70
            "1234567890" + // 80
            "1234567890" + // 90
            "1234567890" + // 100
            "1234567890" + // 110
            "1234567890"; // 120
    final static Num firstSuperPrecisionNum = PrecisionNum.valueOf(superPrecisionString);
    Num superPrecisionNum = PrecisionNum.valueOf(superPrecisionString);

    // override the auto-precision based on length of superPrecisionString by passing a precision to valueOf()
    Function<Number, Num> superPrecisionFunc = (number -> PrecisionNum.valueOf(number.toString(), 256));
    // auto-set precision based on length of superPrecisionString (120)
    Function<Number, Num> precisionFunc = PrecisionNum::valueOf;
    Function<Number, Num> precision32Func = (number -> PrecisionNum.valueOf(number.toString(), 32));
    Function<Number, Num> bigDecimalFunc = BigDecimalNum::valueOf;
    Function<Number, Num> doubleFunc = DoubleNum::valueOf;

    TimeSeries superPrecisionSeries;
    TimeSeries precisionSeries;
    TimeSeries precision32Series;
    TimeSeries bigDecimalSeries;
    TimeSeries doubleSeries;

    Indicator<Num> superPrecisionIndicator;
    Indicator<Num> precisionIndicator;
    Indicator<Num> precision32Indicator;
    Indicator<Num> bigDecimalIndicator;
    Indicator<Num> doubleIndicator;

    @Test
    public void precisionNumTest() throws Exception {
        // different series values may lead to different accuracies
        // series is generated randomly so test a bunch of them to find nefarious cases
        for (int i = 0; i < NUMLOOPS; i++) {
            init();
            test();
        }
    }

    public void init() throws Exception {
        List<Bar> superPrecisionBarList = new ArrayList<Bar>();
        List<Bar> precisionBarList = new ArrayList<Bar>();
        List<Bar> precision32BarList = new ArrayList<Bar>();
        List<Bar> bigDecimalBarList = new ArrayList<Bar>();
        List<Bar> doubleBarList = new ArrayList<Bar>();
        Duration timePeriod = Duration.ofDays(1);
        ZonedDateTime endTime = ZonedDateTime.now();
        Bar bar;
        Random random = new Random();
        superPrecisionNum = firstSuperPrecisionNum;
        for (int i = 0; i < NUMBARS; i++) {
            bar = new BaseBar(timePeriod, endTime, superPrecisionFunc);
            bar.addPrice(superPrecisionNum);
            superPrecisionBarList.add(bar);
            bar = new BaseBar(timePeriod, endTime, precisionFunc);
            bar.addPrice(superPrecisionNum);
            precisionBarList.add(bar);
            bar = new BaseBar(timePeriod, endTime, precision32Func);
            bar.addPrice(superPrecisionNum);
            precision32BarList.add(bar);
            bar = new BaseBar(timePeriod, endTime, bigDecimalFunc);
            bar.addPrice(superPrecisionNum);
            bigDecimalBarList.add(bar);
            bar = new BaseBar(timePeriod, endTime, doubleFunc);
            bar.addPrice(superPrecisionNum);
            doubleBarList.add(bar);
            endTime = endTime.plus(timePeriod);
            superPrecisionNum = superPrecisionNum.plus(superPrecisionFunc.apply(100d * random.nextGaussian()));
        }
        superPrecisionSeries = new BaseTimeSeries.SeriesBuilder()
                .withName("superPrecision").withNumTypeOf(superPrecisionFunc).withBars(superPrecisionBarList).build();
        precisionSeries = new BaseTimeSeries.SeriesBuilder()
                .withName("precision").withNumTypeOf(precisionFunc).withBars(precisionBarList).build();
        precision32Series = new BaseTimeSeries.SeriesBuilder()
                .withName("precision32").withNumTypeOf(precision32Func).withBars(precision32BarList).build();
        bigDecimalSeries = new BaseTimeSeries.SeriesBuilder()
                .withName("bigDecimal").withNumTypeOf(bigDecimalFunc).withBars(bigDecimalBarList).build();
        doubleSeries = new BaseTimeSeries.SeriesBuilder()
                .withName("double").withNumTypeOf(doubleFunc).withBars(doubleBarList).build();
    }

    public void test() throws InterruptedException {
        assertNumEquals(superPrecisionString, superPrecisionSeries.getBar(0).getClosePrice());
        assertNumEquals(precisionFunc.apply(firstSuperPrecisionNum.getDelegate()).toString(), precisionSeries.getBar(0).getClosePrice());
        assertNumEquals(precision32Func.apply(firstSuperPrecisionNum.getDelegate()).toString(), precision32Series.getBar(0).getClosePrice());
        assertNumEquals(bigDecimalFunc.apply(firstSuperPrecisionNum.getDelegate()).toString(), bigDecimalSeries.getBar(0).getClosePrice());
        assertNumEquals(doubleFunc.apply(firstSuperPrecisionNum.getDelegate()).toString(), doubleSeries.getBar(0).getClosePrice());

        Indicator<Num> superPrecisionClose = new ClosePriceIndicator(superPrecisionSeries);
        Indicator<Num> precisionClose = new ClosePriceIndicator(precisionSeries);
        Indicator<Num> precision32Close = new ClosePriceIndicator(precision32Series);
        Indicator<Num> bigDecimalClose = new ClosePriceIndicator(bigDecimalSeries);
        Indicator<Num> doubleClose = new ClosePriceIndicator(doubleSeries);

        superPrecisionIndicator = new RSIIndicator(superPrecisionClose, 200);
        precisionIndicator = new RSIIndicator(precisionClose, 200);
        precision32Indicator = new RSIIndicator(precision32Close, 200);
        bigDecimalIndicator = new RSIIndicator(bigDecimalClose, 200);
        doubleIndicator = new RSIIndicator(doubleClose, 200);

        calculateSuperPrecision();
        calculatePrecision();
        calculatePrecision32();
        calculateBigDecimal();
        calculateDouble();

        // accuracies relative to SuperPrecision (maximum precisions to which they match superPrecision) 
        assertIndicatorEquals(superPrecisionIndicator, precisionIndicator,
                PrecisionNum.valueOf("0.0000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001"));
        assertIndicatorEquals(superPrecisionIndicator, precision32Indicator,
                PrecisionNum.valueOf("0.0000000000000000000000000001"));
        assertIndicatorEquals(superPrecisionIndicator, bigDecimalIndicator,
                PrecisionNum.valueOf("0.0000000000000000000000000001"));
        assertIndicatorEquals(superPrecisionIndicator, doubleIndicator,
                PrecisionNum.valueOf("0.0000000000001"));
        // accuracies relative to Precision (maximum precisions to which they match Precision) 
        assertIndicatorEquals(precisionIndicator, precision32Indicator,
                PrecisionNum.valueOf("0.0000000000000000000000000001"));
        assertIndicatorEquals(precisionIndicator, bigDecimalIndicator,
                PrecisionNum.valueOf("0.0000000000000000000000000001"));
        assertIndicatorEquals(precisionIndicator, doubleIndicator,
                PrecisionNum.valueOf("0.0000000000001"));
        // accuracies relative to BigDecimal (maximum precisions to which they match BigDecimal) 
        // since precision32Indicator and bigDecimalIndicator have the same precision, they match to any precision
        assertIndicatorEquals(precision32Indicator, bigDecimalIndicator,
                PrecisionNum.valueOf("0.00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001"));
        assertIndicatorEquals(precision32Indicator, doubleIndicator,
                PrecisionNum.valueOf("0.0000000000001"));
        // accuracies relative to Double (maximum precisions to which they match Double) 
        assertIndicatorEquals(bigDecimalIndicator, doubleIndicator,
                PrecisionNum.valueOf("0.0000000000001"));

        // This helps for doing a memory snapshot
        //Thread.sleep(1000000);
    }

    public void calculateSuperPrecision() {
        Indicator<Num> indicator = superPrecisionIndicator;
        for (int i = indicator.getTimeSeries().getBeginIndex(); i < indicator.getTimeSeries().getEndIndex(); i++) {
            indicator.getValue(i);
        }
    }

    public void calculatePrecision() {
        Indicator<Num> indicator = precisionIndicator;
        for (int i = indicator.getTimeSeries().getBeginIndex(); i < indicator.getTimeSeries().getEndIndex(); i++) {
            indicator.getValue(i);
        }
    }

    public void calculatePrecision32() {
        Indicator<Num> indicator = precision32Indicator;
        for (int i = indicator.getTimeSeries().getBeginIndex(); i < indicator.getTimeSeries().getEndIndex(); i++) {
            indicator.getValue(i);
        }
    }

    public void calculateBigDecimal() {
        Indicator<Num> indicator = bigDecimalIndicator;
        for (int i = indicator.getTimeSeries().getBeginIndex(); i < indicator.getTimeSeries().getEndIndex(); i++) {
            indicator.getValue(i);
        }
    }

    public void calculateDouble() {
        Indicator<Num> indicator = doubleIndicator;
        for (int i = indicator.getTimeSeries().getBeginIndex(); i < indicator.getTimeSeries().getEndIndex(); i++) {
            indicator.getValue(i);
        }
    }

}
