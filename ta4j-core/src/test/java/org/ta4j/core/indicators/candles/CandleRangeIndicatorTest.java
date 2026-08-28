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
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class CandleRangeIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;

    public CandleRangeIndicatorTest(NumFactory numFactory) {
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
        var range = new CandleRangeIndicator(series);
        assertNumEquals(10, range.getValue(0));
        assertNumEquals(4, range.getValue(1));
        assertNumEquals(2, range.getValue(2));
        assertNumEquals(7, range.getValue(3));
        assertNumEquals(2, range.getValue(4));
    }

    @Test
    public void isNeverNegative() {
        var range = new CandleRangeIndicator(series);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertFalse(range.getValue(i).isNegative());
        }
    }

    @Test
    public void decomposesIntoShadowsAndBody() {
        var range = new CandleRangeIndicator(series);
        var body = new CandleBodyIndicator(series);
        var upper = new UpperShadowIndicator(series);
        var lower = new LowerShadowIndicator(series);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertNumEquals(range.getValue(i), upper.getValue(i).plus(body.getValue(i)).plus(lower.getValue(i)));
        }
    }

    @Test
    public void zeroRangeForFlatBar() {
        BarSeries flat = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        flat.barBuilder().openPrice(5).closePrice(5).highPrice(5).lowPrice(5).add();
        assertTrue(new CandleRangeIndicator(flat).getValue(0).isZero());
    }

    @Test
    public void isUndefinedForMissingHighPrice() {
        series.barBuilder().openPrice(10).closePrice(12).highPrice((Num) null).lowPrice(10).add();
        assertTrue(new CandleRangeIndicator(series).getValue(series.getEndIndex()).isNaN());
    }

    @Test
    public void isUndefinedForMissingLowPrice() {
        series.barBuilder().openPrice(10).closePrice(12).highPrice(14).lowPrice((Num) null).add();
        assertTrue(new CandleRangeIndicator(series).getValue(series.getEndIndex()).isNaN());
    }

    @Test
    public void isUndefinedForNonFinitePrices() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries nonFinite = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        nonFinite.barBuilder()
                .openPrice(10)
                .closePrice(12)
                .highPrice(doubleFactory.numOf(Double.POSITIVE_INFINITY))
                .lowPrice(10)
                .add();
        assertTrue(new CandleRangeIndicator(nonFinite).getValue(0).isNaN());
    }

    @Test
    public void isUndefinedWhenSubtractionOverflows() {
        DoubleNumFactory doubleFactory = DoubleNumFactory.getInstance();
        BarSeries overflow = new MockBarSeriesBuilder().withNumFactory(doubleFactory).build();
        overflow.barBuilder()
                .openPrice(0)
                .closePrice(0)
                .highPrice(doubleFactory.numOf(Double.MAX_VALUE))
                .lowPrice(doubleFactory.numOf(-Double.MAX_VALUE))
                .add();
        assertTrue(new CandleRangeIndicator(overflow).getValue(0).isNaN());
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new CandleRangeIndicator(series), stableIndexes(series)));
    }

}
