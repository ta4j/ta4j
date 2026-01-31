/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.ta4j.core.rules.AndRule;
import org.ta4j.core.rules.FixedRule;

/**
 * Regression coverage + opt-in perf harness for {@link RuleNameBenchmark}.
 */
class RuleNameBenchmarkTest {

    private static final String BENCHMARK_PROPERTY = "ta4j.runBenchmarks";

    @Test
    void eagerAndRuleTouchesChildNamesOnceAndCachesResult() {
        CountingRule left = new CountingRule("left");
        CountingRule right = new CountingRule("right");

        AndRule andRule = new AndRule(left, right);

        assertEquals(1, left.nameRequests(), "AndRule constructor should read left child name exactly once");
        assertEquals(1, right.nameRequests(), "AndRule constructor should read right child name exactly once");

        assertEquals("AndRule(left,right)", andRule.getName(), "AndRule name should include both child names");
        andRule.getName();
        assertEquals(1, left.nameRequests(), "AndRule should not re-query child names once cached");
        assertEquals(1, right.nameRequests(), "AndRule should not re-query child names once cached");
    }

    @Test
    void lazyAndRuleDefersChildNameResolutionUntilGetName() {
        CountingRule left = new CountingRule("lazyLeft");
        CountingRule right = new CountingRule("lazyRight");

        RuleNameBenchmark.LazyAndRule lazyAndRule = new RuleNameBenchmark.LazyAndRule(left, right);

        assertEquals(0, left.nameRequests(), "LazyAndRule should not touch child names in constructor");
        assertEquals(0, right.nameRequests(), "LazyAndRule should not touch child names in constructor");

        assertEquals("LazyAndRule(lazyLeft,lazyRight)", lazyAndRule.getName(),
                "LazyAndRule should build name lazily on first access");
        assertEquals(1, left.nameRequests(), "LazyAndRule should resolve left name once when needed");
        assertEquals(1, right.nameRequests(), "LazyAndRule should resolve right name once when needed");
        lazyAndRule.getName();
        assertEquals(1, left.nameRequests(), "LazyAndRule should not re-resolve after caching the name");
        assertEquals(1, right.nameRequests(), "LazyAndRule should not re-resolve after caching the name");
    }

    @Test
    void noNameLeakAndRuleAvoidsChildNameResolutionEntirely() {
        CountingRule left = new CountingRule("ignoredLeft");
        CountingRule right = new CountingRule("ignoredRight");

        RuleNameBenchmark.NoNameLeakAndRule noLeakRule = new RuleNameBenchmark.NoNameLeakAndRule(left, right);

        assertEquals("NoNameLeakAndRule(CountingRule,CountingRule)", noLeakRule.getName(),
                "NoNameLeakAndRule should use child types without touching getName()");
        assertEquals(0, left.nameRequests(), "Child names should not be accessed for no-leak variant");
        assertEquals(0, right.nameRequests(), "Child names should not be accessed for no-leak variant");
    }

    @Test
    @Tag("benchmark")
    @EnabledIfSystemProperty(named = BENCHMARK_PROPERTY, matches = "true")
    void benchmarksRunWhenExplicitlyEnabled() throws Exception {
        int threads = Math.max(8, Runtime.getRuntime().availableProcessors());
        RuleNameBenchmark.main(new String[] { Integer.toString(threads), "2", "20000" });
    }

    private static final class CountingRule extends FixedRule {

        private final AtomicInteger nameRequests = new AtomicInteger();
        private final String label;

        private CountingRule(String label) {
            super(0);
            this.label = label;
            setName(label);
        }

        @Override
        public String getName() {
            nameRequests.incrementAndGet();
            return super.getName();
        }

        int nameRequests() {
            return nameRequests.get();
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
