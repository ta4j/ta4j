/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;

/**
 * Unit tests for {@link AnalysisWindow}.
 */
public class AnalysisWindowTest {

    @Test
    public void barRangeFactoryCreatesBarRangeWindow() {
        var window = AnalysisWindow.barRange(5, 12);

        assertTrue(window instanceof AnalysisWindow.BarRange);
        var barRange = (AnalysisWindow.BarRange) window;
        assertEquals(5, barRange.startIndexInclusive());
        assertEquals(12, barRange.endIndexInclusive());
    }

    @Test
    public void barRangeRejectsNegativeStart() {
        assertThrows(IllegalArgumentException.class, () -> AnalysisWindow.barRange(-1, 10));
    }

    @Test
    public void barRangeRejectsEndBeforeStart() {
        assertThrows(IllegalArgumentException.class, () -> AnalysisWindow.barRange(10, 5));
    }

    @Test
    public void lookbackBarsFactoryCreatesLookbackBarsWindow() {
        var window = AnalysisWindow.lookbackBars(30);

        assertTrue(window instanceof AnalysisWindow.LookbackBars);
        assertEquals(30, ((AnalysisWindow.LookbackBars) window).barCount());
    }

    @Test
    public void lookbackBarsRejectsNonPositiveCount() {
        assertThrows(IllegalArgumentException.class, () -> AnalysisWindow.lookbackBars(0));
    }

    @Test
    public void timeRangeFactoryCreatesTimeRangeWindow() {
        var start = Instant.parse("2026-02-10T00:00:00Z");
        var end = Instant.parse("2026-02-14T00:00:00Z");
        var window = AnalysisWindow.timeRange(start, end);

        assertTrue(window instanceof AnalysisWindow.TimeRange);
        var timeRange = (AnalysisWindow.TimeRange) window;
        assertEquals(start, timeRange.startInclusive());
        assertEquals(end, timeRange.endExclusive());
    }

    @Test
    public void timeRangeRejectsStartAtOrAfterEnd() {
        var instant = Instant.parse("2026-02-10T00:00:00Z");
        assertThrows(IllegalArgumentException.class, () -> AnalysisWindow.timeRange(instant, instant));
    }

    @Test
    public void lookbackDurationFactoryCreatesLookbackDurationWindow() {
        var duration = Duration.ofDays(7);
        var window = AnalysisWindow.lookbackDuration(duration);

        assertTrue(window instanceof AnalysisWindow.LookbackDuration);
        assertEquals(duration, ((AnalysisWindow.LookbackDuration) window).duration());
    }

    @Test
    public void lookbackDurationRejectsNonPositiveDuration() {
        assertThrows(IllegalArgumentException.class, () -> AnalysisWindow.lookbackDuration(Duration.ZERO));
    }
}
