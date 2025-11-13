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
import org.ta4j.core.Rule;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.rules.AndRule;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.rules.NotRule;
import org.ta4j.core.rules.OrRule;
import org.ta4j.core.rules.XorRule;

public class RuleSerializationCustomNameTest {

    @Test
    public void preserveCustomNameForSimpleRule() {
        BarSeries series = new MockBarSeriesBuilder().build();
        Rule rule = new FixedRule(1);
        rule.setName("My Custom Rule");

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertThat(restored.getName()).isEqualTo("My Custom Rule");
    }

    @Test
    public void preserveCustomNameForCompositeRule() {
        BarSeries series = new MockBarSeriesBuilder().build();
        Rule entryRule = new FixedRule(1);
        entryRule.setName("Entry");
        Rule exitRule = new FixedRule(2);
        exitRule.setName("Exit");

        Rule andRule = new AndRule(entryRule, exitRule);
        // AndRule automatically sets its name based on child names
        String originalName = andRule.getName();

        ComponentDescriptor descriptor = RuleSerialization.describe(andRule);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertThat(restored.getName()).isEqualTo(originalName);
        // Verify child rules also preserved their names
        AndRule restoredAnd = (AndRule) restored;
        assertThat(restoredAnd.getRule1().getName()).isEqualTo("Entry");
        assertThat(restoredAnd.getRule2().getName()).isEqualTo("Exit");
    }

    @Test
    public void preserveCustomNameForNestedCompositeRules() {
        BarSeries series = new MockBarSeriesBuilder().build();
        Rule entryRule = new FixedRule(1);
        entryRule.setName("Entry");
        Rule exitRule = new FixedRule(2);
        exitRule.setName("Exit");

        Rule innerAnd = new AndRule(entryRule, exitRule);
        Rule notExit = new NotRule(exitRule);
        Rule outerOr = new OrRule(innerAnd, notExit);

        String originalName = outerOr.getName();

        ComponentDescriptor descriptor = RuleSerialization.describe(outerOr);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertThat(restored.getName()).isEqualTo(originalName);
    }

    @Test
    public void preserveCustomNameForOrRule() {
        BarSeries series = new MockBarSeriesBuilder().build();
        Rule rule1 = new FixedRule(1);
        rule1.setName("Rule1");
        Rule rule2 = new FixedRule(2);
        rule2.setName("Rule2");

        Rule orRule = new OrRule(rule1, rule2);
        String originalName = orRule.getName();

        ComponentDescriptor descriptor = RuleSerialization.describe(orRule);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertThat(restored.getName()).isEqualTo(originalName);
    }

    @Test
    public void preserveCustomNameForXorRule() {
        BarSeries series = new MockBarSeriesBuilder().build();
        Rule rule1 = new FixedRule(1);
        rule1.setName("Rule1");
        Rule rule2 = new FixedRule(2);
        rule2.setName("Rule2");

        Rule xorRule = new XorRule(rule1, rule2);
        String originalName = xorRule.getName();

        ComponentDescriptor descriptor = RuleSerialization.describe(xorRule);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertThat(restored.getName()).isEqualTo(originalName);
    }

    @Test
    public void preserveCustomNameForNotRule() {
        BarSeries series = new MockBarSeriesBuilder().build();
        Rule rule = new FixedRule(1);
        rule.setName("MyRule");

        Rule notRule = new NotRule(rule);
        String originalName = notRule.getName();

        ComponentDescriptor descriptor = RuleSerialization.describe(notRule);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertThat(restored.getName()).isEqualTo(originalName);
    }
}

