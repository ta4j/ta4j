/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.time.Instant;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ExecutionMatchPolicy;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.TradingStatementExecutionResult;
import org.ta4j.core.backtest.TradingStatementExecutionResult.RankingProfile;
import org.ta4j.core.backtest.TradingStatementExecutionResult.WeightedCriterion;
import org.ta4j.core.criteria.CalmarRatioCriterion;
import org.ta4j.core.criteria.AbstractAnalysisCriterion;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.SortinoRatioCriterion;
import org.ta4j.core.criteria.Statistics;
import org.ta4j.core.criteria.drawdown.MaximumAbsoluteDrawdownCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownBarLengthCriterion;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.drawdown.Drawdown;
import org.ta4j.core.criteria.drawdown.MonteCarloMaximumDrawdownCriterion;
import org.ta4j.core.criteria.drawdown.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.BarSeriesUtils;
import org.ta4j.core.reports.BaseTradingStatement;
import org.ta4j.core.reports.TradingStatement;

/**
 * Opt-in, deterministic measurement of the real criterion and ranking APIs.
 * Results go to {@code target/equity-curve-benchmark.csv}; no timing threshold
 * is asserted. Fixture construction, warmups and correctness checks are outside
 * the measured interval. Allocation is the executing thread's allocated bytes,
 * including ranking output; it is not retained heap or a process-wide measure.
 */
@Tag("benchmark")
class EquityCurveBenchmarkTest {

    private static volatile Object consumed;

