/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.TraceTestLogger;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.acceleration.AccelerationRuntime;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.indicators.forecast.EwmaReturnForecastStateIndicator;
import org.ta4j.core.indicators.forecast.MonteCarloPriceForecastIndicator;
import org.ta4j.core.indicators.forecast.projection.Forecast;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.LogReturnIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AbstractRule;

import com.google.gson.GsonBuilder;

@Tag("benchmark")
@Tag("requires-metal")
class MetalBacktestBenchmarkTest {

    private static final int BAR_COUNT = 4_096;
    private static final int DECISIONS = 256;
    private static final int PATHS = 2_048;
    private static final int HORIZON = 32;
    private static final int LOOKBACK = 256;
    private static final int TRIALS = 5;
    private static final long SEED = 42L;

    @BeforeAll
    static void enableApproximateOptIn() {
        System.setProperty(MetalAccelerationProvider.APPROXIMATE_PROPERTY, "true");
        System.setProperty("ta4j.forecast.rngVersion", "1");
    }

    @Test
    void compareTransparentScalarAndMetalBacktests() throws IOException {
        String library = System.getProperty(MetalAccelerationProviderFactory.LIBRARY_PROPERTY);
        assertThat(library).as(MetalAccelerationProviderFactory.LIBRARY_PROPERTY).isNotBlank();
        CliIndicatorAccelerationService.useQualificationProviderForTests("metal");
        TraceTestLogger logs = new TraceTestLogger();
        logs.open();
        logs.setLoggerLevel(AccelerationRuntime.class, org.apache.logging.log4j.Level.DEBUG);
        try {
            run("off", logs);
            run("auto", logs);
            List<Trial> scalar = new ArrayList<>();
            List<Trial> metal = new ArrayList<>();
            for (int i = 0; i < TRIALS; i++) {
                scalar.add(run("off", logs));
                metal.add(run("auto", logs));
            }
            assertParity(scalar, metal);
            assertThat(metal)
                    .allSatisfy(trial -> assertThat(trial.runtimeDiagnostic()).contains("effectiveBackend=metal"));

            Trial scalarMedian = median(scalar);
            Trial metalMedian = median(metal);
            double speedup = (double) scalarMedian.elapsedNanos() / metalMedian.elapsedNanos();
            Report report = new Report("cf336-transparent-metal-backtest-v2",
                    new Spec(BAR_COUNT, DECISIONS, PATHS, HORIZON, LOOKBACK, TRIALS, SEED), scalarMedian, metalMedian,
                    speedup, scalar, metal);
            Path output = outputPath();
            Files.createDirectories(output.getParent());
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n",
                    StandardCharsets.UTF_8);
        } finally {
            logs.close();
            System.clearProperty(AccelerationRuntime.PROPERTY);
            System.clearProperty("ta4j.forecast.rngVersion");
            CliIndicatorAccelerationService.clearQuarantineForTests();
        }
    }

    private static Trial run(String mode, TraceTestLogger logs) {
        System.setProperty(AccelerationRuntime.PROPERTY, mode);
        logs.clear();
        Workload workload = workload();
        long started = System.nanoTime();
        TradingRecord record = new BarSeriesManager(workload.series(), new TradeOnCurrentCloseModel())
                .run(workload.strategy());
        long elapsed = System.nanoTime() - started;
        List<String> operations = record.getPositions()
                .stream()
                .map(position -> position.getEntry().getIndex() + ":"
                        + (position.getExit() == null ? "open" : position.getExit().getIndex()))
                .toList();
        int barsInMarket = record.getPositions()
                .stream()
                .filter(position -> position.getExit() != null)
                .mapToInt(position -> position.getExit().getIndex() - position.getEntry().getIndex())
                .sum();
        double equity = record.getPositions()
                .stream()
                .filter(position -> position.getExit() != null)
                .mapToDouble(position -> position.getExit().getNetPrice().doubleValue()
                        / position.getEntry().getNetPrice().doubleValue())
                .reduce(1d, (left, right) -> left * right);
        String diagnostic = logs.getLogOutput()
                .lines()
                .filter(line -> line.contains("ta4j acceleration requested="))
                .reduce((first, second) -> second)
                .orElse("");
        return new Trial(mode, elapsed, record.getPositionCount(), barsInMarket, equity, operations, diagnostic);
    }

    private static Workload workload() {
        double[] prices = new double[BAR_COUNT];
        prices[0] = 100d;
        for (int i = 1; i < prices.length; i++) {
            double logReturn = 0.0002d + 0.006d * Math.sin(i * 0.031d) + 0.003d * Math.cos(i * 0.071d);
            prices[i] = prices[i - 1] * Math.exp(logReturn);
        }
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(prices)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        LogReturnIndicator returns = new LogReturnIndicator(close);
        EwmaReturnForecastStateIndicator state = new EwmaReturnForecastStateIndicator(returns, LOOKBACK, 0.94d);
        MonteCarloPriceForecastIndicator forecast = MonteCarloPriceForecastIndicator.builder(close, state)
                .horizon(HORIZON)
                .iterationCount(PATHS)
                .lookbackBarCount(LOOKBACK)
                .seed(SEED)
                .quantiles(0.25d, 0.5d, 0.75d)
                .build();
        int firstDecision = BAR_COUNT - DECISIONS;
        Strategy strategy = new BaseStrategy(new ForecastRule(forecast, close, firstDecision, true),
                new ForecastRule(forecast, close, firstDecision, false));
        return new Workload(series, strategy);
    }

    private static void assertParity(List<Trial> scalar, List<Trial> metal) {
        Trial expected = scalar.getFirst();
        for (Trial trial : scalar) {
            assertOutcome(trial, expected);
        }
        for (Trial trial : metal) {
            assertOutcome(trial, expected);
        }
    }

    private static void assertOutcome(Trial actual, Trial expected) {
        assertThat(actual.positions()).isEqualTo(expected.positions());
        assertThat(actual.barsInMarket()).isEqualTo(expected.barsInMarket());
        assertThat(actual.operations()).isEqualTo(expected.operations());
        assertThat(actual.endingEquity()).isEqualTo(expected.endingEquity());
    }

    private static Trial median(List<Trial> trials) {
        return trials.stream()
                .sorted(Comparator.comparingLong(Trial::elapsedNanos))
                .skip(trials.size() / 2L)
                .findFirst()
                .orElseThrow();
    }

    private static Path outputPath() {
        Path workingDirectory = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        Path root = workingDirectory;
        while (!Files.exists(root.resolve(".git"))) {
            Path parent = root.getParent();
            if (parent == null) {
                root = workingDirectory;
                break;
            }
            root = parent;
        }
        String defaultOutput = root
                .resolve(
                        Path.of(".agents", "benchmarks", "cf-336-validation", "cf-336-transparent-metal-backtest.json"))
                .toString();
        String configured = System.getProperty("ta4j.acceleration.benchmark.output", defaultOutput);
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private record Workload(BarSeries series, Strategy strategy) {
    }

    private record Spec(int barCount, int decisions, int paths, int horizon, int lookback, int trialCount, long seed) {
    }

    private record Trial(String mode, long elapsedNanos, int positions, int barsInMarket, double endingEquity,
            List<String> operations, String runtimeDiagnostic) {
    }

    private record Report(String schemaVersion, Spec spec, Trial scalarMedian, Trial metalMedian, double speedup,
            List<Trial> scalarTrials, List<Trial> metalTrials) {
    }

    private static final class ForecastRule extends AbstractRule {

        private final MonteCarloPriceForecastIndicator forecast;
        private final ClosePriceIndicator close;
        private final int firstDecision;
        private final boolean entry;

        private ForecastRule(MonteCarloPriceForecastIndicator forecast, ClosePriceIndicator close, int firstDecision,
                boolean entry) {
            this.forecast = forecast;
            this.close = close;
            this.firstDecision = firstDecision;
            this.entry = entry;
        }

        @Override
        public boolean isSatisfied(int index, TradingRecord tradingRecord) {
            if (index < firstDecision) {
                return false;
            }
            Forecast value = forecast.getValue(index);
            if (!value.isStable()) {
                return false;
            }
            Num multiplier = close.getBarSeries().numFactory().numOf(entry ? 1.01d : 0.99d);
            Num threshold = close.getValue(index).multipliedBy(multiplier);
            return entry ? value.quantile(0.75d).isGreaterThan(threshold) : value.quantile(0.25d).isLessThan(threshold);
        }
    }
}
