/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class VWAPBandIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public VWAPBandIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);

        return List.of(serializationFixture(series,
                new VWAPBandIndicator(new VWAPIndicator(close, volume, 8),
                        new VWAPStandardDeviationIndicator(new VWAPIndicator(close, volume, 8)), 1.5,
                        VWAPBandIndicator.BandType.UPPER),
                stableIndexes(series)));
    }
}
