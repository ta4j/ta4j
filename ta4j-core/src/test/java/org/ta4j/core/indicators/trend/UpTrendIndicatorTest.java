/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.trend;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.adx.MinusDIIndicator;
import org.ta4j.core.indicators.adx.PlusDIIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class UpTrendIndicatorTest extends AbstractIndicatorTest<Indicator<Boolean>, Boolean> {

    public UpTrendIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void returnsFalseBeforeUnstableWindow() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        UpTrendIndicator indicator = new UpTrendIndicator(series, 5);

        assertFalse(indicator.getValue(0));
        assertFalse(indicator.getValue(indicator.getCountOfUnstableBars() - 1));
    }

    @Test
    public void unstableBarsTrackDependentIndicators() {
        int barCount = 8;
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();

        UpTrendIndicator indicator = new UpTrendIndicator(series, barCount);
        ADXIndicator adx = new ADXIndicator(series, barCount);
        MinusDIIndicator minusDI = new MinusDIIndicator(series, barCount);
        PlusDIIndicator plusDI = new PlusDIIndicator(series, barCount);
        int diUnstableBars = Math.max(minusDI.getCountOfUnstableBars(), plusDI.getCountOfUnstableBars());
        int expectedUnstableBars = Math.max(adx.getCountOfUnstableBars(), diUnstableBars + 1);

        assertEquals(expectedUnstableBars, indicator.getCountOfUnstableBars());
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(serializationFixture(series, new UpTrendIndicator(series, 7, 20.0), stableIndexes(series)));
    }

}
