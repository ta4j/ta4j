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
package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.indicators.simple.ConstantIndicator;
import eu.verdelhan.ta4j.indicators.simple.SimpleMultiplierIndicator;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class SimpleMultiplierIndicatorTest {
    private ConstantIndicator<Double> constantIndicator;
    private SimpleMultiplierIndicator simpleMultiplier;

    @Before
    public void setUp() {
        constantIndicator = new ConstantIndicator<Double>(5d);
        simpleMultiplier = new SimpleMultiplierIndicator(constantIndicator, 5d);
    }

    @Test
    public void constantIndicator() {
        assertThat(simpleMultiplier.getValue(10)).isEqualTo(25d);
        assertThat(simpleMultiplier.getValue(1)).isEqualTo(25d);
        assertThat(simpleMultiplier.getValue(0)).isEqualTo(25d);
        assertThat(simpleMultiplier.getValue(30)).isEqualTo(25d);
    }
}
