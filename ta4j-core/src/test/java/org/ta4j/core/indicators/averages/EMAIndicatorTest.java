/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.averages;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.ExternalIndicatorTest;
import org.ta4j.core.Indicator;
import org.ta4j.core.TestUtils;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.XLSIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.Arrays;
import java.util.List;

public class EMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private final ExternalIndicatorTest xls;

    public EMAIndicatorTest(NumFactory numFactory) {
        super((data, params) -> new EMAIndicator(data, (int) params[0]), numFactory);
        xls = new XLSIndicatorTest(this.getClass(), "EMA.xls", 6, numFactory);
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
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 1);
        // With barCount=1, unstable period is 1, so index 0 should return NaN
        assertThat(Double.isNaN(indicator.getValue(0).doubleValue())).isTrue();
    }

    @Test
    public void usingBarCount10UsingClosePrice() {
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(data), 10);
        // Index 9 is in unstable period (barCount=10, so indices 0-9 are unstable)
        assertThat(Double.isNaN(indicator.getValue(9).doubleValue())).isTrue();
        // Index 10 is first valid value after unstable period - initializes to current
        // value
        assertNumEquals(61.33, indicator.getValue(10)); // First value after unstable period = current value
        // Index 11 is first calculated EMA value (from index 10)
        // With barCount=10, multiplier = 2/(10+1) = 2/11 ≈ 0.1818
        // EMA(11) = 61.33 + (61.51 - 61.33) * 0.1818 ≈ 61.33 + 0.18 * 0.1818 ≈ 61.33 +
        // 0.0327 ≈ 61.3627
        Num value11 = indicator.getValue(11);
        assertThat(value11.doubleValue()).isGreaterThan(61.33);
        assertThat(value11.doubleValue()).isLessThan(62.0);
    }

    @Test
    public void stackOverflowError() throws Exception {
        var bigSeries = new MockBarSeriesBuilder().build();
        for (int i = 0; i < 10000; i++) {
            bigSeries.barBuilder().closePrice(i).add();
        }
        Indicator<Num> indicator = getIndicator(new ClosePriceIndicator(bigSeries), 10);
        // if a StackOverflowError is thrown here, then the RecursiveCachedIndicator
        // does not work as intended.
        assertNumEquals(9994.5, indicator.getValue(9999));
    }

    @Test
    public void externalData() throws Exception {
        BarSeries xlsSeries = xls.getSeries();
        Indicator<Num> closePrice = new ClosePriceIndicator(xlsSeries);
        Indicator<Num> indicator;

        indicator = getIndicator(closePrice, 1);
        // With barCount=1, unstable period is 1, so index 0 returns NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(329.0, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(closePrice, 3);
        // With barCount=3, unstable period is 3, so indices 0-2 return NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(327.7748, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);

        indicator = getIndicator(closePrice, 13);
        // With barCount=13, unstable period is 13, so indices 0-12 return NaN
        // Values after unstable period will differ initially but should converge. Only
        // check end value.
        assertEquals(327.4076, indicator.getValue(indicator.getBarSeries().getEndIndex()).doubleValue(),
                TestUtils.GENERAL_OFFSET);
    }

    @Test
    public void returnsNaNWhenCurrentValueIsNaN() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();
        List<Num> values = Arrays.asList(numFactory.numOf(10.0), numFactory.numOf(11.0), numFactory.numOf(12.0), NaN,
                numFactory.numOf(14.0));
        MockIndicator mockIndicator = new MockIndicator(series, values);
        EMAIndicator ema = new EMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(2).doubleValue())).isTrue();
        // Index 3: current value is NaN, should return NaN
        assertThat(Double.isNaN(ema.getValue(3).doubleValue())).isTrue();
    }

    @Test
    public void recoversFromNaNInPreviousValue() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();
        List<Num> values = Arrays.asList(numFactory.numOf(10.0), numFactory.numOf(11.0), numFactory.numOf(12.0), NaN,
                numFactory.numOf(14.0));
        MockIndicator mockIndicator = new MockIndicator(series, values);
        EMAIndicator ema = new EMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(2).doubleValue())).isTrue();

        // Index 3: current value is NaN, should return NaN
        assertThat(Double.isNaN(ema.getValue(3).doubleValue())).isTrue();

        // Index 4 should recover - reset to current value (14.0) when previous is NaN
        Num value4 = ema.getValue(4);
        assertThat(value4).isEqualByComparingTo(numFactory.numOf(14.0));
    }

    @Test
    public void returnsNaNWhenFirstValueIsNaN() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13).build();
        List<Num> values = Arrays.asList(NaN, numFactory.numOf(11.0), numFactory.numOf(12.0), numFactory.numOf(13.0));
        MockIndicator mockIndicator = new MockIndicator(series, values);
        EMAIndicator ema = new EMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(2).doubleValue())).isTrue();
        // Index 3: first valid value after unstable period, but previous was NaN, so
        // should return current value
        Num value3 = ema.getValue(3);
        assertThat(value3).isEqualByComparingTo(numFactory.numOf(13.0));
    }

    @Test
    public void handlesNonZeroBeginIndex() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema = new EMAIndicator(closePrice, 3);

        int beginIndex = series.getBeginIndex();
        int unstableBars = ema.getCountOfUnstableBars();
        // During unstable period, should return NaN
        for (int i = beginIndex; i < beginIndex + unstableBars; i++) {
            assertThat(Double.isNaN(ema.getValue(i).doubleValue())).isTrue();
        }
        // After unstable period, should return valid values
        Num valueAfterUnstable = ema.getValue(beginIndex + unstableBars);
        assertThat(Double.isNaN(valueAfterUnstable.doubleValue())).isFalse();
    }

    @Test
    public void handlesNaNInInputValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13).build();
        List<Num> values = Arrays.asList(numFactory.numOf(10.0), numFactory.numOf(11.0), numFactory.numOf(12.0), NaN);
        MockIndicator mockIndicator = new MockIndicator(series, values);
        EMAIndicator ema = new EMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(2).doubleValue())).isTrue();
        // Index 3: current value is NaN, should return NaN
        assertThat(Double.isNaN(ema.getValue(3).doubleValue())).isTrue();
    }

    @Test
    public void preventsNaNContamination() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 11, 12, 13, 14, 15, 16)
                .build();
        List<Num> values = Arrays.asList(numFactory.numOf(10.0), numFactory.numOf(11.0), numFactory.numOf(12.0), NaN,
                numFactory.numOf(14.0), numFactory.numOf(15.0), numFactory.numOf(16.0));
        MockIndicator mockIndicator = new MockIndicator(series, values);
        EMAIndicator ema = new EMAIndicator(mockIndicator, 3);

        // Index 0, 1, 2 are in unstable period (barCount=3)
        assertThat(Double.isNaN(ema.getValue(0).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(1).doubleValue())).isTrue();
        assertThat(Double.isNaN(ema.getValue(2).doubleValue())).isTrue();

        // Index 3: current value is NaN, should return NaN
        assertThat(Double.isNaN(ema.getValue(3).doubleValue())).isTrue();

        // Index 4 should recover (reset to current value 14.0)
        Num value4 = ema.getValue(4);
        assertThat(value4).isEqualByComparingTo(numFactory.numOf(14.0));

        // Index 5 should continue normal EMA calculation (not contaminated)
        Num value5 = ema.getValue(5);
        assertThat(value5).isNotNull();
        assertThat(Double.isNaN(value5.doubleValue())).isFalse();
        // Should be a valid EMA calculation from 14.0
        assertThat(value5.doubleValue()).isGreaterThan(14.0);
    }

    @Test
    public void recoversWhenChainedAfterRsiIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 3, 4, 5).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        RSIIndicator rsi = new RSIIndicator(closePrice, 3);
        EMAIndicator emaOnRsi = new EMAIndicator(rsi, 2);

        // EMA unstable period is shorter than RSI, so NaN should propagate through the
        // RSI unstable window
        assertThat(Num.isNaNOrNull(emaOnRsi.getValue(0))).isTrue();
        assertThat(Num.isNaNOrNull(emaOnRsi.getValue(1))).isTrue();
        assertThat(Num.isNaNOrNull(emaOnRsi.getValue(2))).isTrue();

        Num firstRsiValue = rsi.getValue(3);
        assertThat(firstRsiValue).isEqualByComparingTo(numFactory.hundred());
        // Recovery: EMA resets to the first stable RSI value instead of remaining NaN
        assertThat(emaOnRsi.getValue(3)).isEqualByComparingTo(firstRsiValue);

        Num multiplier = numFactory.two().dividedBy(numFactory.numOf(emaOnRsi.getBarCount() + 1));

        Num expectedIndex4 = firstRsiValue.plus(rsi.getValue(4).minus(firstRsiValue).multipliedBy(multiplier));
        assertThat(emaOnRsi.getValue(4)).isEqualByComparingTo(expectedIndex4);

        Num expectedIndex5 = expectedIndex4.plus(rsi.getValue(5).minus(expectedIndex4).multipliedBy(multiplier));
        assertThat(emaOnRsi.getValue(5)).isEqualByComparingTo(expectedIndex5);
    }

    @Test
    public void serializationRoundTrip() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
                .build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        EMAIndicator original = new EMAIndicator(base, 5);

        String json = original.toJson();
        @SuppressWarnings("unchecked")
        Indicator<Num> reconstructed = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(reconstructed).isInstanceOf(EMAIndicator.class);
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