    @Test
    void measuresCriteriaAndRanking() throws IOException {
        int warmups = Integer.getInteger("equity.benchmark.warmups", 2);
        boolean rankingOnly = Boolean.getBoolean("equity.benchmark.rankingOnly");
        int repetitions = Integer.getInteger("equity.benchmark.repetitions", 3);
        assertTrue(warmups > 0 && repetitions > 0, "warmups and repetitions must be positive");
        com.sun.management.ThreadMXBean allocation = ManagementFactory
                .getPlatformMXBean(com.sun.management.ThreadMXBean.class);
        assertTrue(allocation != null && allocation.isThreadAllocatedMemorySupported(),
                "measurement requires JVM thread allocation accounting");
        allocation.setThreadAllocatedMemoryEnabled(true);
        List<String> rows = new ArrayList<>();
        rows.add("factory,bars,positions,operation,repetition,nanoseconds,allocated_bytes");
        Path output = Path.of("target", "equity-curve-benchmark.csv");
        Files.createDirectories(output.getParent());
        Files.write(output, rows);
        Files.writeString(Path.of("target", "equity-curve-benchmark-environment.txt"),
                "seed=" + EquityCurveTestSupport.SEED + "\nwarmups=" + warmups + "\nrepetitions=" + repetitions
                        + "\nrankingOnly=" + rankingOnly + "\nmonteCarloIterations=32\nmonteCarloPathBlocks=16\njava="
                        + System.getProperty("java.runtime.version") + "\nvm=" + System.getProperty("java.vm.name")
                        + "\nos=" + System.getProperty("os.name") + " " + System.getProperty("os.version") + " "
                        + System.getProperty("os.arch") + "\njvmArgs="
                        + ManagementFactory.getRuntimeMXBean().getInputArguments() + "\nstarted=" + Instant.now()
                        + "\nsourceRevision=" + System.getProperty("equity.benchmark.revision", "unspecified")
                        + "\navailableProcessors=" + Runtime.getRuntime().availableProcessors() + "\nmaxHeapBytes="
                        + Runtime.getRuntime().maxMemory() + "\n");
        List<AnalysisCriterion> criteria = List.of(new MaximumDrawdownCriterion(),
                new MaximumAbsoluteDrawdownCriterion(), new MaximumDrawdownBarLengthCriterion(),
                new MonteCarloMaximumDrawdownCriterion(32, 16, EquityCurveTestSupport.SEED, Statistics.P95),
                new ReturnOverMaxDrawdownCriterion(), new CalmarRatioCriterion(), new SharpeRatioCriterion(),
                new SortinoRatioCriterion());
        for (NumFactory factory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            for (int[] workload : new int[][] { { 1_100, 7 }, { 100_000, 500 } }) {
                int bars = workload[0];
                int positions = workload[1];
                BarSeries series = EquityCurveTestSupport.series(factory, bars, EquityCurveTestSupport.SEED);
                List<TradingStatement> statements = new ArrayList<>();
                for (int index = 0; index < 20; index++) {
                    BaseTradingRecord record = EquityCurveTestSupport.record(series, positions,
                            ExecutionMatchPolicy.FIFO, EquityCurveTestSupport.SEED + index);
                    assertEquals(positions, record.getPositions().size() + record.getOpenPositions().size());
                    statements.add(new BaseTradingStatement(null, record, null, null));
                }
                Result result = new Result(series, List.copyOf(statements));
                if (!rankingOnly) {
                    for (AnalysisCriterion criterion : criteria) {
                        measure(rows, allocation, factory, bars, positions, criterion.getClass().getSimpleName(),
                                () -> criterion.calculate(series, statements.getFirst().getTradingRecord()), warmups,
                                repetitions);
                    }
                }
                List<WeightedCriterion> weights = criteria.stream()
                        .map(criterion -> new WeightedCriterion(criterion, factory.one()))
                        .toList();
                RankingProfile profile = RankingProfile.weighted(weights);
                measure(rows, allocation, factory, bars, positions, "ranking20",
                        () -> result.rankTradingStatements(profile), warmups, repetitions);
                SharingPrototype prototype = new SharingPrototype(series, criteria);
                RankingProfile candidateProfile = RankingProfile.weighted(prototype.criteria.stream()
                        .map(criterion -> new WeightedCriterion(criterion, factory.one()))
                        .toList());
                Supplier<List<TradingStatementExecutionResult.RankedTradingStatement>> candidate = () -> {
                    try {
                        return result.rankTradingStatements(candidateProfile);
                    } finally {
                        prototype.curves.clear();
                    }
                };
                List<TradingStatementExecutionResult.RankedTradingStatement> baselineRows = result
                        .rankTradingStatements(profile);
                List<TradingStatementExecutionResult.RankedTradingStatement> candidateRows = candidate.get();
                assertTrue(prototype.curves.isEmpty(), "sharing state must be released after ranking");
                assertEquals(1, prototype.peakCurves, "sharing must retain only the current statement");
                assertEquals(20, candidateRows.size());
                for (int index = 0; index < baselineRows.size(); index++) {
                    TradingStatementExecutionResult.RankedTradingStatement baseline = baselineRows.get(index);
                    TradingStatementExecutionResult.RankedTradingStatement shared = candidateRows.get(index);
                    assertEquals(baseline.originalIndex(), shared.originalIndex(), "ranking order");
                    assertEquals(baseline.compositeScore(), shared.compositeScore(), "composite score");
                    for (int criterion = 0; criterion < criteria.size(); criterion++) {
                        assertEquals(baseline.rawScores().get(criteria.get(criterion)),
                                shared.rawScores().get(prototype.criteria.get(criterion)), "raw score");
                        assertEquals(baseline.normalizedScores().get(criteria.get(criterion)),
                                shared.normalizedScores().get(prototype.criteria.get(criterion)), "normalized score");
                    }
                }
                measure(rows, allocation, factory, bars, positions, "ranking20-shared-drawdowns", candidate, warmups,
                        repetitions);
            }
        }

    }

