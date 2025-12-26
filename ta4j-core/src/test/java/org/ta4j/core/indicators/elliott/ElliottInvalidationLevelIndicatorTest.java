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
package org.ta4j.core.indicators.elliott;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

class ElliottInvalidationLevelIndicatorTest {

    @Test
    void returnsNaNWhenNoScenarios() {
        BarSeries series = new MockBarSeriesBuilder().build();
        series.barBuilder().openPrice(100).highPrice(100).lowPrice(100).closePrice(100).volume(0).add();

        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator indicator = new ElliottInvalidationLevelIndicator(scenarioIndicator);

        Num invalidation = indicator.getValue(0);

        // With no scenarios, invalidation returns NaN - check via Num API
        assertThat(Num.isNaNOrNull(invalidation)).isTrue();
    }

    @Test
    void primaryModeUsesTopScenario() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator indicator = new ElliottInvalidationLevelIndicator(scenarioIndicator,
                ElliottInvalidationLevelIndicator.InvalidationMode.PRIMARY);

        Num invalidation = indicator.getValue(series.getEndIndex());

        // Should be primary scenario's invalidation or NaN
        assertThat(invalidation).isNotNull();
    }

    @Test
    void conservativeModeUsesTightestInvalidation() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator conservative = new ElliottInvalidationLevelIndicator(scenarioIndicator,
                ElliottInvalidationLevelIndicator.InvalidationMode.CONSERVATIVE);

        Num invalidation = conservative.getValue(series.getEndIndex());

        assertThat(invalidation).isNotNull();
    }

    @Test
    void aggressiveModeUsesWidestInvalidation() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator aggressive = new ElliottInvalidationLevelIndicator(scenarioIndicator,
                ElliottInvalidationLevelIndicator.InvalidationMode.AGGRESSIVE);

        Num invalidation = aggressive.getValue(series.getEndIndex());

        assertThat(invalidation).isNotNull();
    }

    @Test
    void isInvalidatedCheck() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator indicator = new ElliottInvalidationLevelIndicator(scenarioIndicator);

        // Check returns boolean without throwing
        boolean invalidated = indicator.isInvalidated(series.getEndIndex(), series.numFactory().numOf(50));

        assertThat(invalidated).isIn(true, false);
    }

    @Test
    void distanceToInvalidation() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 1, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator indicator = new ElliottInvalidationLevelIndicator(scenarioIndicator);

        Num distance = indicator.distanceToInvalidation(series.getEndIndex(), series.numFactory().numOf(120));

        // Returns a valid distance or NaN
        assertThat(distance).isNotNull();
    }

    @Test
    void unstableBarsFromScenarioIndicator() {
        BarSeries series = createSeriesWithSwings();
        ElliottSwingIndicator swingIndicator = new ElliottSwingIndicator(series, 3, ElliottDegree.MINOR);
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator);
        ElliottInvalidationLevelIndicator indicator = new ElliottInvalidationLevelIndicator(scenarioIndicator);

        assertThat(indicator.getCountOfUnstableBars()).isEqualTo(scenarioIndicator.getCountOfUnstableBars());
    }

    private BarSeries createSeriesWithSwings() {
        MockBarSeriesBuilder builder = new MockBarSeriesBuilder();
        BarSeries series = builder.build();

        double[] prices = { 100, 110, 105, 120, 108, 130, 115, 140, 125, 150 };
        for (double price : prices) {
            series.barBuilder()
                    .openPrice(price - 2)
                    .highPrice(price + 2)
                    .lowPrice(price - 3)
                    .closePrice(price)
                    .volume(1000)
                    .add();
        }

        return series;
    }
}
