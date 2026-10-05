/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class EntryEdgeIndicatorTest {

    private BarSeries series;

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().build();
        double[] closes = { 100, 102, 104, 103, 105, 107, 106, 108 };
        for (double close : closes) {
            series.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(1).add();
        }
    }

    @Test
    public void shouldMeasureLongSignalEdgeVersusBaseline() {
        Indicator<Boolean> signals = fixedSignals(0, 4);
        EntryEdgeIndicator subject = new EntryEdgeIndicator(signals, new ClosePriceIndicator(series), TradeType.BUY, 2,
                2);

        assertNumEquals(153.2710280373832, subject.getValue(7));
    }

    @Test
    public void shouldIgnoreSignalsWhoseForwardOutcomeHasNotMatured() {
        Indicator<Boolean> maturedSignals = fixedSignals(0, 4);
        Indicator<Boolean> signalsWithFutureOutcome = fixedSignals(0, 4, 6);
        EntryEdgeIndicator expected = new EntryEdgeIndicator(maturedSignals, new ClosePriceIndicator(series),
                TradeType.BUY, 2, 3);
        EntryEdgeIndicator subject = new EntryEdgeIndicator(signalsWithFutureOutcome, new ClosePriceIndicator(series),
                TradeType.BUY, 2, 3);

        assertNumEquals(expected.getValue(7), subject.getValue(7));
    }

    @Test
    public void shouldFlipReturnSignForShortSignals() {
        Indicator<Boolean> signals = fixedSignals(1, 3);
        EntryEdgeIndicator subject = new EntryEdgeIndicator(signals, new ClosePriceIndicator(series), TradeType.SELL, 2,
                2);

        assertThat(subject.getValue(7).isPositive()).isFalse();
    }

    @Test
    public void shouldNotRescanSparseSignalGapsForEachIndex() {
        BarSeries longSeries = new MockBarSeriesBuilder().build();
        for (int index = 0; index < 80; index++) {
            double close = 100 + index;
            longSeries.barBuilder().openPrice(close).highPrice(close).lowPrice(close).closePrice(close).volume(1).add();
        }
        CountingSignalIndicator signals = new CountingSignalIndicator(longSeries, 0);
        EntryEdgeIndicator subject = new EntryEdgeIndicator(signals, new ClosePriceIndicator(longSeries), TradeType.BUY,
                1, 1);

        for (int index = 1; index <= longSeries.getEndIndex(); index++) {
            subject.getValue(index);
        }

        assertThat(signals.getReads()).isLessThan(200);
    }

    private Indicator<Boolean> fixedSignals(int... indexes) {
        return new CachedIndicator<>(series) {
            @Override
            protected Boolean calculate(int index) {
                for (int candidate : indexes) {
                    if (candidate == index) {
                        return Boolean.TRUE;
                    }
                }
                return Boolean.FALSE;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
    }

    private static final class CountingSignalIndicator extends AbstractIndicator<Boolean> {

        private final int signalIndex;
        private int reads;

        private CountingSignalIndicator(BarSeries series, int signalIndex) {
            super(series);
            this.signalIndex = signalIndex;
        }

        @Override
        public Boolean getValue(int index) {
            reads++;
            return index == signalIndex;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        private int getReads() {
            return reads;
        }
    }
}