    private static void measure(List<String> rows, com.sun.management.ThreadMXBean allocation, NumFactory factory,
            int bars, int positions, String operation, Supplier<?> action, int warmups, int repetitions)
            throws IOException {
        Object expected = action.get();
        for (int iteration = 0; iteration < warmups; iteration++) {
            consumed = action.get();
            assertEquals(expected, consumed, operation + " warmup");
        }
        long thread = Thread.currentThread().threadId();
        for (int iteration = 0; iteration < repetitions; iteration++) {
            long allocatedBefore = allocation.getThreadAllocatedBytes(thread);
            long started = System.nanoTime();
            Object value = action.get();
            long elapsed = System.nanoTime() - started;
            long allocated = allocation.getThreadAllocatedBytes(thread) - allocatedBefore;
            consumed = value;
            assertEquals(expected, value, operation + " repetition " + iteration);
            assertTrue(allocated >= 0, "allocation accounting became unavailable");
            rows.add(factory.getClass().getSimpleName() + "," + bars + "," + positions + "," + operation + ","
                    + iteration + "," + elapsed + "," + allocated);
            Files.write(Path.of("target", "equity-curve-benchmark.csv"), rows);
        }
    }

    /**
     * Bounded static-input experiment: shares one CashFlow between maximum
     * drawdown, duration, RoMaD and Calmar. The latter two reproduce only their
     * default immutable-fixture formula. Monte Carlo, Sharpe, Sortino and absolute
     * drawdown use the original implementations. This measures a conservative
     * subset, not a general-purpose cache or a substitute for mutation contracts.
     */
    private static final class SharingPrototype {
        private final Map<TradingRecord, CashFlow> curves = new IdentityHashMap<>();
        private int peakCurves;
        private final List<AnalysisCriterion> criteria = new ArrayList<>();

        private SharingPrototype(BarSeries series, List<AnalysisCriterion> originals) {
            for (AnalysisCriterion original : originals) {
                if (!(original instanceof MaximumDrawdownCriterion
                        || original instanceof MaximumDrawdownBarLengthCriterion
                        || original instanceof ReturnOverMaxDrawdownCriterion
                        || original instanceof CalmarRatioCriterion)) {
                    criteria.add(original);
                    continue;
                }
                criteria.add(new AbstractAnalysisCriterion() {
                    @Override
                    public Num calculate(BarSeries input, TradingRecord record) {
                        // Ranking evaluates every criterion for one statement
                        // before moving to the next. Release its predecessor here.
                        if (!curves.containsKey(record)) {
                            curves.clear();
                        }
                        CashFlow curve = curves.computeIfAbsent(record, key -> new CashFlow(series, key));
                        peakCurves = Math.max(peakCurves, curves.size());
                        if (original instanceof MaximumDrawdownBarLengthCriterion) {
                            return Drawdown.length(series, record, curve);
                        }
                        Num drawdown = Drawdown.amount(series, record, curve);
                        if (original instanceof MaximumDrawdownCriterion) {
                            return drawdown;
                        }
                        Num one = series.numFactory().one();
                        Num growth = curve.getValue(curve.getEndIndex()).dividedBy(curve.getBaselineValue());
                        Num netReturn;
                        if (original instanceof CalmarRatioCriterion) {
                            Num years = BarSeriesUtils.deltaYears(series, curve.getBeginIndex(), curve.getEndIndex());
                            netReturn = growth.pow(one.dividedBy(years)).minus(one);
                        } else {
                            netReturn = growth.minus(one);
                        }
                        return drawdown.isZero() ? netReturn : netReturn.dividedBy(drawdown);
                    }

                    @Override
                    public Num calculate(BarSeries input, Position position) {
                        return original.calculate(input, position);
                    }

                    @Override
                    public boolean betterThan(Num first, Num second) {
                        return original.betterThan(first, second);
                    }
                });
            }
        }
    }

    /** Uses the production default ranking implementation without a backtest. */
    private record Result(BarSeries barSeries,
            List<TradingStatement> tradingStatements) implements TradingStatementExecutionResult<Void> {
        @Override
        public Void runtimeReport() {
            return null;
        }
    }
}
