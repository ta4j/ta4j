/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2023 Ta4j Organization & respective
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
package org.ta4j.core.mocks;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.Num;

/**
 * A bar series with sample data. TODO: add Builder
 */
public class MockBarSeries extends BaseBarSeries {

    private static final long serialVersionUID = -1216549934945189371L;

    public MockBarSeries(Function<Number, Num> nf, double... data) {
        super(doublesToBars(nf, data));
    }

    public MockBarSeries(Function<Number, Num> nf, List<Double> data) {
        super(doublesToBars(nf, data));
    }

    public MockBarSeries(List<Bar> bars) {
        super(bars);
    }

    public MockBarSeries(Function<Number, Num> nf, double[] data, ZonedDateTime[] times) {
        super(doublesAndTimesToBars(nf, data, times));
    }

    public MockBarSeries(Function<Number, Num> nf, ZonedDateTime... dates) {
        super(timesToBars(nf, dates));
    }

    public MockBarSeries(Function<Number, Num> nf) {
        super(arbitraryBars(nf));
    }

    private static List<Bar> doublesToBars(Function<Number, Num> nf, List<Double> data) {
        ArrayList<Bar> bars = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            bars.add(new MockBar(ZonedDateTime.now().minusSeconds((data.size() + 1 - i)), data.get(i), nf));
        }
        return bars;
    }

    private static List<Bar> doublesToBars(Function<Number, Num> nf, double... data) {
        ArrayList<Bar> bars = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            bars.add(new MockBar(ZonedDateTime.now().minusSeconds((data.length + 1 - i)), data[i], nf));
        }
        return bars;
    }

    private static List<Bar> doublesAndTimesToBars(Function<Number, Num> nf, double[] data, ZonedDateTime[] times) {
        if (data.length != times.length) {
            throw new IllegalArgumentException();
        }
        ArrayList<Bar> bars = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            bars.add(new MockBar(times[i], data[i], nf));
        }
        return bars;
    }

    private static List<Bar> timesToBars(Function<Number, Num> nf, ZonedDateTime... dates) {
        ArrayList<Bar> bars = new ArrayList<>();
        int i = 1;
        for (ZonedDateTime date : dates) {
            bars.add(new MockBar(date, i++, nf));
        }
        return bars;
    }

    private static List<Bar> arbitraryBars(Function<Number, Num> nf) {
        ArrayList<Bar> bars = new ArrayList<>();
        for (double i = 0d; i < 5000; i++) {
            bars.add(new MockBar(ZonedDateTime.now().minusMinutes((long) (5001 - i)), i, i + 1, i + 2, i + 3, i + 4,
                    i + 5, (int) (i + 6), nf));
        }
        return bars;
    }
}
