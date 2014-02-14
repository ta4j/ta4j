package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.indicators.helpers.StandardDeviationIndicator;
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
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 0, 9 });
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

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		StandardDeviationIndicator quoteSDV = new StandardDeviationIndicator(new ClosePriceIndicator(data), 3);
		quoteSDV.getValue(13);
	}
}
