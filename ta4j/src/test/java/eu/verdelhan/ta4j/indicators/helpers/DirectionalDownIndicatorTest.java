/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan
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

import eu.verdelhan.ta4j.indicators.helpers.DirectionalDownIndicator;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;


public class DirectionalDownIndicatorTest {
    
    @Test
    public void averageDirectionalMovement()
    {
        
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(0, 0, 13, 7));
        ticks.add(new MockTick(0, 0, 11, 5));
        ticks.add(new MockTick(0, 0, 15, 3));
        ticks.add(new MockTick(0, 0, 14, 2));
        ticks.add(new MockTick(0, 0, 13, 0.2));
        
        MockTimeSeries series = new MockTimeSeries(ticks);
        DirectionalDownIndicator ddown = new DirectionalDownIndicator(series, 3);
        assertThat(ddown.getValue(0)).isEqualTo((double) 1d);
        assertThat(ddown.getValue(1)).isEqualTo((double) (1d * 2d/3 +2d / 3) / (2d/3 + 11d/3));
        assertThat(ddown.getValue(2)).isEqualTo((double) ((1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0) / (((2d/3 + 11d/3) * 2d/3) + 15d/3));
        assertThat(ddown.getValue(3)).isEqualTo((double) (((1d * 2d/3 +2d / 3) * 2d/3 + 1d/3 * 0) * 2d/3 + 1d/3) / (((((2d/3 + 11d/3) * 2d/3) + 15d/3) * 2d/3) + 14d/3));
        assertThat(ddown.getValue(4)).isEqualTo((double) ((((1d * 2d/3 + 2d / 3) * 2d/3 + 1d/3 * 0) * 2d/3 + 1d/3) * 2d/3 + 1.8 * 1d/3) / (((((((2d/3 + 11d/3) * 2d/3) + 15d/3) * 2d/3) + 14d/3) * 2d/3) + 13d/3));
    }
}
