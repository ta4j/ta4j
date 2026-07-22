/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.named;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NamedRuleTest {

    @AfterEach
    void tearDown() {
        NamedRule.unregisterImplementation(TestUnregisterRule.class);
        NamedRule.unregisterImplementation(FirstRuleHolder.DuplicateRule.class);
        NamedRule.unregisterImplementation(SecondRuleHolder.DuplicateRule.class);
    }

    @Test
    void buildLabelRejectsUnderscoreParameters() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> NamedRule.buildLabel(NamedRuleFixture.class, "ABOVE", "10_value"));

        assertThat(exception).hasMessage("Named rule parameters cannot contain underscores: parameters[1]");
    }

    @Test
    void buildLabelRejectsUnderscoreTypeNames() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> NamedRule.buildLabel(Underscored_Rule.class, "ABOVE"));

        assertThat(exception).hasMessage("Named rule class names cannot contain underscores: Underscored_Rule");
    }

    @Test
    void splitLabelRejectsBlankValues() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> NamedRule.splitLabel(""));

        assertThat(exception).hasMessage("Named rule label cannot be blank");
    }

    @Test
    void splitLabelPreservesEmptyEdgeTokens() {
        List<String> leading = NamedRule.splitLabel("_param");
        List<String> trailing = NamedRule.splitLabel("Rule_");

        assertThat(leading).containsExactly("", "param");
        assertThat(trailing).containsExactly("Rule", "");
    }

    @Test
    void varargsConstructorRebuildsNamedRule() {
        BarSeries series = new MockBarSeriesBuilder().withData(1d, 2d, 3d).build();

        NamedRuleFixture rule = new NamedRuleFixture(series, "ABOVE", "2");

        assertThat(rule.getName()).isEqualTo("NamedRuleFixture_ABOVE_2");
        assertThat(rule.getComparison()).isEqualTo(NamedRuleFixture.Comparison.ABOVE);
        assertThat(rule.getThreshold()).hasToString("2");
    }

    @Test
    void lookupAndUnregisterRoundTrip() {
        NamedRule.registerImplementation(TestUnregisterRule.class);

        assertThat(NamedRule.lookup("TestUnregisterRule")).contains(TestUnregisterRule.class);
        assertThat(NamedRule.unregisterImplementation(TestUnregisterRule.class)).isTrue();
        assertThat(NamedRule.lookup("TestUnregisterRule")).isEmpty();
    }

    @Test
    void constructionDoesNotReplaceExplicitRegistration() {
        new TestUnregisterRule(TestUnregisterRule.ClosePredicate.ALWAYS);

        assertThat(NamedRule.lookup("TestUnregisterRule")).isEmpty();
    }

    @Test
    void unregisterDoesNotRemoveDifferentImplementationWithSameSimpleName() {
        NamedRule.registerImplementation(FirstRuleHolder.DuplicateRule.class);

        assertThat(NamedRule.unregisterImplementation(SecondRuleHolder.DuplicateRule.class)).isFalse();

        assertThat(NamedRule.lookup("DuplicateRule")).contains(FirstRuleHolder.DuplicateRule.class);
    }

    @Test
    void requireRegisteredRejectsUnknownRules() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> NamedRule.requireRegistered("MissingRule"));

        assertThat(exception).hasMessage(
                "Unknown named rule 'MissingRule'. Ensure it is registered via NamedRule.registerImplementation() or initializeRegistry().");
    }

    @Test
    void concurrentRegistryInitializationWaitsForScanningToFinish() throws Exception {
        String packageName = "org.ta4j.core.rules.named.concurrentfixture";
        BlockingResourceClassLoader loader = new BlockingResourceClassLoader(packageName.replace('.', '/'));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch secondStarted = new CountDownLatch(1);
        CountDownLatch secondReturned = new CountDownLatch(1);
        try {
            Future<?> first = executor.submit(() -> initializeRegistryWith(loader, packageName));
            assertThat(loader.scanStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> second = executor.submit(() -> {
                secondStarted.countDown();
                try {
                    initializeRegistryWith(loader, packageName);
                } finally {
                    secondReturned.countDown();
                }
            });
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(secondReturned.await(250, TimeUnit.MILLISECONDS)).isFalse();

            loader.releaseScan.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            loader.releaseScan.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void ruleJsonRoundTripsThroughConcreteSerialization() {
        BarSeries series = new MockBarSeriesBuilder().withData(1d, 2d, 3d, 4d).build();
        NamedRuleFixture original = new NamedRuleFixture(series, NamedRuleFixture.Comparison.ABOVE,
                series.numFactory().numOf("2"));

        Rule restored = Rule.fromJson(series, original.toJson());

        assertThat(restored).isInstanceOf(NamedRuleFixture.class);
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.isSatisfied(3)).isTrue();
        assertThat(restored.isSatisfied(0)).isFalse();
    }

    private static final class TestUnregisterRule extends NamedRule {

        private final ClosePredicate closePredicate;

        private TestUnregisterRule(ClosePredicate closePredicate) {
            super(NamedRule.buildLabel(TestUnregisterRule.class, closePredicate.name()));
            this.closePredicate = closePredicate;
        }

        public TestUnregisterRule(String... parameters) {
            this(ClosePredicate.valueOf(parameters[0]));
        }

        @Override
        public boolean isSatisfied(int index, org.ta4j.core.TradingRecord tradingRecord) {
            return closePredicate == ClosePredicate.ALWAYS;
        }

        private enum ClosePredicate {
            ALWAYS
        }
    }

    private static final class FirstRuleHolder {

        private abstract static class DuplicateRule extends NamedRule {

            private DuplicateRule() {
                super(NamedRule.buildLabel(DuplicateRule.class));
            }
        }
    }

    private static final class SecondRuleHolder {

        private abstract static class DuplicateRule extends NamedRule {

            private DuplicateRule() {
                super(NamedRule.buildLabel(DuplicateRule.class));
            }
        }
    }

    private abstract static class Underscored_Rule extends NamedRule {

        private Underscored_Rule() {
            super("unreachable");
        }
    }

    private static void initializeRegistryWith(ClassLoader loader, String packageName) {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        try {
            thread.setContextClassLoader(loader);
            NamedRule.initializeRegistry(packageName);
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
    }

    private static final class BlockingResourceClassLoader extends ClassLoader {

        private final String blockedPath;
        private final CountDownLatch scanStarted = new CountDownLatch(1);
        private final CountDownLatch releaseScan = new CountDownLatch(1);

        private BlockingResourceClassLoader(String blockedPath) {
            super(NamedRuleTest.class.getClassLoader());
            this.blockedPath = blockedPath;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (!blockedPath.equals(name)) {
                return super.getResources(name);
            }
            scanStarted.countDown();
            try {
                if (!releaseScan.await(5, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting to release the registry scan");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting to release the registry scan", exception);
            }
            return Collections.emptyEnumeration();
        }
    }
}
