/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.mocks;

import org.ta4j.core.Bar;
import org.ta4j.core.BaseTimeSeries;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;

/**
 * A time series with sample data.
 */
public class MockTimeSeries extends BaseTimeSeries {

    public MockTimeSeries(double... data) {
        super(doublesToBars(data));
    }

    public MockTimeSeries(List<Bar> bars) {
        super(bars);
    }

    public MockTimeSeries(double[] data, ZonedDateTime[] times) {
        super(doublesAndTimesToBars(data, times));
    }

    public MockTimeSeries(ZonedDateTime... dates) {
        super(timesToBars(dates));
    }

    public MockTimeSeries() {
        super(arbitraryBars());
    }

    private static List<Bar> doublesToBars(double... data) {
        ArrayList<Bar> bars = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            bars.add(new MockBar(ZonedDateTime.now().with(ChronoField.MILLI_OF_SECOND, i), data[i]));
        }
        return bars;
    }

    private static List<Bar> doublesAndTimesToBars(double[] data, ZonedDateTime[] times) {
        if (data.length != times.length) {
            throw new IllegalArgumentException();
        }
        ArrayList<Bar> bars = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            bars.add(new MockBar(times[i], data[i]));
        }
        return bars;
    }

    private static List<Bar> timesToBars(ZonedDateTime... dates) {
        ArrayList<Bar> bars = new ArrayList<>();
        int i = 1;
        for (ZonedDateTime date : dates) {
            bars.add(new MockBar(date, i++));
        }
        return bars;
    }

    private static List<Bar> arbitraryBars() {
        ArrayList<Bar> bars = new ArrayList<>();
        for (double i = 0d; i < 5000; i++) {
            bars.add(new MockBar(ZonedDateTime.now(), i, i + 1, i + 2, i + 3, i + 4, i + 5, (int) (i + 6)));
        }
        return bars;
    }
}
