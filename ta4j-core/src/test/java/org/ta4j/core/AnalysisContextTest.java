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
import org.ta4j.core.AnalysisContext.OpenPositionPolicy;
import org.ta4j.core.AnalysisContext.PositionInclusionPolicy;

/**
 * Unit tests for {@link AnalysisContext}.
 */
public class AnalysisContextTest {

    @Test
    public void defaultsUseConservativePolicies() {
        var defaults = AnalysisContext.defaults();

        assertEquals(MissingHistoryPolicy.STRICT, defaults.missingHistoryPolicy());
        assertEquals(PositionInclusionPolicy.EXIT_IN_WINDOW, defaults.positionInclusionPolicy());
        assertEquals(OpenPositionPolicy.EXCLUDE, defaults.openPositionPolicy());
        assertNull(defaults.asOf());
    }

    @Test
    public void constructorRejectsNullPolicies() {
        assertThrows(NullPointerException.class, () -> new AnalysisContext(null, PositionInclusionPolicy.EXIT_IN_WINDOW,
                OpenPositionPolicy.EXCLUDE, null));
        assertThrows(NullPointerException.class,
                () -> new AnalysisContext(MissingHistoryPolicy.STRICT, null, OpenPositionPolicy.EXCLUDE, null));
        assertThrows(NullPointerException.class, () -> new AnalysisContext(MissingHistoryPolicy.STRICT,
                PositionInclusionPolicy.EXIT_IN_WINDOW, null, null));
    }

    @Test
    public void withMethodsReturnUpdatedCopies() {
        var asOf = Instant.parse("2026-02-14T00:00:00Z");
        var context = AnalysisContext.defaults()
                .withMissingHistoryPolicy(MissingHistoryPolicy.CLAMP)
                .withPositionInclusionPolicy(PositionInclusionPolicy.FULLY_CONTAINED)
                .withOpenPositionPolicy(OpenPositionPolicy.MARK_TO_MARKET_AT_WINDOW_END)
                .withAsOf(asOf);

        assertEquals(MissingHistoryPolicy.CLAMP, context.missingHistoryPolicy());
        assertEquals(PositionInclusionPolicy.FULLY_CONTAINED, context.positionInclusionPolicy());
        assertEquals(OpenPositionPolicy.MARK_TO_MARKET_AT_WINDOW_END, context.openPositionPolicy());
        assertEquals(asOf, context.asOf());
    }
}
