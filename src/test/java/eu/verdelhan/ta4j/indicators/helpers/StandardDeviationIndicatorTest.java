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

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class StandardDeviationIndicatorTest {
	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 0, 9);
	}

	@Test
	public void testStandardDeviationUsingTimeFrame4UsingClosePrice() throws Exception {
		StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 4);

		assertThat(sdv.getValue(0)).isEqualTo(0d);
		assertThat(sdv.getValue(1)).isEqualTo(Math.sqrt(0.5));
		assertThat(sdv.getValue(2)).isEqualTo(Math.sqrt(2.0));
		assertThat(sdv.getValue(3)).isEqualTo(Math.sqrt(5.0));
		assertThat(sdv.getValue(4)).isEqualTo(Math.sqrt(2.0));
		assertThat(sdv.getValue(5)).isEqualTo(1);
		assertThat(sdv.getValue(6)).isEqualTo(Math.sqrt(2.0));
		assertThat(sdv.getValue(7)).isEqualTo(Math.sqrt(2.0));
		assertThat(sdv.getValue(8)).isEqualTo(Math.sqrt(2.0));
		assertThat(sdv.getValue(9)).isEqualTo(Math.sqrt(14.0));
		assertThat(sdv.getValue(10)).isEqualTo(Math.sqrt(42.0));

	}

	@Test
	public void testFirstValueShouldBeZero() throws Exception {
		StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 4);

		assertThat(sdv.getValue(0)).isEqualTo(0);
	}

	@Test
	public void testStandardDeviationValueIndicatorValueWhenTimeFraseIs1ShouldBeZero() {
		StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 1);
		assertThat(sdv.getValue(3)).isEqualTo(0d);
		assertThat(sdv.getValue(8)).isEqualTo(0d);
	}

	@Test
	public void testStandardDeviationUsingTimeFrame2UsingClosePrice() throws Exception {
		StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 2);

		assertThat(sdv.getValue(0)).isEqualTo(0d);
		assertThat(sdv.getValue(1)).isEqualTo(Math.sqrt(0.5));
		assertThat(sdv.getValue(2)).isEqualTo(Math.sqrt(0.5));
		assertThat(sdv.getValue(3)).isEqualTo(Math.sqrt(0.5));
		assertThat(sdv.getValue(9)).isEqualTo(Math.sqrt(4.5));
		assertThat(sdv.getValue(10)).isEqualTo(Math.sqrt(40.5));
	}
}
