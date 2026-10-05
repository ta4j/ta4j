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

public class VWAPZScoreIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public VWAPZScoreIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        VolumeIndicator volume = new VolumeIndicator(series);
        VWAPIndicator vwap = new VWAPIndicator(close, volume, 8);

        return List.of(serializationFixture(series, new VWAPZScoreIndicator(new VWAPDeviationIndicator(close, vwap),
                new VWAPStandardDeviationIndicator(vwap)), stableIndexes(series)));
    }
}
