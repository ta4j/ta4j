/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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
package eu.verdelhan.ta4j.mocks;

import eu.verdelhan.ta4j.BaseTimeSeries;
import eu.verdelhan.ta4j.Tick;
import java.util.ArrayList;
import java.util.List;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;

/**
 * A time series with sample data.
 */
public class MockTimeSeries extends BaseTimeSeries {

    public MockTimeSeries(double... data) {
        super(doublesToTicks(data));
    }

    public MockTimeSeries(List<Tick> ticks) {
        super(ticks);
    }

    public MockTimeSeries(double[] data, ZonedDateTime[] times) {
        super(doublesAndTimesToTicks(data, times));
    }

    public MockTimeSeries(ZonedDateTime... dates) {
        super(timesToTicks(dates));
    }

    public MockTimeSeries() {
        super(arbitraryTicks());
    }

    private static List<Tick> doublesToTicks(double... data) {
        ArrayList<Tick> ticks = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            ticks.add(new MockTick(ZonedDateTime.now().with(ChronoField.MILLI_OF_SECOND, i), data[i]));
        }
        return ticks;
    }

    private static List<Tick> doublesAndTimesToTicks(double[] data, ZonedDateTime[] times) {
        if (data.length != times.length) {
            throw new IllegalArgumentException();
        }
        ArrayList<Tick> ticks = new ArrayList<>();
        for (int i = 0; i < data.length; i++) {
            ticks.add(new MockTick(times[i], data[i]));
        }
        return ticks;
    }

    private static List<Tick> timesToTicks(ZonedDateTime... dates) {
        ArrayList<Tick> ticks = new ArrayList<>();
        int i = 1;
        for (ZonedDateTime date : dates) {
            ticks.add(new MockTick(date, i++));
        }
        return ticks;
    }

    private static List<Tick> arbitraryTicks() {
        ArrayList<Tick> ticks = new ArrayList<>();
        for (double i = 0d; i < 10; i++) {
            ticks.add(new MockTick(ZonedDateTime.now(), i, i + 1, i + 2, i + 3, i + 4, i + 5, (int) (i + 6)));
        }
        return ticks;
    }
}
