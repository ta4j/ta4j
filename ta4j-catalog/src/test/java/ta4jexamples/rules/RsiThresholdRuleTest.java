/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.rules;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RsiThresholdRuleTest {

    @Test
    void labelConstructorAndJsonRoundTripStayStable() {
        BarSeries series = new MockBarSeriesBuilder()
                .withData(1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d, 11d, 12d, 13d, 14d, 15d, 16d, 17d, 18d, 19d, 20d)
                .build();
        RsiThresholdRule original = new RsiThresholdRule(series, "ABOVE", "14", "60");

        Rule restored = Rule.fromJson(series, original.toJson());

        assertEquals("RsiThresholdRule_ABOVE_14_60", original.getName());
        assertTrue(original.isSatisfied(series.getEndIndex()));
        assertEquals(original.getName(), restored.getName());
        assertTrue(restored.isSatisfied(series.getEndIndex()));
    }

    @Test
    void stronglyTypedConstructorRejectsNullInputsWithNamedMessages() {
        BarSeries series = new MockBarSeriesBuilder().withData(1d, 2d, 3d, 4d, 5d).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        NullPointerException missingDirection = assertThrows(NullPointerException.class,
                () -> new RsiThresholdRule(closePrice, 14, series.numFactory().numOf(30), null));
        NullPointerException missingThreshold = assertThrows(NullPointerException.class,
                () -> new RsiThresholdRule(closePrice, 14, null, RsiThresholdRule.ThresholdDirection.BELOW));
        NullPointerException missingClosePrice = assertThrows(NullPointerException.class,
                () -> new RsiThresholdRule(null, 14, series.numFactory().numOf(30),
                        RsiThresholdRule.ThresholdDirection.BELOW));

        assertEquals("direction", missingDirection.getMessage());
        assertEquals("threshold", missingThreshold.getMessage());
        assertEquals("closePriceIndicator", missingClosePrice.getMessage());
    }

    @Test
    void stronglyTypedConstructorRejectsNonFiniteThresholds() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(1d, 2d, 3d, 4d, 5d)
                .build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        IllegalArgumentException nanThreshold = assertThrows(IllegalArgumentException.class,
                () -> new RsiThresholdRule(closePrice, 14, series.numFactory().numOf(Double.NaN),
                        RsiThresholdRule.ThresholdDirection.BELOW));
        IllegalArgumentException infiniteThreshold = assertThrows(IllegalArgumentException.class,
                () -> new RsiThresholdRule(closePrice, 14, series.numFactory().numOf(Double.POSITIVE_INFINITY),
                        RsiThresholdRule.ThresholdDirection.ABOVE));

        assertEquals("RsiThresholdRule threshold must be a finite value", nanThreshold.getMessage());
        assertEquals("RsiThresholdRule threshold must be a finite value", infiniteThreshold.getMessage());
    }

    @Test
    void highPrecisionThresholdLabelPreservesCanonicalDecimalString() {
        BarSeries series = new MockBarSeriesBuilder()
                .withNumFactory(org.ta4j.core.num.DecimalNumFactory.getInstance(40))
                .withData(1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d, 11d, 12d, 13d, 14d, 15d, 16d, 17d, 18d, 19d, 20d)
                .build();
        String threshold = "30.0000000000000000000001";
        RsiThresholdRule original = new RsiThresholdRule(series, "ABOVE", "14", threshold);

        Rule restored = Rule.fromJson(series, original.toJson());

        assertEquals("RsiThresholdRule_ABOVE_14_" + threshold, original.getName());
        assertEquals(original.getName(), restored.getName());
        assertTrue(restored.isSatisfied(series.getEndIndex()));
    }
}
