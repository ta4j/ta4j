/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.candles;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

public class CandleBodyIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public CandleBodyIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(10).closePrice(18).highPrice(20).lowPrice(10).add();
        series.barBuilder().openPrice(17).closePrice(20).highPrice(21).lowPrice(17).add();
        series.barBuilder().openPrice(15).closePrice(15).highPrice(16).lowPrice(14).add();
        series.barBuilder().openPrice(15).closePrice(11).highPrice(15).lowPrice(8).add();
        series.barBuilder().openPrice(11).closePrice(12).highPrice(12).lowPrice(10).add();
    }

    @Test
    public void getValue() {
        var body = new CandleBodyIndicator(series);
        assertNumEquals(8, body.getValue(0));
        assertNumEquals(3, body.getValue(1));
        assertNumEquals(0, body.getValue(2));
        assertNumEquals(4, body.getValue(3));
        assertNumEquals(1, body.getValue(4));
    }

    @Test
    public void isNeverNegative() {
        var body = new CandleBodyIndicator(series);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertFalse(body.getValue(i).isNegative());
        }
    }

    @Test
    public void isInvariantUnderOpenCloseSwap() {
        var body = new CandleBodyIndicator(series);
        BarSeries swapped = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            Bar bar = series.getBar(i);
            swapped.barBuilder()
                    .openPrice(bar.getClosePrice())
                    .closePrice(bar.getOpenPrice())
                    .highPrice(bar.getHighPrice())
                    .lowPrice(bar.getLowPrice())
                    .add();
        }
        var swappedBody = new CandleBodyIndicator(swapped);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(body.getValue(i), swappedBody.getValue(i));
        }
    }

    @Test
    public void matchesRealBodyMagnitude() {
        var body = new CandleBodyIndicator(series);
        @SuppressWarnings("deprecation")
        var realBody = new RealBodyIndicator(series);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(realBody.getValue(i).abs(), body.getValue(i));
        }
    }

    @Test
    public void zeroBodyForDojiBar() {
        BarSeries doji = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1d).build();
        doji.barBuilder().openPrice(5).closePrice(5).highPrice(6).lowPrice(4).add();
        assertTrue(new CandleBodyIndicator(doji).getValue(1).isZero());
    }

    @Test
    public void isUndefinedForMissingOpenPrice() {
        series.barBuilder().openPrice((Num) null).closePrice(12).highPrice(14).lowPrice(10).add();
        assertTrue(new CandleBodyIndicator(series).getValue(series.getEndIndex()).isNaN());
    }

    @Test
    public void isUndefinedForNonFinitePrices() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries nonFinite = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        nonFinite.barBuilder()
                .openPrice(doubleFactory.numOf(Double.POSITIVE_INFINITY))
                .closePrice(doubleFactory.numOf(Double.POSITIVE_INFINITY))
                .highPrice(doubleFactory.numOf(Double.POSITIVE_INFINITY))
                .lowPrice(10)
                .add();
        assertTrue(new CandleBodyIndicator(nonFinite).getValue(0).isNaN());
    }

    @Test
    public void reportsNonFiniteMagnitudeWhenSubtractionOverflows() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries overflow = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        overflow.barBuilder()
                .openPrice(doubleFactory.numOf(Double.MAX_VALUE))
                .closePrice(doubleFactory.numOf(-Double.MAX_VALUE))
                .highPrice(doubleFactory.numOf(Double.MAX_VALUE))
                .lowPrice(doubleFactory.numOf(-Double.MAX_VALUE))
                .add();
        Num body = new CandleBodyIndicator(overflow).getValue(0);
        assertFalse(body.isNaN());
        assertFalse(Num.isFinite(body));
    }

    @Test
    public void isUndefinedForMissingClosePrice() {
        series.barBuilder().openPrice(10).closePrice((Num) null).highPrice(14).lowPrice(10).add();
        assertTrue(new CandleBodyIndicator(series).getValue(series.getEndIndex()).isNaN());
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new CandleBodyIndicator(series), stableIndexes(series)));
    }

}
