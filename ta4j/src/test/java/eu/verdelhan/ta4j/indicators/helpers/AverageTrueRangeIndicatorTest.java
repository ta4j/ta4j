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
package eu.verdelhan.ta4j.indicators.helpers;

import static eu.verdelhan.ta4j.TATestsUtils.assertDecimalEquals;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class AverageTrueRangeIndicatorTest {

    @Test
    public void getValue() {
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(0, 12, 15, 8));
        ticks.add(new MockTick(0, 8, 11, 6));
        ticks.add(new MockTick(0, 15, 17, 14));
        ticks.add(new MockTick(0, 15, 17, 14));
        ticks.add(new MockTick(0, 0, 0, 2));
        AverageTrueRangeIndicator atr = new AverageTrueRangeIndicator(new MockTimeSeries(ticks), 3);
        
        assertDecimalEquals(atr.getValue(0), 1);
        assertDecimalEquals(atr.getValue(1), 8d/3);
        assertDecimalEquals(atr.getValue(2), 8d/3 * 2d/3 + 3);
        assertDecimalEquals(atr.getValue(3), (8d/3 * 2d/3 + 3) * 2d/3 + 1);
        assertDecimalEquals(atr.getValue(4), ((8d/3 * 2d/3 + 3) * 2d/3 + 1) * 2d/3 + 15d/3);
    }
}
