/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.rules;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.rules.AbstractRule;
import org.ta4j.core.rules.AndRule;
import org.ta4j.core.rules.FixedRule;

/**
 * Comparative benchmark for eager vs lazy rule naming.
 * <p>
 * Scenarios:
 * <ul>
 * <li>Eagerly setting AndRule's name in constructor and then calling getName()
 * (current default)</li>
 * <li>Lazily setting/getting AndRule's name in getName() (i.e. don't set name
 * in constructor)</li>
 * <li>Eagerly setting the name in constructor (using class names, not calling
 * getName() on children) but never calling getName() - demonstrates that JIT
 * may optimize away string construction overhead when the name is never
 * accessed, though the volatile write itself cannot be eliminated</li>
 * </ul>
 *
 * @since 0.22.0
 */
public class RuleNameBenchmark {

    private static final Logger LOG = LogManager.getLogger(RuleNameBenchmark.class);

    private static final int DEFAULT_THREADS = Math.max(8, Runtime.getRuntime().availableProcessors());
    private static final int DEFAULT_RULES_PER_THREAD = 100_000;
    private static final int DEFAULT_BATCHES = 3;

    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_THREADS;
        int batches = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_BATCHES;
        int rulesPerThread = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_RULES_PER_THREAD;

        new RuleNameBenchmark().run(threads, batches, rulesPerThread);
    }

    private void run(int threads, int batches, int rulesPerThread) throws Exception {
        String formattedRulesPerThread = NumberFormat.getIntegerInstance(Locale.US).format(rulesPerThread);
        LOG.info("Starting rule construction benchmark: threads={}, batches={}, rulesPerThread={}", threads, batches,
                formattedRulesPerThread);

        Map<String, ScenarioStats> statsByScenario = new HashMap<>();
        for (int batch = 1; batch <= batches; batch++) {
            runScenario("Eager setName() + getName()", threads, rulesPerThread, this::buildEagerRule, true, batch,
                    statsByScenario);
            runScenario("Lazy setName() + getName()", threads, rulesPerThread, this::buildLazyRule, true, batch,
                    statsByScenario);
            runScenario("Eager setName() + No getName()", threads, rulesPerThread, this::buildEagerRuleNoChildNames,
                    false, batch, statsByScenario);
        }

        LOG.info("=== Rule construction throughput summary (threads={}, batches={}, rulesPerThread={}) ===", threads,
                batches, formattedRulesPerThread);
        statsByScenario.forEach((label, s) -> {
            double avgThroughput = s.totalThroughput / s.runs;
            Duration avgDuration = Duration.ofNanos((long) (s.totalDurationNanos / s.runs));
            LOG.info(
                    "{}: runs={}, avgThroughput={} rules/s, minThroughput={}, maxThroughput={}, avgDuration={}, totalChecksum={}",
                    label, s.runs, NumberFormat.getIntegerInstance(Locale.US).format(avgThroughput),
                    NumberFormat.getIntegerInstance(Locale.US).format(s.minThroughput),
                    NumberFormat.getIntegerInstance(Locale.US).format(s.maxThroughput), avgDuration, s.totalChecksum);
        });
    }

    private void runScenario(String label, int threads, int rulesPerThread, Supplier<Rule> factory, boolean callName,
            int batch, Map<String, ScenarioStats> statsByScenario) throws Exception {
        long started = System.nanoTime();
        long checksum = exercise(threads, rulesPerThread, factory, callName);
        long elapsedNanos = System.nanoTime() - started;
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double constructions = (double) threads * rulesPerThread;
        double throughput = constructions / elapsedSeconds;

        LOG.debug("Batch {} [{}]: duration={}, throughput={} rules/s, checksum={}", batch, label,
                Duration.ofNanos(elapsedNanos), NumberFormat.getIntegerInstance(Locale.US).format(throughput),
                checksum);

        ScenarioStats stats = statsByScenario.computeIfAbsent(label, k -> new ScenarioStats());
        stats.runs++;
        stats.totalDurationNanos += elapsedNanos;
        stats.totalThroughput += throughput;
        stats.totalChecksum += checksum;
        stats.minThroughput = Math.min(stats.minThroughput, throughput);
        stats.maxThroughput = Math.max(stats.maxThroughput, throughput);
    }

    private long exercise(int threads, int rulesPerThread, Supplier<Rule> factory, boolean callName) throws Exception {
        var pool = Executors.newFixedThreadPool(threads);
        try {
            List<CompletableFuture<Long>> futures = new ArrayList<>(threads);
            for (int t = 0; t < threads; t++) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    long localChecksum = 0;
                    for (int i = 0; i < rulesPerThread; i++) {
                        Rule r = factory.get();
                        if (callName) {
                            String name = r.getName();
                            localChecksum += name.hashCode();
                        } else {
                            // Touch type to prevent dead-code elimination without invoking getName()
                            localChecksum += r.getClass().hashCode();
                        }
                    }
                    return localChecksum;
                }, pool));
            }
            long total = 0;
            for (CompletableFuture<Long> future : futures) {
                total += future.get(5, TimeUnit.MINUTES);
            }
            return total;
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }
    }

    private Rule buildEagerRule() {
        Rule left = new FixedRule(1);
        Rule right = new FixedRule(2);
        return new AndRule(left, right);
    }

    Rule buildLazyRule() {
        Rule left = new FixedRule(1);
        Rule right = new FixedRule(2);
        return new LazyAndRule(left, right);
    }

    Rule buildEagerRuleNoChildNames() {
        Rule left = new FixedRule(1);
        Rule right = new FixedRule(2);
        return new NoNameLeakAndRule(left, right);
    }

    /**
     * Lazy variant of AndRule that defers name construction to getName().
     */
    static final class LazyAndRule extends AbstractRule {

        private final Rule rule1;
        private final Rule rule2;

        LazyAndRule(Rule rule1, Rule rule2) {
            this.rule1 = rule1;
            this.rule2 = rule2;
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            boolean satisfied = rule1.isSatisfied(index, tradingRecord) && rule2.isSatisfied(index, tradingRecord);
            traceIsSatisfied(index, satisfied);
            return satisfied;
        }

        @Override
        protected String createDefaultName() {
            setName(createCompositeName(getClass().getSimpleName(), rule1, rule2));
            return getName();
        }
    }

    /**
     * Eager variant that constructs the name in the constructor but avoids calling
     * getName() on children (to prevent triggering their lazy name resolution).
     * This demonstrates that even when setName() is called eagerly, if getName() is
     * never invoked on the rule, the JIT compiler may be able to optimize away some
     * of the string construction overhead, though the volatile write itself cannot
     * be eliminated.
     * <p>
     * Note: The volatile write in setName() is an observable side effect that
     * prevents complete dead-code elimination, but the string construction work
     * (StringBuilder operations) may still be optimized if the result is never
     * read.
     */
    static final class NoNameLeakAndRule extends AbstractRule {

        private final Rule rule1;
        private final Rule rule2;

        NoNameLeakAndRule(Rule rule1, Rule rule2) {
            this.rule1 = rule1;
            this.rule2 = rule2;
            // Eagerly construct and set the name, but use class names instead of
            // calling getName() on children to avoid triggering their lazy name resolution.
            // This allows us to test if JIT can eliminate the string construction
            // overhead when the name is never accessed.
            setName(buildNameWithoutChildNames());
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            boolean satisfied = rule1.isSatisfied(index, tradingRecord) && rule2.isSatisfied(index, tradingRecord);
            traceIsSatisfied(index, satisfied);
            return satisfied;
        }

        private String buildNameWithoutChildNames() {
            StringBuilder builder = new StringBuilder(getClass().getSimpleName());
            builder.append('(');
            builder.append(rule1 == null ? "null" : rule1.getClass().getSimpleName());
            builder.append(',');
            builder.append(rule2 == null ? "null" : rule2.getClass().getSimpleName());
            builder.append(')');
            return builder.toString();
        }
    }

    private static final class ScenarioStats {
        int runs;
        double totalDurationNanos;
        double totalThroughput;
        double minThroughput = Double.MAX_VALUE;
        double maxThroughput = Double.MIN_VALUE;
        long totalChecksum;
    }
}
