/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RuleNameTest {

    @Test
    public void defaultNameFallsBackToClassSimpleName() {
        Rule rule = new FixedRule(1);

        assertEquals("FixedRule", rule.getName());
        assertEquals("FixedRule", rule.toString());
    }

    @Test
    public void customNameCanBeSetAndReset() {
        Rule rule = new FixedRule(1);

        rule.setName("My Custom Rule");
        assertEquals("My Custom Rule", rule.getName());
        assertEquals("My Custom Rule", rule.toString());

        rule.setName(null);
        assertEquals("FixedRule", rule.getName());

        rule.setName("   ");
        assertEquals("FixedRule", rule.getName());
    }

    @Test
    public void compositeRulesCombineChildNames() {
        Rule entryRule = new FixedRule(1);
        entryRule.setName("Entry");
        Rule exitRule = new FixedRule(2);
        exitRule.setName("Exit");

        Rule andRule = new AndRule(entryRule, exitRule);
        assertEquals("AndRule(Entry,Exit)", andRule.getName());

        Rule orRule = new OrRule(entryRule, exitRule);
        assertEquals("OrRule(Entry,Exit)", orRule.getName());

        Rule xorRule = new XorRule(entryRule, exitRule);
        assertEquals("XorRule(Entry,Exit)", xorRule.getName());

        Rule notRule = new NotRule(entryRule);
        assertEquals("NotRule(Entry)", notRule.getName());
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

        // Verify initial state: composite has generated simple name
        assertEquals("AndRule(Entry,Exit)", originalCompositeName);

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

        assertEquals("OrRule(AndRule(Entry,Exit),NotRule(Exit))", outerOr.getName());
    }

    @Test
    public void defaultNameIsRecomputedWhenNeeded() {
        CountingRule rule = new CountingRule();

        assertEquals("CountingRule", rule.getName());
        assertEquals("CountingRule", rule.getName());
        assertEquals(2, rule.getCreateDefaultNameCalls());

        rule.setName("Custom");
        assertEquals("Custom", rule.getName());
        assertEquals(2, rule.getCreateDefaultNameCalls());

        rule.setName(null);
        assertEquals("CountingRule", rule.getName());
        assertEquals(3, rule.getCreateDefaultNameCalls());
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
        assertEquals("CountingRule", rule.getName());
        assertEquals("Default name should be built once per call under contention plus final read", threads + 1,
                rule.getCreateDefaultNameCalls());
    }

    @Test
    public void customNameVisibleAcrossThreadsWithoutExplicitSync() throws Exception {
        CountingRule rule = new CountingRule();
        String customName = "CustomName-" + System.nanoTime();
        CountDownLatch readerDone = new CountDownLatch(1);
        AtomicInteger seenCustom = new AtomicInteger(0);

        Thread reader = new Thread(() -> {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (System.nanoTime() < deadline) {
                if (customName.equals(rule.getName())) {
                    seenCustom.incrementAndGet();
                    break;
                }
                Thread.yield();
            }
            readerDone.countDown();
        }, "custom-name-reader");

        reader.start();
        // Writer thread sets the custom name after a short delay to avoid any implicit
        // happens-before with reader start.
        Thread writer = new Thread(() -> {
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            rule.setName(customName);
        }, "custom-name-writer");
        writer.start();

        writer.join(1000);
        readerDone.await(3, TimeUnit.SECONDS);

        assertTrue("Custom name should become visible to reader thread", seenCustom.get() > 0);
        assertEquals(customName, rule.getName());
    }

    private static final class CountingRule extends AbstractRule {

        private final AtomicInteger createDefaultNameCalls = new AtomicInteger();

        @Override
        protected String createDefaultName() {
            createDefaultNameCalls.incrementAndGet();
            return super.createDefaultName();
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            return false;
        }

        int getCreateDefaultNameCalls() {
            return createDefaultNameCalls.get();
        }
    }
}
