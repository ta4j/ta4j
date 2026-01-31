/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.rules.FixedRule;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class BacktestRuntimeReportTest {

    @Test
    public void emptyCreatesReportWithZeroedStatistics() {
        BacktestRuntimeReport report = BacktestRuntimeReport.empty();

        assertEquals(Duration.ZERO, report.overallRuntime());
        assertEquals(Duration.ZERO, report.minStrategyRuntime());
        assertEquals(Duration.ZERO, report.maxStrategyRuntime());
        assertEquals(Duration.ZERO, report.averageStrategyRuntime());
        assertEquals(Duration.ZERO, report.medianStrategyRuntime());
        assertTrue(report.strategyRuntimes().isEmpty());
        assertEquals(0, report.strategyCount());
    }

    @Test
    public void strategyCountReturnsCorrectSize() {
        Strategy strategy1 = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));
        Strategy strategy2 = new BaseStrategy(new FixedRule(1, 3), new FixedRule(2, 4));

        List<BacktestRuntimeReport.StrategyRuntime> runtimes = List.of(
                new BacktestRuntimeReport.StrategyRuntime(strategy1, Duration.ofMillis(100)),
                new BacktestRuntimeReport.StrategyRuntime(strategy2, Duration.ofMillis(200)));

        BacktestRuntimeReport report = new BacktestRuntimeReport(Duration.ofMillis(300), Duration.ofMillis(100),
                Duration.ofMillis(200), Duration.ofMillis(150), Duration.ofMillis(150), runtimes);

        assertEquals(2, report.strategyCount());
    }

    @Test
    public void constructorValidatesStrategyRuntimesNotNull() {
        try {
            new BacktestRuntimeReport(Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, Duration.ZERO, null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            assertEquals("strategyRuntimes must not be null", e.getMessage());
        }
    }

    @Test
    public void constructorCreatesImmutableCopyOfStrategyRuntimes() {
        Strategy strategy = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));
        List<BacktestRuntimeReport.StrategyRuntime> mutableList = new ArrayList<>();
        mutableList.add(new BacktestRuntimeReport.StrategyRuntime(strategy, Duration.ofMillis(100)));

        BacktestRuntimeReport report = new BacktestRuntimeReport(Duration.ZERO, Duration.ZERO, Duration.ZERO,
                Duration.ZERO, Duration.ZERO, mutableList);

        assertEquals(1, report.strategyRuntimes().size());

        mutableList.add(new BacktestRuntimeReport.StrategyRuntime(strategy, Duration.ofMillis(200)));

        assertEquals("strategyRuntimes should be immutable", 1, report.strategyRuntimes().size());
    }

    @Test
    public void toStringExcludesStrategyRuntimes() {
        Strategy strategy1 = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));
        Strategy strategy2 = new BaseStrategy(new FixedRule(1, 3), new FixedRule(2, 4));

        List<BacktestRuntimeReport.StrategyRuntime> runtimes = List.of(
                new BacktestRuntimeReport.StrategyRuntime(strategy1, Duration.ofMillis(100)),
                new BacktestRuntimeReport.StrategyRuntime(strategy2, Duration.ofMillis(200)));

        BacktestRuntimeReport report = new BacktestRuntimeReport(Duration.ofSeconds(5), Duration.ofMillis(100),
                Duration.ofMillis(200), Duration.ofMillis(150), Duration.ofMillis(150), runtimes);

        String jsonString = report.toString();

        assertNotNull("toString() should not return null", jsonString);
        assertFalse("toString() should return non-empty JSON", jsonString.isEmpty());

        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();

        assertTrue("JSON should contain overallRuntime", json.has("overallRuntime"));
        assertTrue("JSON should contain minStrategyRuntime", json.has("minStrategyRuntime"));
        assertTrue("JSON should contain maxStrategyRuntime", json.has("maxStrategyRuntime"));
        assertTrue("JSON should contain averageStrategyRuntime", json.has("averageStrategyRuntime"));
        assertTrue("JSON should contain medianStrategyRuntime", json.has("medianStrategyRuntime"));

        assertFalse("JSON should NOT contain strategyRuntimes", json.has("strategyRuntimes"));
    }

    @Test
    public void toStringSerializesDurationFields() {
        Duration overall = Duration.ofSeconds(5);
        Duration min = Duration.ofMillis(100);
        Duration max = Duration.ofMillis(200);
        Duration avg = Duration.ofMillis(150);
        Duration median = Duration.ofMillis(150);

        BacktestRuntimeReport report = new BacktestRuntimeReport(overall, min, max, avg, median, List.of());

        String jsonString = report.toString();
        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();

        assertEquals("PT5S", json.get("overallRuntime").getAsString());
        assertEquals("PT0.1S", json.get("minStrategyRuntime").getAsString());
        assertEquals("PT0.2S", json.get("maxStrategyRuntime").getAsString());
        assertEquals("PT0.15S", json.get("averageStrategyRuntime").getAsString());
        assertEquals("PT0.15S", json.get("medianStrategyRuntime").getAsString());
    }

    @Test
    public void toStringHandlesEmptyStrategyRuntimes() {
        BacktestRuntimeReport report = BacktestRuntimeReport.empty();

        String jsonString = report.toString();

        assertNotNull("toString() should not return null", jsonString);
        assertFalse("toString() should return non-empty JSON", jsonString.isEmpty());

        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();

        assertFalse("JSON should NOT contain strategyRuntimes", json.has("strategyRuntimes"));
        assertEquals("PT0S", json.get("overallRuntime").getAsString());
    }

    @Test
    public void strategyRuntimeRecordHoldsStrategyAndRuntime() {
        Strategy strategy = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));
        Duration runtime = Duration.ofMillis(150);

        BacktestRuntimeReport.StrategyRuntime strategyRuntime = new BacktestRuntimeReport.StrategyRuntime(strategy,
                runtime);

        assertSame(strategy, strategyRuntime.strategy());
        assertEquals(runtime, strategyRuntime.runtime());
    }
}
