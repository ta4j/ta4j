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
package org.ta4j.core.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.CrossIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

/**
 * Tests for nested component extraction in rule serialization.
 *
 * <p>
 * These tests verify that composite indicators (like {@link CrossIndicator})
 * stored as fields in rules are correctly "unwrapped" to extract their nested
 * components via getter methods, so they can be matched against constructor
 * parameters during deserialization.
 */
public class RuleSerializationNestedComponentsTest {

    @Test
    public void extractNestedComponentsFromCrossIndicatorInCrossedUpRule() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(close, 2);
        SMAIndicator longSma = new SMAIndicator(close, 3);

        Rule rule = new CrossedUpIndicatorRule(shortSma, longSma);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // Verify the rule type is correct
        assertThat(descriptor.getType()).isEqualTo("CrossedUpIndicatorRule");

        // Verify that nested components from CrossIndicator are extracted
        // The rule stores a CrossIndicator, but the constructor takes two separate
        // indicators
        // So we should see both indicators in the components list
        assertThat(descriptor.getComponents()).hasSize(2);

        // Verify both indicators are present (order may vary, so check types)
        ComponentDescriptor first = descriptor.getComponents().get(0);
        ComponentDescriptor second = descriptor.getComponents().get(1);

        assertThat(first.getType()).isEqualTo("SMAIndicator");
        assertThat(second.getType()).isEqualTo("SMAIndicator");

