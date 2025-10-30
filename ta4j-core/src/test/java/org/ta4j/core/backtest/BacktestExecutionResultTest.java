/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.backtest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

import java.util.List;

import static org.junit.Assert.*;

public class BacktestExecutionResultTest extends AbstractIndicatorTest<BarSeries, Num> {

    public BacktestExecutionResultTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void toStringReturnsJsonWithCountAndRuntimeReport() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(10, 11, 12, 13, 14).build();

        Strategy strategyOne = new BaseStrategy(new FixedRule(0, 2), new FixedRule(1, 3));
        Strategy strategyTwo = new BaseStrategy(new FixedRule(1, 3), new FixedRule(2, 4));

        List<Strategy> strategies = List.of(strategyOne, strategyTwo);

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(strategies, numOf(1));

        String jsonString = result.toString();

        assertNotNull("toString() should not return null", jsonString);
        assertFalse("toString() should return non-empty JSON", jsonString.isEmpty());

        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();

        assertTrue("JSON should contain tradingStatementsCount", json.has("tradingStatementsCount"));
        assertEquals("tradingStatementsCount should match actual count", strategies.size(),
                json.get("tradingStatementsCount").getAsInt());

        assertTrue("JSON should contain runtimeReport", json.has("runtimeReport"));
        assertNotNull("runtimeReport should not be null", json.get("runtimeReport"));
        assertTrue("runtimeReport should be a JSON object", json.get("runtimeReport").isJsonObject());

        JsonObject runtimeReportJson = json.get("runtimeReport").getAsJsonObject();
        assertTrue("runtimeReport should contain overallRuntime", runtimeReportJson.has("overallRuntime"));
        assertTrue("runtimeReport should contain minStrategyRuntime", runtimeReportJson.has("minStrategyRuntime"));
        assertTrue("runtimeReport should contain maxStrategyRuntime", runtimeReportJson.has("maxStrategyRuntime"));
        assertTrue("runtimeReport should contain averageStrategyRuntime",
                runtimeReportJson.has("averageStrategyRuntime"));
        assertTrue("runtimeReport should contain medianStrategyRuntime",
                runtimeReportJson.has("medianStrategyRuntime"));
        assertFalse("runtimeReport should NOT contain strategyRuntimes", runtimeReportJson.has("strategyRuntimes"));
    }

    @Test
    public void toStringHandlesEmptyTradingStatements() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(5, 6, 7).build();

        BacktestExecutor executor = new BacktestExecutor(series);
        BacktestExecutionResult result = executor.executeWithRuntimeReport(List.of(), numOf(1));

        String jsonString = result.toString();

        assertNotNull("toString() should not return null", jsonString);
        assertFalse("toString() should return non-empty JSON", jsonString.isEmpty());

        JsonObject json = JsonParser.parseString(jsonString).getAsJsonObject();

        assertEquals("tradingStatementsCount should be 0 for empty list", 0,
                json.get("tradingStatementsCount").getAsInt());
        assertTrue("JSON should contain runtimeReport", json.has("runtimeReport"));
    }
}
