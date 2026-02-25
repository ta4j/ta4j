/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import java.util.Arrays;

import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

final class StopRuleTestSupport {

    private StopRuleTestSupport() {
    }

    static BarSeries series(NumFactory numFactory, Number... closes) {
        double[] closeArray = Arrays.stream(closes).mapToDouble(Number::doubleValue).toArray();
        return new MockBarSeriesBuilder().withNumFactory(numFactory).withData(closeArray).build();
    }

    static ClosePriceIndicator closePrice(NumFactory numFactory, Number... closes) {
        return new ClosePriceIndicator(series(numFactory, closes));
    }
}