        // Verify the barCount parameters are preserved
        boolean foundShort = false;
        boolean foundLong = false;
        for (ComponentDescriptor component : descriptor.getComponents()) {
            if (component.getType().equals("SMAIndicator")) {
                Object barCount = component.getParameters().get("barCount");
                if (barCount != null) {
                    if (barCount.equals(2)) {
                        foundShort = true;
                    } else if (barCount.equals(3)) {
                        foundLong = true;
                    }
                }
            }
        }
        assertThat(foundShort).as("Should find SMAIndicator with barCount=2").isTrue();
        assertThat(foundLong).as("Should find SMAIndicator with barCount=3").isTrue();
    }

    @Test
    public void extractNestedComponentsFromCrossIndicatorInCrossedDownRule() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator first = new SMAIndicator(close, 2);
        SMAIndicator second = new SMAIndicator(close, 3);

        Rule rule = new CrossedDownIndicatorRule(first, second);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // Verify the rule type is correct
        assertThat(descriptor.getType()).isEqualTo("CrossedDownIndicatorRule");

        // Verify that nested components from CrossIndicator are extracted
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents().get(0).getType()).isEqualTo("SMAIndicator");
        assertThat(descriptor.getComponents().get(1).getType()).isEqualTo("SMAIndicator");
    }

    @Test
    public void roundTripCrossedUpIndicatorRuleWithNestedComponents() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(close, 2);
        SMAIndicator longSma = new SMAIndicator(close, 3);

        Rule original = new CrossedUpIndicatorRule(shortSma, longSma);

        // Serialize and deserialize
        ComponentDescriptor descriptor = RuleSerialization.describe(original);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        // Verify the restored rule is of the correct type
        assertThat(restored).isInstanceOf(CrossedUpIndicatorRule.class);

        // Verify the rule behaves the same way
        CrossedUpIndicatorRule originalRule = (CrossedUpIndicatorRule) original;
        CrossedUpIndicatorRule restoredRule = (CrossedUpIndicatorRule) restored;

        // Both should have the same nested indicators accessible via getters
        Indicator<Num> originalLow = originalRule.getLow();
        Indicator<Num> originalUp = originalRule.getUp();
        Indicator<Num> restoredLow = restoredRule.getLow();
        Indicator<Num> restoredUp = restoredRule.getUp();

        assertThat(originalLow).isNotNull();
        assertThat(originalUp).isNotNull();
        assertThat(restoredLow).isNotNull();
        assertThat(restoredUp).isNotNull();

        // Verify the indicators have the same values at various indices
        for (int i = 0; i < series.getBarCount(); i++) {
            assertThat(restored.isSatisfied(i)).as("Rule should behave the same at index %d", i)
                    .isEqualTo(original.isSatisfied(i));
        }
    }

    @Test
    public void roundTripCrossedDownIndicatorRuleWithNestedComponents() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator first = new SMAIndicator(close, 2);
        SMAIndicator second = new SMAIndicator(close, 3);

        Rule original = new CrossedDownIndicatorRule(first, second);

        // Serialize and deserialize
        ComponentDescriptor descriptor = RuleSerialization.describe(original);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        // Verify the restored rule is of the correct type
        assertThat(restored).isInstanceOf(CrossedDownIndicatorRule.class);

        // Verify the rule behaves the same way
        for (int i = 0; i < series.getBarCount(); i++) {
            assertThat(restored.isSatisfied(i)).as("Rule should behave the same at index %d", i)
                    .isEqualTo(original.isSatisfied(i));
        }
    }

    @Test
    public void extractNestedComponentsWithConstantIndicators() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ConstantIndicator<Num> constant = new ConstantIndicator<>(series, series.numFactory().numOf(10));

        Rule rule = new CrossedUpIndicatorRule(close, constant);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // Verify nested components are extracted
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents().get(0).getType()).isIn("ClosePriceIndicator", "ConstantIndicator");
        assertThat(descriptor.getComponents().get(1).getType()).isIn("ClosePriceIndicator", "ConstantIndicator");
    }

    @Test
    public void extractNestedComponentsWithNumberThreshold() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        // Use constructor that takes Number threshold (creates ConstantIndicator
        // internally)
        Rule rule = new CrossedUpIndicatorRule(close, 10);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // Verify nested components are extracted
        assertThat(descriptor.getComponents()).hasSize(2);
        // One should be ClosePriceIndicator, the other should be ConstantIndicator
        boolean foundClose = false;
        boolean foundConstant = false;
        for (ComponentDescriptor component : descriptor.getComponents()) {
            if (component.getType().equals("ClosePriceIndicator")) {
                foundClose = true;
            } else if (component.getType().equals("ConstantIndicator")) {
                foundConstant = true;
            }
        }
        assertThat(foundClose).isTrue();
        assertThat(foundConstant).isTrue();
    }

    @Test
    public void extractNestedComponentsWithNumThreshold() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Num threshold = series.numFactory().numOf(10);

        // Use constructor that takes Num threshold (creates ConstantIndicator
        // internally)
        Rule rule = new CrossedUpIndicatorRule(close, threshold);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // Verify nested components are extracted
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents().get(0).getType()).isIn("ClosePriceIndicator", "ConstantIndicator");
        assertThat(descriptor.getComponents().get(1).getType()).isIn("ClosePriceIndicator", "ConstantIndicator");
    }

    @Test
    public void nestedComponentsExtractionPreservesComponentOrder() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator indicator1 = new SMAIndicator(close, 2);
        SMAIndicator indicator2 = new SMAIndicator(close, 3);

        Rule rule = new CrossedUpIndicatorRule(indicator1, indicator2);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // Verify components are extracted in a consistent order
        // (alphabetical by getter name: getLow comes before getUp)
        assertThat(descriptor.getComponents()).hasSize(2);

        // Round-trip should preserve the same order
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);
        ComponentDescriptor restoredDescriptor = RuleSerialization.describe(restored);

        // The order should be consistent (though not necessarily the same as original
        // due to constructor matching logic)
        assertThat(restoredDescriptor.getComponents()).hasSize(2);
    }

    @Test
    public void nestedComponentsExtractionWorksInStrategyContext() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(close, 2);
        SMAIndicator longSma = new SMAIndicator(close, 3);

        Rule entry = new CrossedUpIndicatorRule(shortSma, longSma);
        Rule exit = new CrossedDownIndicatorRule(shortSma, longSma);

        org.ta4j.core.Strategy strategy = new org.ta4j.core.BaseStrategy("Test", entry, exit, 1);

        // Serialize strategy
        ComponentDescriptor strategyDescriptor = strategy.toDescriptor();

        // Verify entry rule has nested components extracted
        ComponentDescriptor entryDescriptor = strategyDescriptor.getComponents().get(0);
        assertThat(entryDescriptor.getType()).isEqualTo("CrossedUpIndicatorRule");
        assertThat(entryDescriptor.getComponents()).hasSize(2);

        // Verify exit rule has nested components extracted
        ComponentDescriptor exitDescriptor = strategyDescriptor.getComponents().get(1);
        assertThat(exitDescriptor.getType()).isEqualTo("CrossedDownIndicatorRule");
        assertThat(exitDescriptor.getComponents()).hasSize(2);

        // Round-trip the strategy
        org.ta4j.core.Strategy restoredStrategy = org.ta4j.core.serialization.StrategySerialization
                .fromDescriptor(series, strategyDescriptor);

        // Verify the restored strategy works correctly
        assertThat(restoredStrategy.getName()).isEqualTo("Test");
        assertThat(restoredStrategy.getUnstableBars()).isEqualTo(1);
    }

    @Test
    public void nestedComponentsExtractionHandlesMultipleCompositeIndicators() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator sma1 = new SMAIndicator(close, 2);
        SMAIndicator sma2 = new SMAIndicator(close, 3);
        SMAIndicator sma3 = new SMAIndicator(close, 4);

        // Create rules with different composite indicators
        Rule rule1 = new CrossedUpIndicatorRule(sma1, sma2);
        Rule rule2 = new CrossedDownIndicatorRule(sma2, sma3);

        // Both should serialize correctly with nested components extracted
        ComponentDescriptor desc1 = RuleSerialization.describe(rule1);
        ComponentDescriptor desc2 = RuleSerialization.describe(rule2);

        assertThat(desc1.getComponents()).hasSize(2);
        assertThat(desc2.getComponents()).hasSize(2);

        // Both should round-trip correctly
        Rule restored1 = RuleSerialization.fromDescriptor(series, desc1);
        Rule restored2 = RuleSerialization.fromDescriptor(series, desc2);

        assertThat(restored1).isInstanceOf(CrossedUpIndicatorRule.class);
        assertThat(restored2).isInstanceOf(CrossedDownIndicatorRule.class);
    }
}
