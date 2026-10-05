/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.ichimoku;

import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.serializationSeries;
import static org.ta4j.core.indicators.IndicatorSerializationRoundTripTestSupport.stableIndexes;

import java.util.List;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class IchimokuSenkouSpanBIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public IchimokuSenkouSpanBIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Override
    protected List<IndicatorSerializationFixture<?>> serializationFixtures() {
        BarSeries series = serializationSeries(numFactory);
        return List.of(
                serializationFixture(series, new IchimokuSenkouSpanBIndicator(series, 21, 8), stableIndexes(series)));
    }
}
