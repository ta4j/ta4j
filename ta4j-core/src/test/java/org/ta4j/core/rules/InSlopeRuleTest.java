/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.num.Num;

public class InSlopeRuleTest {

    private InSlopeRule rulePositiveSlope;
    private InSlopeRule ruleNegativeSlope;
    private BarSeries series;
    private TraceTestLogger traceTestLogger;

    @BeforeEach
    public void setUp() {
        series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).build();
        Indicator<Num> indicator = new FixedNumIndicator(series, 50, 70, 80, 90, 99, 60, 30, 20, 10, 0);
        rulePositiveSlope = new InSlopeRule(indicator, series.numFactory().numOf(20), series.numFactory().numOf(30));
        ruleNegativeSlope = new InSlopeRule(indicator, series.numFactory().numOf(-40), series.numFactory().numOf(-20));
        traceTestLogger = new TraceTestLogger();
        traceTestLogger.open();
    }

    @AfterEach
    public void tearDown() {
        traceTestLogger.close();
    }

    @Test
    public void isSatisfied() {
        assertFalse(rulePositiveSlope.isSatisfied(0));
        assertTrue(rulePositiveSlope.isSatisfied(1));
        assertFalse(rulePositiveSlope.isSatisfied(2));
        assertFalse(rulePositiveSlope.isSatisfied(9));

        assertFalse(ruleNegativeSlope.isSatisfied(0));
        assertFalse(ruleNegativeSlope.isSatisfied(1));
        assertTrue(ruleNegativeSlope.isSatisfied(5));
        assertFalse(ruleNegativeSlope.isSatisfied(9));
    }

    @Test
    public void traceIncludesSlopeValues() {
        assertTrue(rulePositiveSlope.isSatisfiedWithTraceMode(1, Rule.TraceMode.VERBOSE));

        String logContent = traceTestLogger.getLogOutput();
        assertTrue(logContent.contains("currentValue=70"), "Trace should include the current value");
        assertTrue(logContent.contains("previousValue=50"), "Trace should include the previous value");
        assertTrue(logContent.contains("slope=20"), "Trace should include the computed slope");
        assertTrue(logContent.contains("minSlope=20"), "Trace should include the minimum slope");
        assertTrue(logContent.contains("maxSlope=30"), "Trace should include the maximum slope");
        assertTrue(logContent.contains("reason=withinSlopeRange"), "Trace should explain the slope result");
    }

    @Test
    public void testSerializationRoundTrip() {
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, rulePositiveSlope);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, rulePositiveSlope);
        RuleSerializationRoundTripTestSupport.assertRuleRoundTrips(series, ruleNegativeSlope);
        RuleSerializationRoundTripTestSupport.assertRuleJsonRoundTrips(series, ruleNegativeSlope);
    }

    @Test
    public void rejectsInvalidConstructorArguments() {
        Indicator<Num> indicator = new FixedNumIndicator(series, 1, 2, 3);

        assertThrows(NullPointerException.class, () -> new InSlopeRule(null, series.numFactory().zero()));
        assertThrows(IllegalArgumentException.class,
                () -> new InSlopeRule(indicator, 0, series.numFactory().zero(), series.numFactory().one()));
    }
}
