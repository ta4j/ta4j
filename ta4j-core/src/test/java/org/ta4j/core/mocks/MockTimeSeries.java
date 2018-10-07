package org.ta4j.core.mocks;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.num.Num;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A time series with sample data.
 * TODO: add Builder
 */
public class MockTimeSeries extends BaseTimeSeries {

    public MockTimeSeries(Function<Number, Num> nf, double... data) {
        super(doublesToBars(nf, data));
    }

    public MockTimeSeries(List<Bar> bars) {
        super(bars);
    }

    public MockTimeSeries(Function<Number, Num> nf, double[] data, ZonedDateTime[] times) {
        super(doublesAndTimesToBars(nf,data, times));
    }

    public MockTimeSeries(Function<Number, Num> nf, ZonedDateTime... dates) {
        super(timesToBars(nf, dates));
    }

    public MockTimeSeries(Function<Number, Num> nf) {
        super(arbitraryBars(nf));
    }

    private static List<Bar> doublesToBars(Function<Number, Num> nf,double... data) {
        ArrayList<Bar> bars = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            //bars.add(new MockBar(ZonedDateTime.now().with(ChronoField.MILLI_OF_SECOND, i), data[i]));
            bars.add(new MockBar(ZonedDateTime.now().minusSeconds((data.length+1-i)), data[i], nf));
        }
        return bars;
    }

    private static List<Bar> doublesAndTimesToBars(Function<Number, Num> nf,double[] data, ZonedDateTime[] times) {
        if (data.length != times.length) {
            throw new IllegalArgumentException();
        }
        ArrayList<Bar> bars = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            bars.add(new MockBar(times[i], data[i],nf));
        }
        return bars;
    }

    private static List<Bar> timesToBars(Function<Number, Num> nf, ZonedDateTime... dates) {
        ArrayList<Bar> bars = new ArrayList<>();
        int i = 1;
        for (ZonedDateTime date : dates) {
            bars.add(new MockBar(date, i++,nf));
        }
        return bars;
    }

    private static List<Bar> arbitraryBars(Function<Number, Num> nf) {
        ArrayList<Bar> bars = new ArrayList<>();
        for (double i = 0d; i < 5000; i++) {
            bars.add(new MockBar(ZonedDateTime.now().minusMinutes((long)(5001-i)), i, i + 1, i + 2, i + 3, i + 4, i + 5, (int) (i + 6),nf));
        }
        return bars;
    }
}
