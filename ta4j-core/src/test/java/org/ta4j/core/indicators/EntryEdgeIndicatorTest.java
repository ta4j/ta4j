/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class EntryEdgeIndicatorTest {

    private BarSeries series;

    @BeforeEach
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

    @Test
    public void shouldExpireOldSignalsAndRecalculateFormingBar() {
        EntryEdgeIndicator subject = new EntryEdgeIndicator(fixedSignals(0, 2, 4), new ClosePriceIndicator(series),
                TradeType.BUY, 2, 2);
        for (int index = 2; index <= 7; index++) {
            subject.getValue(index);
        }

        assertNumEquals(1.3479511143062552, subject.getValue(7));
        series.addPrice(110);
        assertNumEquals(-92.10999281092738, subject.getValue(7));
    }

    @Test
    public void shouldMatchScanWhenMaturedSignalOutcomeBecomesNonFinite() {
        for (NumFactory factory : List.of(DecimalNumFactory.getInstance(), DoubleNumFactory.getInstance())) {
            // Exercise both a cancellation rebase and a successful rolling update.
            for (double lastClose : new double[] { 102.72, 108 }) {
                for (Num invalidPrice : List.of(NaN.NaN, factory.numOf(Double.MAX_VALUE))) {
                    setPrices(factory, 100, 102, 104, 103, 105, 107, 106, lastClose);
                    EntryEdgeIndicator subject = new EntryEdgeIndicator(fixedSignals(0, 5), series, 2, 2);
                    for (int index = 2; index < 7; index++) {
                        subject.getValue(index);
                    }
                    assertNumEquals(152.38095238095238, subject.getValue(7));

                    series.addPrice(invalidPrice);
                    assertThat(subject.getValue(7).isNaN()).isTrue();

                    series.addPrice(108);
                    assertNumEquals(152.38095238095238, subject.getValue(7));
                }
            }
        }
    }

    @Test
    public void shouldPreserveCachedSignalExclusionWhenOutcomeBecomesFinite() {
        for (NumFactory factory : List.of(DecimalNumFactory.getInstance(), DoubleNumFactory.getInstance())) {
            setPrices(factory, 100, 102, 104, 103, 105, 107, 105, 108);
            series.addPrice(NaN.NaN);
            EntryEdgeIndicator subject = new EntryEdgeIndicator(fixedSignals(0, 5), series, 2, 2);
            for (int index = 2; index < 7; index++) {
                subject.getValue(index);
            }
            assertNumEquals(400.0, subject.getValue(7));

            series.addPrice(108);
            assertNumEquals(353.2710280373832, subject.getValue(7));
        }
    }

    @Test
    public void shouldSkipNonFiniteBaselineReturnWithoutSignal() {
        EntryEdgeIndicator subject = new EntryEdgeIndicator(fixedSignals(0), series, 2, 2);
        for (int index = 2; index <= 7; index++) {
            subject.getValue(index);
        }

        series.addPrice(NaN.NaN);
        assertNumEquals(304.76190476190476, subject.getValue(7));
    }

    @Test
    public void shouldRecalculateAfterHistoricalPriceReplacement() {
        EntryEdgeIndicator subject = new EntryEdgeIndicator(fixedSignals(0, 2, 4), new ClosePriceIndicator(series),
                TradeType.BUY, 2, 2);
        for (int index = 2; index <= 7; index++) {
            subject.getValue(index);
        }
        Bar old = series.getBar(4);
        ((BaseBarSeries) series).replaceBar(4,
                series.barBuilder()
                        .timePeriod(old.getTimePeriod())
                        .endTime(old.getEndTime())
                        .openPrice(110)
                        .highPrice(110)
                        .lowPrice(110)
                        .closePrice(110)
                        .volume(old.getVolume())
                        .build());

        assertNumEquals(241.73256649892164, subject.getValue(7));
    }

    private void setPrices(NumFactory factory, double... prices) {
        series = new MockBarSeriesBuilder().withNumFactory(factory).build();
        for (double price : prices) {
            series.barBuilder().openPrice(price).highPrice(price).lowPrice(price).closePrice(price).volume(1).add();
        }
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
