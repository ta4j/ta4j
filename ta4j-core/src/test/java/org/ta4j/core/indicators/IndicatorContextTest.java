/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class IndicatorContextTest {

    @Test
    public void standardSeriesRetainsContextAndCanonicalFactories() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).build();
        IndicatorContext context = series.indicators();

        assertSame(context, series.indicators());
        assertSame(context.closePrice(), context.closePrice());
        assertSame(context.openPrice(), context.openPrice());
        assertSame(context.highPrice(), context.highPrice());
        assertSame(context.lowPrice(), context.lowPrice());
        assertSame(context.typicalPrice(), context.typicalPrice());
        assertSame(context.volume(), context.volume());
        assertSame(context.sma(context.closePrice(), 3), context.sma(context.closePrice(), 3));
        assertSame(context.ema(context.closePrice(), 3), context.ema(context.closePrice(), 3));
        assertSame(context.atr(3), context.atr(3));
        assertSame(context.adx(3), context.adx(3));
        assertSame(context.rsi(context.closePrice(), 3), context.rsi(context.closePrice(), 3));
        assertSame(context.roc(context.closePrice(), 3), context.roc(context.closePrice(), 3));
        assertSame(context.macd(context.closePrice(), 3, 5), context.macd(context.closePrice(), 3, 5));
        assertSame(context.previous(context.closePrice(), 2), context.previous(context.closePrice(), 2));
        assertSame(context.highest(context.closePrice(), 3), context.highest(context.closePrice(), 3));
        assertSame(context.lowest(context.closePrice(), 3), context.lowest(context.closePrice(), 3));
        assertSame(context.standardDeviation(context.closePrice(), 3),
                context.standardDeviation(context.closePrice(), 3));
    }

    @Test
    public void readOnlyViewsDelegateWhileSubseriesOwnIndependentContexts() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        assertSame(series.indicators(), close.getBarSeries().indicators());
        assertEquals(series.getBarHistoryEpoch(), close.getBarSeries().getBarHistoryEpoch());
        assertNotSame(series.indicators(), series.getSubSeries(1, 4).indicators());
    }

    @Test
    public void unsupportedEpochKeepsOtherwiseEquivalentIndicatorsIsolated() {
        BarSeries series = new UnsupportedEpochSeries();
        series.addBar(new MockBarSeriesBuilder().withData(1).build().getBar(0));
        SMAIndicator first = new SMAIndicator(new ClosePriceIndicator(series), 2);
        SMAIndicator second = new SMAIndicator(new ClosePriceIndicator(series), 2);

        assertNotSame(((CachedIndicator<?>) first).sharedStateIdentity(),
                ((CachedIndicator<?>) second).sharedStateIdentity());
    }

    @Test
    public void deserializedSeriesCreatesFreshRuntimeContext() throws Exception {
        BaseBarSeries series = (BaseBarSeries) new MockBarSeriesBuilder().withData(1, 2, 3).build();
        IndicatorContext originalContext = series.indicators();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(series);
        }

        BaseBarSeries restored;
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = (BaseBarSeries) input.readObject();
        }

        assertNotSame(originalContext, restored.indicators());
        assertSame(restored.indicators(), restored.indicators());
    }

    private static final class UnsupportedEpochSeries extends BaseBarSeries {

        private UnsupportedEpochSeries() {
            super("unsupported", java.util.List.of());
        }

        @Override
        public long getBarHistoryEpoch() {
            return -1L;
        }
    }
}
