/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.AnalysisContext.MissingHistoryPolicy;
import org.ta4j.core.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.analysis.OpenPositionHandling;

/**
 * Unit tests for {@link AnalysisContext}.
 */
public class AnalysisContextTest {

    @Test
    public void defaultsUseConservativePolicies() {
        AnalysisContext defaults = AnalysisContext.defaults();

        assertEquals(MissingHistoryPolicy.STRICT, defaults.missingHistoryPolicy());
        assertEquals(PositionInclusionPolicy.EXIT_IN_WINDOW, defaults.positionInclusionPolicy());
        assertEquals(OpenPositionHandling.IGNORE, defaults.openPositionHandling());
        assertNull(defaults.asOf());
    }

    @Test
    public void constructorRejectsNullPolicies() {
        assertThrows(NullPointerException.class, () -> new AnalysisContext(null, PositionInclusionPolicy.EXIT_IN_WINDOW,
                OpenPositionHandling.IGNORE, null));
        assertThrows(NullPointerException.class,
                () -> new AnalysisContext(MissingHistoryPolicy.STRICT, null, OpenPositionHandling.IGNORE, null));
        assertThrows(NullPointerException.class, () -> new AnalysisContext(MissingHistoryPolicy.STRICT,
                PositionInclusionPolicy.EXIT_IN_WINDOW, null, null));
    }

    @Test
    public void withMethodsReturnUpdatedCopies() {
        Instant asOf = Instant.parse("2026-02-14T00:00:00Z");
        AnalysisContext context = AnalysisContext.defaults()
                .withMissingHistoryPolicy(MissingHistoryPolicy.CLAMP)
                .withPositionInclusionPolicy(PositionInclusionPolicy.FULLY_CONTAINED)
                .withOpenPositionHandling(OpenPositionHandling.MARK_TO_MARKET)
                .withAsOf(asOf);

        assertEquals(MissingHistoryPolicy.CLAMP, context.missingHistoryPolicy());
        assertEquals(PositionInclusionPolicy.FULLY_CONTAINED, context.positionInclusionPolicy());
        assertEquals(OpenPositionHandling.MARK_TO_MARKET, context.openPositionHandling());
        assertEquals(asOf, context.asOf());
    }
}
