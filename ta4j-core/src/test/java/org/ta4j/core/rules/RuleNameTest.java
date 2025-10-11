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
package org.ta4j.core.rules;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.ta4j.core.Rule;

public class RuleNameTest {

    @Test
    public void defaultNameFallsBackToClassSimpleName() {
        Rule rule = new FixedRule(1);

        assertEquals("{\"type\":\"FixedRule\"}", rule.getName());
        assertEquals("{\"type\":\"FixedRule\"}", rule.toString());
    }

    @Test
    public void customNameCanBeSetAndReset() {
        Rule rule = new FixedRule(1);

        rule.setName("My Custom Rule");
        assertEquals("My Custom Rule", rule.getName());
        assertEquals("My Custom Rule", rule.toString());

        rule.setName(null);
        assertEquals("{\"type\":\"FixedRule\"}", rule.getName());

        rule.setName("   ");
        assertEquals("{\"type\":\"FixedRule\"}", rule.getName());
    }

    @Test
    public void compositeRulesCombineChildNames() {
        Rule entryRule = new FixedRule(1);
        entryRule.setName("Entry");
        Rule exitRule = new FixedRule(2);
        exitRule.setName("Exit");

        Rule andRule = new AndRule(entryRule, exitRule);
        assertEquals("{\"type\":\"AndRule\",\"rules\":[{\"label\":\"Entry\"},{\"label\":\"Exit\"}]}",
                andRule.getName());

        Rule orRule = new OrRule(entryRule, exitRule);
        assertEquals("{\"type\":\"OrRule\",\"rules\":[{\"label\":\"Entry\"},{\"label\":\"Exit\"}]}", orRule.getName());

        Rule xorRule = new XorRule(entryRule, exitRule);
        assertEquals("{\"type\":\"XorRule\",\"rules\":[{\"label\":\"Entry\"},{\"label\":\"Exit\"}]}",
                xorRule.getName());

        Rule notRule = new NotRule(entryRule);
        assertEquals("{\"type\":\"NotRule\",\"rules\":[{\"label\":\"Entry\"}]}", notRule.getName());
    }

    @Test
    public void nestedCompositeRulesAreSerializedRecursively() {
        Rule entryRule = new FixedRule(1);
        entryRule.setName("Entry");
        Rule exitRule = new FixedRule(2);
        exitRule.setName("Exit");

        Rule innerAnd = new AndRule(entryRule, exitRule);
        Rule notExit = new NotRule(exitRule);

        Rule outerOr = new OrRule(innerAnd, notExit);

        assertEquals(
                "{\"type\":\"OrRule\",\"rules\":[{\"type\":\"AndRule\",\"rules\":[{\"label\":\"Entry\"},{\"label\":\"Exit\"}]},{\"type\":\"NotRule\",\"rules\":[{\"label\":\"Exit\"}]}]}",
                outerOr.getName());
    }
}
