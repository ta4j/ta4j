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
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
        assertEquals("{\"type\":\"AndRule\",\"components\":[{\"label\":\"Entry\"},{\"label\":\"Exit\"}]}",
                andRule.getName());

        Rule orRule = new OrRule(entryRule, exitRule);
        assertEquals("{\"type\":\"OrRule\",\"components\":[{\"label\":\"Entry\"},{\"label\":\"Exit\"}]}",
                orRule.getName());

        Rule xorRule = new XorRule(entryRule, exitRule);
        assertEquals("{\"type\":\"XorRule\",\"components\":[{\"label\":\"Entry\"},{\"label\":\"Exit\"}]}",
                xorRule.getName());

        Rule notRule = new NotRule(entryRule);
        assertEquals("{\"type\":\"NotRule\",\"components\":[{\"label\":\"Entry\"}]}", notRule.getName());
    }

    @Test
    public void customNameOnCompositeRuleOverridesGeneratedJson() {
        Rule entryRule = new FixedRule(1);
        entryRule.setName("Entry");
        Rule exitRule = new FixedRule(2);
        exitRule.setName("Exit");

        AndRule andRule = new AndRule(entryRule, exitRule);
        String originalEntryName = entryRule.getName();
        String originalExitName = exitRule.getName();
        String originalCompositeName = andRule.getName();

        // Verify initial state: composite has generated JSON name
        assertEquals("{\"type\":\"AndRule\",\"components\":[{\"label\":\"Entry\"},{\"label\":\"Exit\"}]}",
                originalCompositeName);

        // Set custom name on composite
        andRule.setName("My Custom Composite Rule");

        // Verify composite name is now the custom name
        assertEquals("My Custom Composite Rule", andRule.getName());

        // Verify child rule names remain unchanged
        assertEquals(originalEntryName, entryRule.getName());
        assertEquals(originalExitName, exitRule.getName());
        assertEquals("Entry", entryRule.getName());
        assertEquals("Exit", exitRule.getName());

        // Verify child rules accessed through composite also have unchanged names
        assertEquals(originalEntryName, andRule.getRule1().getName());
        assertEquals(originalExitName, andRule.getRule2().getName());
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
                "{\"type\":\"OrRule\",\"components\":[{\"type\":\"AndRule\",\"components\":[{\"label\":\"Entry\"},{\"label\":\"Exit\"}]},{\"type\":\"NotRule\",\"components\":[{\"label\":\"Exit\"}]}]}",
                outerOr.getName());
    }

    @Test
    public void defaultNameIsCachedUntilReset() {
        CountingRule rule = new CountingRule();

        assertEquals("{\"type\":\"CountingRule\"}", rule.getName());
        assertEquals("{\"type\":\"CountingRule\"}", rule.getName());
        assertEquals(1, rule.getCreateDefaultNameCalls());

        rule.setName("Custom");
        assertEquals("Custom", rule.getName());
        assertEquals(1, rule.getCreateDefaultNameCalls());

        rule.setName(null);
        assertEquals("{\"type\":\"CountingRule\"}", rule.getName());
        assertEquals(2, rule.getCreateDefaultNameCalls());
    }

    @Test
    public void defaultNameComputationIsSynchronized() throws InterruptedException {
        CountingRule rule = new CountingRule();
        int threads = 32;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        Runnable task = () -> {
            try {
                start.await();
                rule.getName();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        };
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(task, "rule-name-sync-" + i);
            t.start();
        }
        start.countDown();
        boolean finished = done.await(10, TimeUnit.SECONDS);
        assertTrue("Threads did not finish in time", finished);
        assertEquals("{\"type\":\"CountingRule\"}", rule.getName());
        assertEquals("Default name should be built exactly once even under contention", 1,
                rule.getCreateDefaultNameCalls());
    }

    private static final class CountingRule extends AbstractRule {

        private int createDefaultNameCalls;

        @Override
        protected String createDefaultName() {
            createDefaultNameCalls++;
            return super.createDefaultName();
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            return false;
        }

        int getCreateDefaultNameCalls() {
            return createDefaultNameCalls;
        }
    }
}
