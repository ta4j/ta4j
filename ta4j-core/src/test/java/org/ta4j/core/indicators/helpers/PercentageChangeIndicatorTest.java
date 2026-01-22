/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

public class PercentageChangeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private PercentageChangeIndicator priceChangePercentage;

    private BarSeries barSeries;

    public PercentageChangeIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        priceChangePercentage = new PercentageChangeIndicator(new ClosePriceIndicator(barSeries));
    }

    @Test
    public void indicatorShouldRetrieveBarPercentageChange() {
        assertThat(priceChangePercentage.getValue(0).isNaN()).isTrue();
        for (int i = 1; i < 10; i++) {
            Num previousBarClosePrice = barSeries.getBar(i - 1).getClosePrice();
            Num currentBarClosePrice = barSeries.getBar(i).getClosePrice();
            Num expectedPercentage = currentBarClosePrice.minus(previousBarClosePrice)
                    .dividedBy(previousBarClosePrice)
                    .multipliedBy(numFactory.hundred());
            assertNumEquals(expectedPercentage, priceChangePercentage.getValue(i));
        }
    }

    @Test
    public void indicatorShouldReturnNaNForZeroPreviousValue() {
        BarSeries seriesWithZero = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, 10, 20).build();
        PercentageChangeIndicator indicator = new PercentageChangeIndicator(new ClosePriceIndicator(seriesWithZero));

        assertThat(indicator.getValue(0).isNaN()).isTrue();
        assertThat(indicator.getValue(1).isNaN()).isTrue(); // Previous value is 0, division by zero
        assertNumEquals(numFactory.hundred(), indicator.getValue(2)); // (20 - 10) / 10 * 100 = 100%
    }

    @Test
    public void indicatorShouldWorkWithPreviousIndicator() {
        Instant endTime = Instant.now();
        Duration duration = Duration.ofSeconds(1);
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime)
                .openPrice(2.8)
                .highPrice(1)
                .lowPrice(1)
                .closePrice(2.9)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(1))
                .openPrice(2.5)
                .highPrice(2)
                .lowPrice(2)
                .closePrice(2.4)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(2))
                .openPrice(2.0)
                .highPrice(1)
                .lowPrice(1)
                .closePrice(3.0)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(3))
                .openPrice(3)
                .highPrice(3)
                .lowPrice(3)
                .closePrice(3.2)
                .add();
        series.barBuilder()
                .timePeriod(duration)
                .endTime(endTime.plusSeconds(4))
                .openPrice(4)
                .highPrice(4)
                .lowPrice(4)
                .closePrice(3.5)
                .add();

        OpenPriceIndicator openPrice = new OpenPriceIndicator(series);
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        PercentageChangeIndicator diff = new PercentageChangeIndicator(openPrice, closePrice);

        // index: 0: beginIndex <= index
        assertNumEquals(NaN.NaN, diff.getValue(0));

        // index: 1: currentOpenPrice = 2.5, previousClosePrice = 2.9
        // (2.5 - 2.9) / 2.9 * 100 = -13.79310344827586
        assertNumEquals(numOf(-13.79310344827586), diff.getValue(1));

        // index: 2: currentOpenPrice = 2.0, previousClosePrice = 2.4
        // (2.0 - 2.4) / 2.4 * 100 = -16.666666666666664 (precision difference expected)
        assertNumEquals(numOf(-16.66666666666667), diff.getValue(2));

        // index: 3: currentOpenPrice = 3.0, previousClosePrice = 3.0
        // (3.0 - 3.0) / 3.0 * 100 = 0.0
        assertNumEquals(numOf(0.0), diff.getValue(3));

        // index: 4: currentOpenPrice = 4.0, previousClosePrice = 3.2
        // (4.0 - 3.2) / 3.2 * 100 = 25.0
        assertNumEquals(numOf(25.0), diff.getValue(4));
    }

    @Test
    public void indicatorShouldWorkWithPercentageThreshold() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        FixedIndicator<Num> mockIndicator = new FixedIndicator<>(series, numOf(1000), numOf(1010), numOf(1020),
                numOf(1050), numOf(1060.5), numOf(1081.5), numOf(1102.5), numOf(1091.475), numOf(1113.525),
                numOf(1036.35), numOf(1067.4405));

        PercentageChangeIndicator indicator = new PercentageChangeIndicator(mockIndicator, null, numOf(5));

        assertNumEquals(NaN.NaN, indicator.getValue(0));
        assertNumEquals(numOf(1), indicator.getValue(1));
        assertNumEquals(numOf(2), indicator.getValue(2));
        assertNumEquals(numOf(5), indicator.getValue(3));
        assertNumEquals(numOf(1), indicator.getValue(4));
        assertNumEquals(numOf(3), indicator.getValue(5));
        assertNumEquals(numOf(5), indicator.getValue(6));
        assertNumEquals(numOf(-1), indicator.getValue(7));
        assertNumEquals(numOf(1), indicator.getValue(8));
        assertNumEquals(numOf(-6), indicator.getValue(9));
        assertNumEquals(numOf(3), indicator.getValue(10));
    }

    @Test
    public void indicatorShouldWorkWithPercentageThresholdAndPreviousIndicator() {
        // When threshold > 0, previousIndicator is ignored - threshold tracking uses
        // indicator itself
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        FixedIndicator<Num> mockIndicator = new FixedIndicator<>(series, numOf(1000), numOf(1010), numOf(1020),
                numOf(1050), numOf(1060.5), numOf(1081.5), numOf(1102.5), numOf(1091.475), numOf(1113.525),
                numOf(1036.35), numOf(1067.4405));

        // Even though we pass a previousIndicator, it should be ignored when threshold
        // > 0
        FixedIndicator<Num> dummyPrevious = new FixedIndicator<>(series, numOf(999), numOf(998), numOf(997));
        PercentageChangeIndicator indicator = new PercentageChangeIndicator(mockIndicator, dummyPrevious, numOf(5));

        // Should behave the same as without previousIndicator when threshold is set
        assertNumEquals(NaN.NaN, indicator.getValue(0));
        assertNumEquals(numOf(1), indicator.getValue(1));
        assertNumEquals(numOf(2), indicator.getValue(2));
        assertNumEquals(numOf(5), indicator.getValue(3));
        assertNumEquals(numOf(1), indicator.getValue(4));
        assertNumEquals(numOf(3), indicator.getValue(5));
        assertNumEquals(numOf(5), indicator.getValue(6));
        assertNumEquals(numOf(-1), indicator.getValue(7));
        assertNumEquals(numOf(1), indicator.getValue(8));
        assertNumEquals(numOf(-6), indicator.getValue(9));
        assertNumEquals(numOf(3), indicator.getValue(10));
    }

    @Test
    public void indicatorShouldMatchDifferencePercentageIndicatorBehavior() {
        // Test that PercentageChangeIndicator with threshold=0 matches
        // DifferencePercentageIndicator with threshold=0
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        FixedIndicator<Num> mockIndicator = new FixedIndicator<>(series, numOf(100), numOf(101), numOf(98.98),
                numOf(102.186952), numOf(91.9682568), numOf(100.5213046824), numOf(101.526517729224));

        PercentageChangeIndicator indicator = new PercentageChangeIndicator(mockIndicator);

        assertNumEquals(NaN.NaN, indicator.getValue(0));
        assertNumEquals(numOf(1), indicator.getValue(1));
        assertNumEquals(numOf(-2), indicator.getValue(2));
        assertNumEquals(numOf(3.24), indicator.getValue(3));
        assertNumEquals(numOf(-10), indicator.getValue(4));
        assertNumEquals(numOf(9.3), indicator.getValue(5));
        assertNumEquals(numOf(1), indicator.getValue(6));
    }

    @Test
    public void trimDecimalParameters() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 1.5, 2, 3, 5).build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        // Use constructor with threshold only (no previousIndicator) for proper
        // deserialization matching
        PercentageChangeIndicator indicator = new PercentageChangeIndicator(base, 1.5);

        ComponentDescriptor descriptor = indicator.toDescriptor();
        assertThat(descriptor.getParameters()).containsEntry("percentageThreshold", "1.5");

        String json = indicator.toJson();
        ComponentDescriptor parsed = ComponentSerialization.parse(json);
        assertThat(parsed.getParameters()).containsEntry("percentageThreshold", "1.5");

        Indicator<?> reconstructed = Indicator.fromJson(series, json);
        assertThat(reconstructed).isInstanceOf(PercentageChangeIndicator.class);
        assertThat(reconstructed.toDescriptor()).isEqualTo(descriptor);
    }
}
