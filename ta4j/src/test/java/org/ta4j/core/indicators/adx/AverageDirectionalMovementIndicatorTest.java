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
package org.ta4j.core.indicators.adx;

import static org.ta4j.core.TATestsUtils.assertDecimalEquals;
import org.ta4j.core.Tick;
import org.ta4j.core.mocks.MockTick;
import org.ta4j.core.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class AverageDirectionalMovementIndicatorTest {

    @Test
    public void getValue()
    {
        List<Tick> ticks = new ArrayList<Tick>();
        
        ticks.add(new MockTick(0, 0, 10, 2));
        ticks.add(new MockTick(0, 0, 12, 2));
        ticks.add(new MockTick(0, 0, 15, 2));
        MockTimeSeries series = new MockTimeSeries(ticks);
        AverageDirectionalMovementIndicator adm = new AverageDirectionalMovementIndicator(series, 3);

        assertDecimalEquals(adm.getValue(0), 1);
        double dup = (2d/3 + 2d/3) / (2d/3 + 12d/3);
        double ddown = (2d/3) /(2d/3 + 12d/3);
        double firstdm = (dup - ddown) / (dup + ddown) * 100;
        assertDecimalEquals(adm.getValue(1), 2d/3 + firstdm/3);
        dup = ((2d/3 + 2d/3) * 2d/3 + 1) / ((2d/3 + 12d/3) * 2d/3 + 15d/3);
        ddown = (4d/9) / ((2d/3 + 12d/3) * 2d/3 + 15d/3);
        double secondDm = (dup - ddown) / (dup + ddown) * 100;
        assertDecimalEquals(adm.getValue(2), (2d/3 + firstdm/3) * 2d/3 + secondDm/3);
    }
}
