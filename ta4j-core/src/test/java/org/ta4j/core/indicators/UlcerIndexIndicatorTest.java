/*******************************************************************************
 *   The MIT License (MIT)
 *
 *   Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2018 Ta4j Organization 
 *   & respective authors (see AUTHORS)
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a copy of
 *   this software and associated documentation files (the "Software"), to deal in
 *   the Software without restriction, including without limitation the rights to
 *   use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *   the Software, and to permit persons to whom the Software is furnished to do so,
 *   subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in all
 *   copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *   FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *   COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *   IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *   CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *******************************************************************************/
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Indicator;
import org.ta4j.core.TimeSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;

import java.util.function.Function;

import static org.ta4j.core.TestUtils.assertNumEquals;

public class UlcerIndexIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num>{
    
    private TimeSeries ibmData;

    public UlcerIndexIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        ibmData = new MockTimeSeries(numFunction,
                194.75, 195.00, 195.10, 194.46, 190.60,
                188.86, 185.47, 184.46, 182.31, 185.22,
                184.00, 182.87, 187.45, 194.51, 191.63,
                190.02, 189.53, 190.27, 193.13, 195.55,
                195.84, 195.15, 194.35, 193.62, 197.68,
                197.91, 199.08, 199.03, 198.42, 199.29,
                199.01, 198.29, 198.40, 200.84, 201.22,
                200.50, 198.65, 197.25, 195.70, 197.77,
                195.69, 194.87, 195.08
        );
    }

    @Test
    public void ulcerIndexUsingBarCount14UsingIBMData() {
        UlcerIndexIndicator ulcer = new UlcerIndexIndicator(new ClosePriceIndicator(ibmData), 14);

        assertNumEquals(0, ulcer.getValue(0));
        
        // From: http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators:ulcer_index
        assertNumEquals(1.3047, ulcer.getValue(26));
        assertNumEquals(1.3022, ulcer.getValue(27));
        assertNumEquals(1.2156, ulcer.getValue(28));
        assertNumEquals(0.9967, ulcer.getValue(29));
        assertNumEquals(0.7257, ulcer.getValue(30));
        assertNumEquals(0.453, ulcer.getValue(31));
        assertNumEquals(0.4284, ulcer.getValue(32));
        assertNumEquals(0.4284, ulcer.getValue(33));
        assertNumEquals(0.4284, ulcer.getValue(34));
        assertNumEquals(0.4287, ulcer.getValue(35));
        assertNumEquals(0.5089, ulcer.getValue(36));
        assertNumEquals(0.6673, ulcer.getValue(37));
        assertNumEquals(0.9914, ulcer.getValue(38));
        assertNumEquals(1.0921, ulcer.getValue(39));
        assertNumEquals(1.3161, ulcer.getValue(40));
        assertNumEquals(1.5632, ulcer.getValue(41));
        assertNumEquals(1.7609, ulcer.getValue(42));
    }
}
