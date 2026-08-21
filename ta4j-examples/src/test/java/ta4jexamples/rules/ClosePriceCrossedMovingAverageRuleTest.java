/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.rules;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClosePriceCrossedMovingAverageRuleTest {

    @Test
    void labelConstructorAndJsonRoundTripStayStable() {
        BarSeries series = new MockBarSeriesBuilder().withData(12d, 11d, 10d, 9d, 8d, 9d, 10d, 11d, 12d).build();
        ClosePriceCrossedMovingAverageRule original = new ClosePriceCrossedMovingAverageRule(series, "UP", "SMA", "3");

        Rule restored = Rule.fromJson(series, original.toJson());
        boolean originalSatisfied = IntStream.rangeClosed(series.getBeginIndex(), series.getEndIndex())
                .anyMatch(original::isSatisfied);
        boolean restoredSatisfied = IntStream.rangeClosed(series.getBeginIndex(), series.getEndIndex())
                .anyMatch(restored::isSatisfied);

        assertEquals("ClosePriceCrossedMovingAverageRule_UP_SMA_3", original.getName());
        assertTrue(originalSatisfied);
        assertEquals(original.getName(), restored.getName());
        assertTrue(restoredSatisfied);
    }

    @Test
    void traceLogsOuterResultUnderCompactLabelWithDelegateAsChild() {
        BarSeries series = new MockBarSeriesBuilder().withData(12d, 11d, 10d, 9d, 8d, 9d, 10d, 11d, 12d).build();
        ClosePriceCrossedMovingAverageRule rule = new ClosePriceCrossedMovingAverageRule(series, "UP", "SMA", "3");
        TraceTestLogger traceTestLogger = new TraceTestLogger();
        traceTestLogger.open();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        config.removeLogger("ta4jexamples.rules");
        config.addLogger("ta4jexamples.rules", new LoggerConfig("ta4jexamples.rules", Level.TRACE, true));
        context.updateLoggers();
        try {
            traceTestLogger.clear();
            rule.isSatisfiedWithTraceMode(7, null, Rule.TraceMode.VERBOSE);

            String logOutput = traceTestLogger.getLogOutput();
            assertTrue(logOutput.contains("ClosePriceCrossedMovingAverageRule_UP_SMA_3#isSatisfied"));
            assertTrue(logOutput.contains("path=root.delegate depth=1"));
        } finally {
            config.removeLogger("ta4jexamples.rules");
            config.addLogger("ta4jexamples.rules", new LoggerConfig("ta4jexamples.rules", Level.OFF, false));
            context.updateLoggers();
            traceTestLogger.close();
        }
    }

    @Test
    void stronglyTypedConstructorRejectsNullInputsWithNamedMessages() {
        BarSeries series = new MockBarSeriesBuilder().withData(12d, 11d, 10d, 9d, 8d).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        NullPointerException missingAverageType = assertThrows(NullPointerException.class,
                () -> new ClosePriceCrossedMovingAverageRule(closePrice, 3, null,
                        ClosePriceCrossedMovingAverageRule.CrossDirection.UP));
        NullPointerException missingDirection = assertThrows(NullPointerException.class,
                () -> new ClosePriceCrossedMovingAverageRule(closePrice, 3,
                        ClosePriceCrossedMovingAverageRule.AverageType.SMA, null));
        NullPointerException missingClosePrice = assertThrows(NullPointerException.class,
                () -> new ClosePriceCrossedMovingAverageRule(null, 3,
                        ClosePriceCrossedMovingAverageRule.AverageType.SMA,
                        ClosePriceCrossedMovingAverageRule.CrossDirection.UP));

        assertEquals("averageType", missingAverageType.getMessage());
        assertEquals("direction", missingDirection.getMessage());
        assertEquals("closePriceIndicator", missingClosePrice.getMessage());
    }
}
