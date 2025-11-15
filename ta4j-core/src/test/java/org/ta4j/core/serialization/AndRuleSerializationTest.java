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
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.rules.AndRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;

/**
 * Focused test for AndRule serialization/deserialization to enable quick
 * iteration.
 *
 * These tests help isolate the serialization bug for composite rules. -
 * roundTripAndRuleWithIndicatorRules: Direct descriptor deserialization
 * (PASSES) - roundTripAndRuleFromJson: JSON round-trip deserialization (FAILS -
 * see TODO.md)
 *
 * @see TODO.md in the serialization package for debugging details
 */
public class AndRuleSerializationTest {

    private static Fixture newFixture() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator slowSma = new SMAIndicator(close, 3);
        SMAIndicator fastSma = new SMAIndicator(close, 2);

        Rule entry = new AndRule(new OverIndicatorRule(fastSma, slowSma), new UnderIndicatorRule(fastSma, slowSma));
        Rule exit = new UnderIndicatorRule(fastSma, slowSma);

        Strategy strategy = new BaseStrategy("Test", entry, exit, 2);

        return new Fixture(series, entry, strategy);
    }

    private static ComponentDescriptor withChildLabels(ComponentDescriptor descriptor) {
        ComponentDescriptor.Builder parent = ComponentDescriptor.builder()
                .withType(descriptor.getType())
                .withParameters(descriptor.getParameters());
        int index = 1;
        for (ComponentDescriptor component : descriptor.getComponents()) {
            ComponentDescriptor.Builder child = ComponentDescriptor.builder()
                    .withType(component.getType())
                    .withLabel("rule" + index++)
                    .withParameters(component.getParameters());
            for (ComponentDescriptor nested : component.getComponents()) {
                child.addComponent(nested);
            }
            parent.addComponent(child.build());
        }
        return parent.build();
    }

    @Test
    public void roundTripAndRuleWithIndicatorRules() {
        Fixture fixture = newFixture();

        // Serialize
        ComponentDescriptor descriptor = RuleSerialization.describe(fixture.andRule());
        ComponentDescriptor parsed = ComponentSerialization.parse(ComponentSerialization.toJson(descriptor));

        // Deserialize
        Rule restored = RuleSerialization.fromDescriptor(fixture.series(), parsed);

        assertThat(restored).isInstanceOf(AndRule.class);
        assertThat(restored.isSatisfied(3)).isEqualTo(fixture.andRule().isSatisfied(3));
    }

    @Test
    public void roundTripAndRuleFromJson() {
        Fixture fixture = newFixture();

        // Serialize to JSON
        ComponentDescriptor descriptor = RuleSerialization.describe(fixture.andRule());
        String json = ComponentSerialization.toJson(descriptor);

        // Deserialize from JSON
        ComponentDescriptor parsed = ComponentSerialization.parse(json);
        Rule restored = RuleSerialization.fromDescriptor(fixture.series(), parsed);

        assertThat(restored).isInstanceOf(AndRule.class);
        assertThat(restored.isSatisfied(3)).isEqualTo(fixture.andRule().isSatisfied(3));
    }

    @Test
    public void deserializeAndRuleWithLabeledComponents() {
        Fixture fixture = newFixture();

        ComponentDescriptor descriptor = RuleSerialization.describe(fixture.andRule());
        ComponentDescriptor labeled = withChildLabels(descriptor);

        Rule restored = RuleSerialization.fromDescriptor(fixture.series(), labeled);

        assertThat(restored).isInstanceOf(AndRule.class);
        assertThat(restored.isSatisfied(3)).isEqualTo(fixture.andRule().isSatisfied(3));
    }

    @Test
    public void roundTripStrategyWithAndRule() {
        Fixture fixture = newFixture();

        String json = fixture.strategy().toJson();
        Strategy restored = Strategy.fromJson(fixture.series(), json);

        assertThat(restored.getName()).isEqualTo("Test");
        assertThat(restored.getUnstableBars()).isEqualTo(2);
        assertThat(restored.getEntryRule()).isInstanceOf(AndRule.class);

        TradingRecord originalRecord = new BaseTradingRecord();
        TradingRecord restoredRecord = new BaseTradingRecord();
        assertThat(restored.shouldEnter(3, restoredRecord))
                .isEqualTo(fixture.strategy().shouldEnter(3, originalRecord));
    }

    private record Fixture(BarSeries series, Rule andRule, Strategy strategy) {
    }
}
