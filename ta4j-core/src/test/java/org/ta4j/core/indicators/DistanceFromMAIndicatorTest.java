/*
 * SPDX-License-Identifier: MIT
 */

package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class DistanceFromMAIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries data;

    public DistanceFromMAIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        data = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 15, 20, 18, 17, 18, 15, 12, 10, 8, 5, 2)
                .build();
    }

    @Test
    public void DistanceFromMovingAverageTest() {
        SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
        DistanceFromMAIndicator distanceFromMAIndicator = new DistanceFromMAIndicator(data, sma);
        assertNumEquals(0.3333, distanceFromMAIndicator.getValue(2));
        assertNumEquals(0.01886792452830182, distanceFromMAIndicator.getValue(5));
        assertNumEquals(-0.1, distanceFromMAIndicator.getValue(6));
    }

    @Test
    public void explicitSeriesBindingReadsClosePricesFromExplicitSeries() {
        BarSeries explicitSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(20, 10)
                .withName("explicit")
                .build();
        BarSeries maSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(10, 30)
                .withName("ma")
                .build();
        SMAIndicator ma = new SMAIndicator(new ClosePriceIndicator(maSeries), 2);
        DistanceFromMAIndicator indicator = new DistanceFromMAIndicator(explicitSeries, ma);

        assertNumEquals(numOf(-1).dividedBy(numOf(2)), indicator.getValue(1));
        assertEquals("explicit", indicator.getBarSeries().getName());
    }

    @Test
    public void unavailableCrossSeriesReadIsDiagnosedWithoutClaimingMisalignment() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20).build();
        DistanceFromMAIndicator distance = new DistanceFromMAIndicator(data,
                new SMAIndicator(new ClosePriceIndicator(source), 2));
        TraceTestLogger logs = new TraceTestLogger();
        logs.open();
        try {
            assertThrows(IndexOutOfBoundsException.class, () -> distance.getValue(5));
            assertThrows(IndexOutOfBoundsException.class, () -> distance.getValue(5));
            assertThat(logs.getLogOutput()).containsOnlyOnce("cross-series source read at index 5 failed")
                    .doesNotContain("misaligned");
        } finally {
            logs.close();
        }
    }

    @Test
    public void laggingAndPrunedSourcesDoNotImplyMisalignment() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 20).build();
        DistanceFromMAIndicator distance = new DistanceFromMAIndicator(data,
                new SMAIndicator(new ClosePriceIndicator(source), 2));
        TraceTestLogger logs = new TraceTestLogger();
        logs.open();
        try {
            assertNumEquals(numOf(0), distance.getValue(1));
            source.barBuilder().closePrice(30).add();
            source.barBuilder().closePrice(40).add();
            source.barBuilder().closePrice(50).add();
            source.barBuilder().closePrice(60).add();
            source.setMaximumBarCount(4);
            assertNumEquals(numOf(-37).dividedBy(numOf(55)), distance.getValue(5));
            assertThat(logs.getLogOutput()).doesNotContain("cross-series source read");
        } finally {
            logs.close();
        }
    }

    @Test
    public void emptySourceCanBePopulatedAfterConstruction() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        TraceTestLogger logs = new TraceTestLogger();
        logs.open();
        try {
            DistanceFromMAIndicator distance = new DistanceFromMAIndicator(data,
                    new SMAIndicator(new ClosePriceIndicator(source), 2));
            source.barBuilder().closePrice(10).add();
            source.barBuilder().closePrice(20).add();
            assertNumEquals(numOf(0), distance.getValue(1));
            assertThat(logs.getLogOutput()).doesNotContain("cross-series source read");
        } finally {
            logs.close();
        }
    }

    @Test
    public void DistanceFromIllegalMovingAverage() {
        assertThrows(IllegalArgumentException.class, () -> {
            ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(data);
            new DistanceFromMAIndicator(data, closePriceIndicator);
        });
    }
}
