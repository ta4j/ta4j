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

    final static int NUMBARS = 1000;
    final static int NUMLOOPS = 10;

    final static String superPrecisionString = "0.123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890";
    final static Num firstSuperPrecisionNum = PrecisionNum.valueOf(superPrecisionString);
    Num superPrecisionNum = PrecisionNum.valueOf(superPrecisionString);

    // override the auto-precision based on length of superPrecisionString by passing a precision to valueOf()
    Function<Number, Num> superPrecisionFunc = (number -> PrecisionNum.valueOf(number.toString(), 256));
    // auto-set precision based on length of superPrecisionString
    Function<Number, Num> precisionFunc = PrecisionNum::valueOf;
    Function<Number, Num> bigDecimalFunc = BigDecimalNum::valueOf;
    Function<Number, Num> doubleFunc = DoubleNum::valueOf;

    TimeSeries superPrecisionSeries;
    TimeSeries precisionSeries;
    TimeSeries bigDecimalSeries;
    TimeSeries doubleSeries;

    @Test
    public void precisionNumTest() throws Exception {
        // different series values may lead to different precisions
        // series is generated randomly so test a bunch of them to find nefarious cases
        for (int i = 0; i < NUMLOOPS; i++) {
            System.out.println("loop " + i);
            init();
            test();
        }
    }

    public void init() throws Exception {
        List<Bar> superPrecisionBarList = new ArrayList<Bar>();
        List<Bar> precisionBarList = new ArrayList<Bar>();
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
        bigDecimalSeries = new BaseTimeSeries.SeriesBuilder()
                .withName("bigDecimal").withNumTypeOf(bigDecimalFunc).withBars(bigDecimalBarList).build();
        doubleSeries = new BaseTimeSeries.SeriesBuilder()
                .withName("double").withNumTypeOf(doubleFunc).withBars(doubleBarList).build();
    }

    public void test() throws InterruptedException {
        assertNumEquals(superPrecisionString, superPrecisionSeries.getBar(0).getClosePrice());
        assertNumEquals(precisionFunc.apply(firstSuperPrecisionNum.getDelegate()).toString(), precisionSeries.getBar(0).getClosePrice());
        assertNumEquals(bigDecimalFunc.apply(firstSuperPrecisionNum.getDelegate()).toString(), bigDecimalSeries.getBar(0).getClosePrice());
        assertNumEquals(doubleFunc.apply(firstSuperPrecisionNum.getDelegate()).toString(), doubleSeries.getBar(0).getClosePrice());

        Indicator<Num> superPrecisionClose = new ClosePriceIndicator(superPrecisionSeries);
        Indicator<Num> precisionClose = new ClosePriceIndicator(precisionSeries);
        Indicator<Num> bigDecimalClose = new ClosePriceIndicator(bigDecimalSeries);
        Indicator<Num> doubleClose = new ClosePriceIndicator(doubleSeries);

        Indicator<Num> superPrecisionIndicator = new RSIIndicator(superPrecisionClose, 200);
        Indicator<Num> precisionIndicator = new RSIIndicator(precisionClose, 200);
        Indicator<Num> bigDecimalIndicator = new RSIIndicator(bigDecimalClose, 200);
        Indicator<Num> doubleIndicator = new RSIIndicator(doubleClose, 200);

        assertIndicatorEquals(superPrecisionIndicator, precisionIndicator,
                PrecisionNum.valueOf("0.00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000001"));
        assertIndicatorEquals(superPrecisionIndicator, bigDecimalIndicator,
                PrecisionNum.valueOf("0.0000000000000000000000000001"));
        assertIndicatorEquals(superPrecisionIndicator, doubleIndicator,
                PrecisionNum.valueOf("0.0000000000001"));
        assertIndicatorEquals(precisionIndicator, bigDecimalIndicator,
                PrecisionNum.valueOf("0.0000000000000000000000000001"));
        assertIndicatorEquals(precisionIndicator, doubleIndicator,
                PrecisionNum.valueOf("0.0000000000001"));
        assertIndicatorEquals(bigDecimalIndicator, doubleIndicator,
                PrecisionNum.valueOf("0.0000000000001"));

        // This helps for doing a memory snapshot
        //Thread.sleep(1000000);
    }

}
