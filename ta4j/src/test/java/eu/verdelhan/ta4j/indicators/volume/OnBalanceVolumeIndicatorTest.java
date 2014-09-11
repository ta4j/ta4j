/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014 Marc de Verdelhan & respective authors
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
package eu.verdelhan.ta4j.indicators.volume;

import eu.verdelhan.ta4j.indicators.volume.OnBalanceVolumeIndicator;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;

public class OnBalanceVolumeIndicatorTest {
    @Test
    public void getValue()
    {
        List<Tick> ticks = new ArrayList<Tick>();
        ticks.add(new MockTick(null, 0, 10, 0, 0, 0, 4, 0));
        ticks.add(new MockTick(null, 0, 5, 0, 0, 0, 2, 0));
        ticks.add(new MockTick(null, 0, 6, 0, 0, 0, 3, 0));
        ticks.add(new MockTick(null, 0, 7, 0, 0, 0, 8, 0));
        ticks.add(new MockTick(null, 0, 7, 0, 0, 0, 6, 0));
        ticks.add(new MockTick(null, 0, 6, 0, 0, 0, 10, 0));
        OnBalanceVolumeIndicator onBalance = new OnBalanceVolumeIndicator(new MockTimeSeries(ticks));
        
        assertThat(onBalance.getValue(0)).isEqualTo(0d);
        assertThat(onBalance.getValue(1)).isEqualTo(-2d);
        assertThat(onBalance.getValue(2)).isEqualTo(1d);
        assertThat(onBalance.getValue(3)).isEqualTo(9d);
        assertThat(onBalance.getValue(4)).isEqualTo(9d);
        assertThat(onBalance.getValue(5)).isEqualTo(-1d);

    }
}
