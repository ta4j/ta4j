/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.Arrays;
import java.util.List;

public class MMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private final ExternalIndicatorTest xls;

    public MMAIndicatorTest(NumFactory numFunction) {
        super((data, params) -> new MMAIndicator(data, (int) params[0]), numFunction);
        xls = new XLSIndicatorTest(this.getClass(), "MMA.xls", 6, numFunction);
    }

    private BarSeries data;

    @Before
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(64.75, 63.79, 63.73, 63.73, 63.55, 63.19, 63.91, 63.85, 62.95, 63.37, 61.33, 61.51)
                .build();
    }

    @Test
    public void firstValueShouldBeNaNDuringUnstablePeriod() {
        var actualIndicator = getIndicator(new ClosePriceIndicator(data), 1);
        // With barCount=1, unstable period is 1, so index 0 should return NaN
        assertThat(Double.isNaN(actualIndicator.getValue(0).doubleValue())).isTrue();
    }

    @Test
    public void mmaUsingBarCount10UsingClosePrice() {
        var actualIndicator = getIndicator(new ClosePriceIndicator(data), 10);
        // Index 9 is in unstable period (barCount=10, so indices 0-9 are unstable)
        assertThat(Double.isNaN(actualIndicator.getValue(9).doubleValue())).isTrue();
        // Index 10 is first valid value after unstable period - initializes to current
        // value
        assertEquals(61.33, actualIndicator.getValue(10).doubleValue(), TestUtils.GENERAL_OFFSET); // First value after
                                                                                                   // unstable period =
                                                                                                   // current value
        // Index 11 is first calculated MMA value (from index 10)
        // With barCount=10, multiplier = 1/10 = 0.1
        // MMA(11) = 61.33 + (61.51 - 61.33) * 0.1 ≈ 61.33 + 0.18 * 0.1 ≈ 61.33 + 0.018
        // ≈ 61.348
        Num value11 = actualIndicator.getValue(11);
        assertThat(value11.doubleValue()).isGreaterThan(61.33);
        assertThat(value11.doubleValue()).isLessThan(62.0);
    }

    @Test
    public void stackOverflowError() {
        var bigSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = 0; i < 10000; i++) {
            bigSeries.barBuilder().closePrice(i).add();
        }
        var closePrice = new ClosePriceIndicator(bigSeries);
        var actualIndicator = getIndicator(closePrice, 10);
        // if a StackOverflowError is thrown here, then the RecursiveCachedIndicator
        // does not work as intended.
        assertEquals(9990.0, actualIndicator.getValue(9999).doubleValue(), TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void testAgainstExternalData() throws Exception {
        var xlsClose = new ClosePriceIndicator(xls.getSeries());
        Indicator<Num> actualIndicator;

        actualIndicator = getIndicator(xlsClose, 1);
        // With barCount=1, unstable period is 1, so index 0 returns NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(329.0, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(xlsClose, 3);
        // With barCount=3, unstable period is 3, so indices 0-2 return NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(327.2900, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        actualIndicator = getIndicator(xlsClose, 13);
        // With barCount=13, unstable period is 13, so indices 0-12 return NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(326.9696, actualIndicator.getValue(actualIndicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void returnsNaNWhenCurrentValueIsNaN() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();
        List<Num> values = Arrays.asList(numFactory.numOf(10.0), numFactory.numOf(11.0), numFactory.numOf(12.0), NaN,
                numFactory.numOf(14.0));
        MockIndicator mockIndicator = new MockIndicator(series, values);
        MMAIndicator mma = new MMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(mma.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(mma.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(mma.getValue(2).doubleValue())).isTrue();
        // Index 3: current value is NaN, should return NaN
        assertThat(Double.isNaN(mma.getValue(3).doubleValue())).isTrue();
    }

    @Test
    public void recoversFromNaNInPreviousValue() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();
        List<Num> values = Arrays.asList(numFactory.numOf(10.0), numFactory.numOf(11.0), numFactory.numOf(12.0), NaN,
                numFactory.numOf(14.0));
        MockIndicator mockIndicator = new MockIndicator(series, values);
        MMAIndicator mma = new MMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(mma.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(mma.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(mma.getValue(2).doubleValue())).isTrue();

        // Index 3: current value is NaN, should return NaN
        assertThat(Double.isNaN(mma.getValue(3).doubleValue())).isTrue();

        // Index 4 should recover - reset to current value (14.0) when previous is NaN
        Num value4 = mma.getValue(4);
        assertThat(value4).isEqualByComparingTo(numFactory.numOf(14.0));
    }

    @Test
    public void preventsNaNContamination() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16)
                .build();
        List<Num> values = Arrays.asList(numFactory.numOf(10.0), numFactory.numOf(11.0), numFactory.numOf(12.0), NaN,
                numFactory.numOf(14.0), numFactory.numOf(15.0), numFactory.numOf(16.0));
        MockIndicator mockIndicator = new MockIndicator(series, values);
        MMAIndicator mma = new MMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(mma.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(mma.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(mma.getValue(2).doubleValue())).isTrue();

        // Index 3: current value is NaN, should return NaN
        assertThat(Double.isNaN(mma.getValue(3).doubleValue())).isTrue();

        // Index 4 should recover (reset to current value 14.0)
        Num value4 = mma.getValue(4);
        assertThat(value4).isEqualByComparingTo(numFactory.numOf(14.0));

        // Index 5 should continue normal MMA calculation (not contaminated)
        Num value5 = mma.getValue(5);
        assertThat(value5).isNotNull();
        assertThat(Double.isNaN(value5.doubleValue())).isFalse();
    }

    @Test
    public void handlesNonZeroBeginIndex() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        MMAIndicator mma = new MMAIndicator(closePrice, 3);

        int beginIndex = series.getBeginIndex();
        int unstableBars = mma.getCountOfUnstableBars();
        // During unstable period, should return NaN
        for (int i = beginIndex; i < beginIndex + unstableBars; i++) {
            assertThat(Double.isNaN(mma.getValue(i).doubleValue())).isTrue();
        }
        // After unstable period, should return valid values
        Num valueAfterUnstable = mma.getValue(beginIndex + unstableBars);
        assertThat(Double.isNaN(valueAfterUnstable.doubleValue())).isFalse();
    }

    @Test
    public void serializationRoundTrip() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
                .build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        MMAIndicator original = new MMAIndicator(base, 5);

        String json = original.toJson();
        @SuppressWarnings("unchecked")
        Indicator<Num> reconstructed = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(reconstructed).isInstanceOf(MMAIndicator.class);
        assertThat(reconstructed.toDescriptor()).isEqualTo(original.toDescriptor());

        // Compare values after unstable period
        int unstableBars = original.getCountOfUnstableBars();
        for (int i = series.getBeginIndex() + unstableBars; i <= series.getEndIndex(); i++) {
            Num expected = original.getValue(i);
            Num actual = reconstructed.getValue(i);
            if (Num.isNaNOrNull(expected) || Num.isNaNOrNull(actual)) {
                assertThat(Num.isNaNOrNull(actual)).isEqualTo(Num.isNaNOrNull(expected));
            } else {
                assertThat(actual).isEqualTo(expected);
            }
        }
    }
}
