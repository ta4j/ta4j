/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.lppl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.lppl.LPPLExhaustionSide;

import com.google.gson.Gson;

/**
 * Package-private calibration support for {@link SectorLPPLExhaustionMapDemo}.
 */
final class SectorLPPLBenchmark {

    private static final Logger LOG = LogManager.getLogger(SectorLPPLBenchmark.class);
    private static final Object PROGRESS_LOCK = new Object();
    private static final String MANIFEST_NAME = "lppl-benchmark-baseline.json";
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new Gson();

    private SectorLPPLBenchmark() {
    }

    static Result run(List<SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded,
            List<SectorLPPLExhaustionMapDemo.InstrumentSnapshot> observed,
            SectorLPPLExhaustionMapDemo.AnalysisProfile profile, Path outputDirectory) throws IOException {
        return run(loaded, observed, profile, outputDirectory, BenchmarkConfig.production());
    }

    static Result run(List<SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded,
            List<SectorLPPLExhaustionMapDemo.InstrumentSnapshot> observed,
            SectorLPPLExhaustionMapDemo.AnalysisProfile profile, Path outputDirectory, BenchmarkConfig config)
            throws IOException {
        Files.createDirectories(outputDirectory);
        Path progress = outputDirectory.resolve("lppl-benchmark-progress.csv");
        Files.writeString(progress, "phase,group,ticker,completed,total\n", StandardCharsets.UTF_8);
        Path nullArtifact = outputDirectory.resolve("lppl-null-benchmarks.csv");
        Files.writeString(nullArtifact, "entity_type,group,key,path,raw_regime_score,raw_side\n",
                StandardCharsets.UTF_8);
        Path rollingArtifact = outputDirectory.resolve("lppl-rolling-snapshots.csv");
        Files.writeString(rollingArtifact, "entity_type,group,key,date,end_index,raw_regime_score,raw_side\n",
                StandardCharsets.UTF_8);

        Map<String, SectorLPPLExhaustionMapDemo.LoadedInstrument> loadedByTicker = indexLoaded(loaded);
        Map<String, SectorLPPLExhaustionMapDemo.InstrumentSnapshot> observedByTicker = indexObserved(observed);
        Map<String, Double> trendScores = calculateTrendScores(loaded, profile, progress);
        List<GroupNullResult> nullGroups = calculateNullScores(loadedByTicker, profile, config, progress, nullArtifact);
        List<RollingScore> rolling = calculateRollingScores(loaded, profile, config, progress, rollingArtifact);
        rolling = appendGroupRollingScores(rolling);

        Map<String, double[]> instrumentNullScores = new HashMap<>();
        Map<String, double[]> groupNullScores = new HashMap<>();
        List<NullScore> nullRows = new ArrayList<>();
        for (GroupNullResult group : nullGroups) {
            groupNullScores.put(group.group(), group.groupScores());
            for (Map.Entry<String, double[]> entry : group.instrumentScores().entrySet()) {
                instrumentNullScores.put(entry.getKey(), entry.getValue());
                for (int path = 0; path < entry.getValue().length; path++) {
                    nullRows.add(new NullScore("INSTRUMENT", group.group(), entry.getKey(), path,
                            entry.getValue()[path], SectorLPPLExhaustionMapDemo.rawSide(entry.getValue()[path])));
                }
            }
            for (int path = 0; path < group.groupScores().length; path++) {
                nullRows.add(new NullScore("GROUP", group.group(), group.group(), path, group.groupScores()[path],
                        SectorLPPLExhaustionMapDemo.rawSide(group.groupScores()[path])));
            }
        }
        nullRows.sort(Comparator.comparing(NullScore::entityType)
                .thenComparing(NullScore::group)
                .thenComparing(NullScore::key)
                .thenComparingInt(NullScore::path));

        Map<String, Metrics> instrumentMetrics = new LinkedHashMap<>();
        for (SectorLPPLExhaustionMapDemo.InstrumentDefinition definition : SectorLPPLExhaustionMapDemo.universe()) {
            String ticker = definition.ticker();
            double observedScore = observedByTicker.get(ticker).rawRegimeScore();
            double trendScore = trendScores.get(ticker);
            double[] nullScores = instrumentNullScores.get(ticker);
            List<RollingScore> entityRolling = rolling.stream()
                    .filter(snapshot -> snapshot.entityType().equals("INSTRUMENT") && snapshot.key().equals(ticker))
                    .toList();
            instrumentMetrics.put(ticker,
                    calculateMetrics(observedScore, trendScore, nullScores, entityRolling, config));
        }

        Map<String, Metrics> groupMetrics = new LinkedHashMap<>();
        for (SectorLPPLExhaustionMapDemo.CoverageGroup group : SectorLPPLExhaustionMapDemo.coverageGroups()) {
            List<SectorLPPLExhaustionMapDemo.InstrumentSnapshot> lenses = group.instruments()
                    .stream()
                    .map(definition -> observedByTicker.get(definition.ticker()))
                    .toList();
            double observedScore = SectorLPPLExhaustionMapDemo.median(lenses.stream()
                    .mapToDouble(SectorLPPLExhaustionMapDemo.InstrumentSnapshot::rawRegimeScore)
                    .toArray());
            double trendScore = SectorLPPLExhaustionMapDemo.median(group.instruments()
                    .stream()
                    .mapToDouble(definition -> trendScores.get(definition.ticker()))
                    .toArray());
            double[] groupNull = groupNullScores.get(group.name());
            List<RollingScore> entityRolling = rolling.stream()
                    .filter(snapshot -> snapshot.entityType().equals("GROUP") && snapshot.key().equals(group.name()))
                    .toList();
            Metrics base = calculateMetrics(observedScore, trendScore, groupNull, entityRolling, config);
            LPPLExhaustionSide observedSide = SectorLPPLExhaustionMapDemo.rawSide(observedScore);
            List<Metrics> lensMetrics = group.instruments()
                    .stream()
                    .map(definition -> instrumentMetrics.get(definition.ticker()))
                    .toList();
            double groupFalsePositiveRate = groupFalsePositiveRate(group, trendScores, instrumentNullScores, groupNull,
                    config);
            boolean qualified = groupQualified(base, observedSide, lensMetrics);
            groupMetrics.put(group.name(), base.withQualification(qualified, groupFalsePositiveRate));
        }

        Metadata metadata = new Metadata(SCHEMA_VERSION, latestDate(observed).toString(), dataDigest(loaded),
                profileFingerprint(profile), config.seed(), config.permutations(), config.rollingStep(),
                config.rollingWarmup(), config.rawSideThreshold(), config.alpha());
        Manifest manifest = Manifest.from(metadata, instrumentMetrics, groupMetrics);
        return new Result(true, metadata, Map.copyOf(instrumentMetrics), Map.copyOf(groupMetrics),
                List.copyOf(nullRows), List.copyOf(rolling), manifest);
    }

    static Optional<Result> loadCommitted(List<SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded,
            SectorLPPLExhaustionMapDemo.AnalysisProfile profile) throws IOException {
        String resource = SectorLPPLExhaustionMapDemo.RESOURCE_PREFIX + MANIFEST_NAME;
        try (InputStream input = SectorLPPLBenchmark.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                return Optional.empty();
            }
            Manifest manifest = GSON.fromJson(new String(input.readAllBytes(), StandardCharsets.UTF_8), Manifest.class);
            if (!matches(manifest, loaded, profile)) {
                return Optional.empty();
            }
            return Optional.of(manifest.toResult());
        }
    }

    static boolean matches(Manifest manifest, List<SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded,
            SectorLPPLExhaustionMapDemo.AnalysisProfile profile) {
        BenchmarkConfig production = BenchmarkConfig.production();
        return manifest != null && manifest.metadata() != null && manifest.metadata().schemaVersion() == SCHEMA_VERSION
                && manifest.metadata().dataDigest().equals(dataDigest(loaded))
                && manifest.metadata().profileFingerprint().equals(profileFingerprint(profile))
                && manifest.metadata().permutations() == production.permutations()
                && manifest.metadata().seed() == production.seed()
                && manifest.metadata().rollingStep() == production.rollingStep()
                && manifest.metadata().rollingWarmup() == production.rollingWarmup()
                && Double.compare(manifest.metadata().rawSideThreshold(), production.rawSideThreshold()) == 0
                && Double.compare(manifest.metadata().alpha(), production.alpha()) == 0
                && hasExpectedEntities(manifest.instruments(),
                        SectorLPPLExhaustionMapDemo.universe()
                                .stream()
                                .map(SectorLPPLExhaustionMapDemo.InstrumentDefinition::ticker)
                                .collect(java.util.stream.Collectors.toSet()))
                && hasExpectedEntities(manifest.groups(),
                        SectorLPPLExhaustionMapDemo.coverageGroups()
                                .stream()
                                .map(SectorLPPLExhaustionMapDemo.CoverageGroup::name)
                                .collect(java.util.stream.Collectors.toSet()));
    }

    private static boolean hasExpectedEntities(List<EntityMetrics> entities, Set<String> expectedKeys) {
        if (entities == null || entities.size() != expectedKeys.size() || entities.stream()
                .anyMatch(entity -> entity == null || entity.metrics() == null || !entity.metrics().available())) {
            return false;
        }
        Set<String> actualKeys = entities.stream().map(EntityMetrics::key).collect(java.util.stream.Collectors.toSet());
        return actualKeys.equals(expectedKeys);
    }

    private static Map<String, Double> calculateTrendScores(List<SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded,
            SectorLPPLExhaustionMapDemo.AnalysisProfile profile, Path progress) throws IOException {
        List<Callable<Map.Entry<String, Double>>> tasks = loaded
                .stream().<Callable<Map.Entry<String, Double>>>map(instrument -> () -> {
                    BarSeries trend = trendSeries(instrument.series());
                    double score = SectorLPPLExhaustionMapDemo
                            .analyzeInstrument(instrument.definition(), trend, profile)
                            .rawRegimeScore();
                    appendProgress(progress, "trend", instrument.definition().group(), instrument.definition().ticker(),
                            1, 1);
                    return Map.entry(instrument.definition().ticker(), score);
                })
                .toList();
        Map<String, Double> scores = new HashMap<>();
        for (Map.Entry<String, Double> result : runParallel(tasks)) {
            scores.put(result.getKey(), result.getValue());
        }
        return scores;
    }

    private static List<GroupNullResult> calculateNullScores(
            Map<String, SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded,
            SectorLPPLExhaustionMapDemo.AnalysisProfile profile, BenchmarkConfig config, Path progress,
            Path nullArtifact) throws IOException {
        List<Callable<GroupNullResult>> tasks = SectorLPPLExhaustionMapDemo.coverageGroups()
                .stream()
                .<Callable<GroupNullResult>>map(
                        group -> () -> calculateGroupNull(group, loaded, profile, config, progress, nullArtifact))
                .toList();
        List<GroupNullResult> results = runParallel(tasks);
        return results.stream().sorted(Comparator.comparing(GroupNullResult::group)).toList();
    }

    private static GroupNullResult calculateGroupNull(SectorLPPLExhaustionMapDemo.CoverageGroup group,
            Map<String, SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded,
            SectorLPPLExhaustionMapDemo.AnalysisProfile profile, BenchmarkConfig config, Path progress,
            Path nullArtifact) throws IOException {
        List<SectorLPPLExhaustionMapDemo.LoadedInstrument> instruments = group.instruments()
                .stream()
                .map(definition -> loaded.get(definition.ticker()))
                .toList();
        validateAligned(instruments);
        int returnCount = instruments.getFirst().series().getBarCount() - 1;
        Map<String, double[]> scores = new LinkedHashMap<>();
        for (SectorLPPLExhaustionMapDemo.LoadedInstrument instrument : instruments) {
            scores.put(instrument.definition().ticker(), new double[config.permutations()]);
        }
        double[] groupScores = new double[config.permutations()];
        for (int path = 0; path < config.permutations(); path++) {
            int[] permutation = permutation(returnCount, seedFor(config.seed(), group.name(), path));
            double[] lensScores = new double[instruments.size()];
            for (int lens = 0; lens < instruments.size(); lens++) {
                SectorLPPLExhaustionMapDemo.LoadedInstrument instrument = instruments.get(lens);
                BarSeries randomized = randomizedReturnSeries(instrument.series(), permutation);
                double score = SectorLPPLExhaustionMapDemo
                        .analyzeInstrument(instrument.definition(), randomized, profile)
                        .rawRegimeScore();
                scores.get(instrument.definition().ticker())[path] = score;
                lensScores[lens] = score;
            }
            groupScores[path] = SectorLPPLExhaustionMapDemo.median(lensScores);
            appendNullProgress(nullArtifact, group.name(), instruments, scores, groupScores, path);
            if ((path + 1) % 10 == 0 || path + 1 == config.permutations()) {
                appendProgress(progress, "randomized_returns", group.name(), "ALL", path + 1, config.permutations());
                LOG.info("Completed randomized LPPL controls for {} ({}/{})", group.name(), path + 1,
                        config.permutations());
            }
        }
        return new GroupNullResult(group.name(), Map.copyOf(scores), groupScores);
    }

    private static List<RollingScore> calculateRollingScores(List<SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded,
            SectorLPPLExhaustionMapDemo.AnalysisProfile profile, BenchmarkConfig config, Path progress,
            Path rollingArtifact) throws IOException {
        List<Callable<List<RollingScore>>> tasks = loaded.stream()
                .<Callable<List<RollingScore>>>map(instrument -> () -> {
                    List<Integer> endIndices = rollingEndIndices(instrument.series().getBarCount(),
                            config.rollingWarmup(), config.rollingStep());
                    List<RollingScore> snapshots = new ArrayList<>(endIndices.size());
                    for (int position = 0; position < endIndices.size(); position++) {
                        int endIndex = endIndices.get(position);
                        BarSeries slice = instrument.series().getSubSeries(0, endIndex + 1);
                        SectorLPPLExhaustionMapDemo.InstrumentSnapshot snapshot = SectorLPPLExhaustionMapDemo
                                .analyzeInstrument(instrument.definition(), slice, profile);
                        snapshots.add(new RollingScore("INSTRUMENT", instrument.definition().group(),
                                instrument.definition().ticker(), snapshot.latestDate(), endIndex,
                                snapshot.rawRegimeScore(), snapshot.rawSide()));
                        appendRollingProgress(rollingArtifact, snapshots.getLast());
                        if ((position + 1) % 10 == 0 || position + 1 == endIndices.size()) {
                            appendProgress(progress, "rolling_history", instrument.definition().group(),
                                    instrument.definition().ticker(), position + 1, endIndices.size());
                        }
                    }
                    return List.copyOf(snapshots);
                })
                .toList();
        List<RollingScore> scores = new ArrayList<>();
        for (List<RollingScore> result : runParallel(tasks)) {
            scores.addAll(result);
        }
        scores.sort(Comparator.comparing(RollingScore::entityType)
                .thenComparing(RollingScore::group)
                .thenComparing(RollingScore::key)
                .thenComparing(RollingScore::date));
        return List.copyOf(scores);
    }

    private static List<RollingScore> appendGroupRollingScores(List<RollingScore> instrumentScores) {
        List<RollingScore> all = new ArrayList<>(instrumentScores);
        for (SectorLPPLExhaustionMapDemo.CoverageGroup group : SectorLPPLExhaustionMapDemo.coverageGroups()) {
            Map<LocalDate, List<RollingScore>> byDate = new HashMap<>();
            for (RollingScore score : instrumentScores) {
                if (score.group().equals(group.name())) {
                    byDate.computeIfAbsent(score.date(), ignored -> new ArrayList<>()).add(score);
                }
            }
            byDate.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().size() == 3)
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        double groupScore = SectorLPPLExhaustionMapDemo
                                .median(entry.getValue().stream().mapToDouble(RollingScore::rawRegimeScore).toArray());
                        int endIndex = entry.getValue().stream().mapToInt(RollingScore::endIndex).min().orElseThrow();
                        all.add(new RollingScore("GROUP", group.name(), group.name(), entry.getKey(), endIndex,
                                groupScore, SectorLPPLExhaustionMapDemo.rawSide(groupScore)));
                    });
        }
        all.sort(Comparator.comparing(RollingScore::entityType)
                .thenComparing(RollingScore::group)
                .thenComparing(RollingScore::key)
                .thenComparing(RollingScore::date));
        return List.copyOf(all);
    }

    private static Metrics calculateMetrics(double observedScore, double trendScore, double[] nullScores,
            List<RollingScore> rolling, BenchmarkConfig config) {
        double pValue = empiricalPValue(observedScore, nullScores);
        double nullSignalRate = Arrays.stream(nullScores)
                .filter(score -> Math.abs(score) >= config.rawSideThreshold())
                .count() / (double) nullScores.length;
        double falsePositiveRate = instrumentFalsePositiveRate(trendScore, nullScores, config);
        boolean beatsTrend = Math.abs(observedScore) > Math.abs(trendScore);
        LPPLExhaustionSide side = SectorLPPLExhaustionMapDemo.rawSide(observedScore);
        boolean qualified = side != LPPLExhaustionSide.NONE && pValue <= config.alpha() && beatsTrend;
        HistoricalMetrics historical = historicalMetrics(observedScore, side, rolling, config.rawSideThreshold());
        return new Metrics(true, qualified, qualified ? side : LPPLExhaustionSide.NONE, pValue, nullSignalRate,
                falsePositiveRate, trendScore, beatsTrend, historical.snapshotCount(), historical.rawSignalRate(),
                historical.currentSideRate(), historical.currentAbsScorePercentile(), historical.sideChangeCount(),
                historical.currentSameSideRunSessions());
    }

    static double empiricalPValue(double observedScore, double[] nullScores) {
        long atLeastAsExtreme = Arrays.stream(nullScores)
                .filter(score -> Math.abs(score) >= Math.abs(observedScore))
                .count();
        return (1.0 + atLeastAsExtreme) / (nullScores.length + 1.0);
    }

    static boolean groupQualified(Metrics groupMetrics, LPPLExhaustionSide groupSide, List<Metrics> lenses) {
        int support = (int) lenses.stream()
                .filter(Metrics::qualified)
                .filter(metrics -> metrics.qualifiedSide() == groupSide)
                .count();
        boolean opposite = lenses.stream()
                .anyMatch(metrics -> metrics.qualified() && metrics.qualifiedSide() != groupSide);
        return groupMetrics.qualified() && groupSide != LPPLExhaustionSide.NONE && support >= 2 && !opposite;
    }

    private static double leaveOneOutPValue(double[] scores, int selected) {
        double selectedMagnitude = Math.abs(scores[selected]);
        int atLeastAsExtreme = 0;
        for (int index = 0; index < scores.length; index++) {
            if (index != selected && Math.abs(scores[index]) >= selectedMagnitude) {
                atLeastAsExtreme++;
            }
        }
        return (1.0 + atLeastAsExtreme) / scores.length;
    }

    private static double instrumentFalsePositiveRate(double trendScore, double[] nullScores, BenchmarkConfig config) {
        int qualified = 0;
        for (int path = 0; path < nullScores.length; path++) {
            double score = nullScores[path];
            if (Math.abs(score) >= config.rawSideThreshold() && leaveOneOutPValue(nullScores, path) <= config.alpha()
                    && Math.abs(score) > Math.abs(trendScore)) {
                qualified++;
            }
        }
        return qualified / (double) nullScores.length;
    }

    private static double groupFalsePositiveRate(SectorLPPLExhaustionMapDemo.CoverageGroup group,
            Map<String, Double> trendScores, Map<String, double[]> instrumentScores, double[] groupScores,
            BenchmarkConfig config) {
        double groupTrend = SectorLPPLExhaustionMapDemo.median(
                group.instruments().stream().mapToDouble(definition -> trendScores.get(definition.ticker())).toArray());
        int qualifiedPaths = 0;
        for (int path = 0; path < groupScores.length; path++) {
            double groupScore = groupScores[path];
            LPPLExhaustionSide groupSide = SectorLPPLExhaustionMapDemo.rawSide(groupScore);
            int support = 0;
            boolean opposite = false;
            for (SectorLPPLExhaustionMapDemo.InstrumentDefinition definition : group.instruments()) {
                double[] scores = instrumentScores.get(definition.ticker());
                double score = scores[path];
                LPPLExhaustionSide side = SectorLPPLExhaustionMapDemo.rawSide(score);
                boolean lensQualified = side != LPPLExhaustionSide.NONE
                        && leaveOneOutPValue(scores, path) <= config.alpha()
                        && Math.abs(score) > Math.abs(trendScores.get(definition.ticker()));
                if (lensQualified && side == groupSide) {
                    support++;
                } else if (lensQualified) {
                    opposite = true;
                }
            }
            boolean groupQualified = groupSide != LPPLExhaustionSide.NONE
                    && leaveOneOutPValue(groupScores, path) <= config.alpha()
                    && Math.abs(groupScore) > Math.abs(groupTrend) && support >= 2 && !opposite;
            if (groupQualified) {
                qualifiedPaths++;
            }
        }
        return qualifiedPaths / (double) groupScores.length;
    }

    private static HistoricalMetrics historicalMetrics(double observedScore, LPPLExhaustionSide observedSide,
            List<RollingScore> rolling, double rawThreshold) {
        if (rolling.isEmpty()) {
            return HistoricalMetrics.empty();
        }
        int rawSignals = (int) rolling.stream()
                .filter(snapshot -> Math.abs(snapshot.rawRegimeScore()) >= rawThreshold)
                .count();
        int sameSide = (int) rolling.stream().filter(snapshot -> snapshot.rawSide() == observedSide).count();
        int noMoreExtreme = (int) rolling.stream()
                .filter(snapshot -> Math.abs(snapshot.rawRegimeScore()) <= Math.abs(observedScore))
                .count();
        int changes = 0;
        for (int index = 1; index < rolling.size(); index++) {
            if (rolling.get(index - 1).rawSide() != rolling.get(index).rawSide()) {
                changes++;
            }
        }
        int firstCurrentRun = rolling.size() - 1;
        while (firstCurrentRun > 0 && rolling.get(firstCurrentRun - 1).rawSide() == observedSide) {
            firstCurrentRun--;
        }
        int runSessions = observedSide == LPPLExhaustionSide.NONE ? 0
                : rolling.getLast().endIndex() - rolling.get(firstCurrentRun).endIndex();
        return new HistoricalMetrics(rolling.size(), rawSignals / (double) rolling.size(),
                sameSide / (double) rolling.size(), noMoreExtreme / (double) rolling.size(), changes, runSessions);
    }

    static BarSeries trendSeries(BarSeries source) {
        int bars = source.getBarCount();
        double first = source.getFirstBar().getClosePrice().doubleValue();
        double last = source.getLastBar().getClosePrice().doubleValue();
        double logFirst = Math.log(first);
        double logChange = Math.log(last) - logFirst;
        double[] prices = new double[bars];
        for (int index = 0; index < bars; index++) {
            prices[index] = Math.exp(logFirst + logChange * index / (bars - 1.0));
        }
        return syntheticSeries(source, prices, "trend-control");
    }

    static BarSeries randomizedReturnSeries(BarSeries source, int[] permutation) {
        double[] returns = logReturns(source);
        if (permutation.length != returns.length) {
            throw new IllegalArgumentException("permutation length must match the return count");
        }
        double[] prices = new double[source.getBarCount()];
        prices[0] = source.getFirstBar().getClosePrice().doubleValue();
        for (int index = 0; index < permutation.length; index++) {
            prices[index + 1] = prices[index] * Math.exp(returns[permutation[index]]);
        }
        return syntheticSeries(source, prices, "randomized-return-control");
    }

    static double[] logReturns(BarSeries series) {
        double[] returns = new double[series.getBarCount() - 1];
        for (int index = 1; index < series.getBarCount(); index++) {
            double previous = series.getBar(index - 1).getClosePrice().doubleValue();
            double current = series.getBar(index).getClosePrice().doubleValue();
            returns[index - 1] = Math.log(current / previous);
        }
        return returns;
    }

    static int[] permutation(int length, long seed) {
        int[] values = new int[length];
        for (int index = 0; index < length; index++) {
            values[index] = index;
        }
        SplittableRandom random = new SplittableRandom(seed);
        for (int index = length - 1; index > 0; index--) {
            int replacement = random.nextInt(index + 1);
            int value = values[index];
            values[index] = values[replacement];
            values[replacement] = value;
        }
        return values;
    }

    private static BarSeries syntheticSeries(BarSeries source, double[] prices, String suffix) {
        BarSeries series = new BaseBarSeriesBuilder().withName(source.getName() + "-" + suffix)
                .withNumFactory(source.numFactory())
                .build();
        for (int index = 0; index < prices.length; index++) {
            Bar original = source.getBar(index);
            double price = prices[index];
            series.barBuilder()
                    .timePeriod(original.getTimePeriod())
                    .endTime(original.getEndTime())
                    .openPrice(price)
                    .highPrice(price)
                    .lowPrice(price)
                    .closePrice(price)
                    .volume(0)
                    .add();
        }
        return series;
    }

    static List<Integer> rollingEndIndices(int barCount, int warmup, int step) {
        if (barCount < warmup) {
            return List.of();
        }
        List<Integer> indices = new ArrayList<>();
        for (int endIndex = warmup - 1; endIndex < barCount; endIndex += step) {
            indices.add(endIndex);
        }
        int last = barCount - 1;
        if (indices.getLast() != last) {
            indices.add(last);
        }
        return List.copyOf(indices);
    }

    static String dataDigest(List<SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded) {
        MessageDigest digest = sha256();
        loaded.stream()
                .sorted(Comparator.comparing(instrument -> instrument.definition().ticker()))
                .forEach(instrument -> {
                    update(digest, instrument.definition().ticker());
                    for (int index = 0; index < instrument.series().getBarCount(); index++) {
                        Bar bar = instrument.series().getBar(index);
                        update(digest, bar.getEndTime().toString());
                        update(digest, Double.toHexString(bar.getClosePrice().doubleValue()));
                    }
                });
        return hex(digest.digest());
    }

    static String profileFingerprint(SectorLPPLExhaustionMapDemo.AnalysisProfile profile) {
        MessageDigest digest = sha256();
        update(digest, Arrays.toString(profile.windows()));
        update(digest, Integer.toString(profile.criticalOffsetStep()));
        update(digest, Integer.toString(profile.activeMinCriticalOffset()));
        update(digest, Integer.toString(profile.activeMaxCriticalOffset()));
        update(digest, Integer.toString(profile.maxEvaluations()));
        update(digest, Double.toHexString(profile.minRSquared()));
        return hex(digest.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String hex(byte[] bytes) {
        StringBuilder value = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            value.append(String.format("%02x", item & 0xff));
        }
        return value.toString();
    }

    private static long seedFor(long seed, String group, int path) {
        long value = seed;
        for (int index = 0; index < group.length(); index++) {
            value = 31L * value + group.charAt(index);
        }
        return value ^ (0x9E3779B97F4A7C15L * (path + 1L));
    }

    private static Map<String, SectorLPPLExhaustionMapDemo.LoadedInstrument> indexLoaded(
            List<SectorLPPLExhaustionMapDemo.LoadedInstrument> loaded) {
        Map<String, SectorLPPLExhaustionMapDemo.LoadedInstrument> indexed = new HashMap<>();
        for (SectorLPPLExhaustionMapDemo.LoadedInstrument instrument : loaded) {
            indexed.put(instrument.definition().ticker(), instrument);
        }
        return Map.copyOf(indexed);
    }

    private static Map<String, SectorLPPLExhaustionMapDemo.InstrumentSnapshot> indexObserved(
            List<SectorLPPLExhaustionMapDemo.InstrumentSnapshot> observed) {
        Map<String, SectorLPPLExhaustionMapDemo.InstrumentSnapshot> indexed = new HashMap<>();
        for (SectorLPPLExhaustionMapDemo.InstrumentSnapshot snapshot : observed) {
            indexed.put(snapshot.definition().ticker(), snapshot);
        }
        return Map.copyOf(indexed);
    }

    private static void validateAligned(List<SectorLPPLExhaustionMapDemo.LoadedInstrument> instruments) {
        BarSeries reference = instruments.getFirst().series();
        for (SectorLPPLExhaustionMapDemo.LoadedInstrument instrument : instruments.subList(1, instruments.size())) {
            if (instrument.series().getBarCount() != reference.getBarCount()) {
                throw new IllegalArgumentException(
                        instrument.definition().group() + " benchmark lenses are not aligned");
            }
            for (int index = 0; index < reference.getBarCount(); index++) {
                if (!instrument.series().getBar(index).getEndTime().equals(reference.getBar(index).getEndTime())) {
                    throw new IllegalArgumentException(
                            instrument.definition().group() + " benchmark lens timestamps are not aligned");
                }
            }
        }
    }

    private static LocalDate latestDate(List<SectorLPPLExhaustionMapDemo.InstrumentSnapshot> observed) {
        return observed.stream()
                .map(SectorLPPLExhaustionMapDemo.InstrumentSnapshot::latestDate)
                .max(LocalDate::compareTo)
                .orElseThrow();
    }

    private static void appendProgress(Path path, String phase, String group, String ticker, int completed, int total)
            throws IOException {
        synchronized (PROGRESS_LOCK) {
            Files.writeString(path, phase + ',' + group + ',' + ticker + ',' + completed + ',' + total + '\n',
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
    }

    private static void appendNullProgress(Path path, String group,
            List<SectorLPPLExhaustionMapDemo.LoadedInstrument> instruments, Map<String, double[]> scores,
            double[] groupScores, int selectedPath) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (SectorLPPLExhaustionMapDemo.LoadedInstrument instrument : instruments) {
            double score = scores.get(instrument.definition().ticker())[selectedPath];
            builder.append("INSTRUMENT,")
                    .append(group)
                    .append(',')
                    .append(instrument.definition().ticker())
                    .append(',')
                    .append(selectedPath)
                    .append(',')
                    .append(SectorLPPLExhaustionMapDemo.format(score))
                    .append(',')
                    .append(SectorLPPLExhaustionMapDemo.sideLabel(SectorLPPLExhaustionMapDemo.rawSide(score)))
                    .append('\n');
        }
        double groupScore = groupScores[selectedPath];
        builder.append("GROUP,")
                .append(group)
                .append(',')
                .append(group)
                .append(',')
                .append(selectedPath)
                .append(',')
                .append(SectorLPPLExhaustionMapDemo.format(groupScore))
                .append(',')
                .append(SectorLPPLExhaustionMapDemo.sideLabel(SectorLPPLExhaustionMapDemo.rawSide(groupScore)))
                .append('\n');
        synchronized (PROGRESS_LOCK) {
            Files.writeString(path, builder.toString(), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
    }

    private static void appendRollingProgress(Path path, RollingScore score) throws IOException {
        String row = score.entityType() + ',' + score.group() + ',' + score.key() + ',' + score.date() + ','
                + score.endIndex() + ',' + SectorLPPLExhaustionMapDemo.format(score.rawRegimeScore()) + ','
                + SectorLPPLExhaustionMapDemo.sideLabel(score.rawSide()) + '\n';
        synchronized (PROGRESS_LOCK) {
            Files.writeString(path, row, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
    }

    private static <T> List<T> runParallel(List<Callable<T>> tasks) throws IOException {
        int parallelism = Math.max(1, Math.min(12, Runtime.getRuntime().availableProcessors()));
        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<Future<T>> futures = executor.invokeAll(tasks);
            List<T> results = new ArrayList<>(futures.size());
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return List.copyOf(results);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("LPPL benchmark was interrupted", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            throw new IOException("LPPL benchmark failed", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    record BenchmarkConfig(long seed, int permutations, int rollingStep, int rollingWarmup, double rawSideThreshold,
            double alpha) {

        static BenchmarkConfig production() {
            return new BenchmarkConfig(20260710L, 199, 21, 750, SectorLPPLExhaustionMapDemo.RAW_SIDE_THRESHOLD, 0.05);
        }
    }

    record Metrics(boolean available, boolean qualified, LPPLExhaustionSide qualifiedSide, double empiricalNullPValue,
            double nullSignalRate, double estimatedGatedFalsePositiveRate, double trendControlScore,
            boolean beatsTrendControl, int rollingSnapshotCount, double historicalRawSignalRate,
            double historicalCurrentSideRate, double currentAbsScorePercentile, int sideChangeCount,
            int currentSameSideRunSessions) {

        static Metrics unavailable() {
            return new Metrics(false, false, LPPLExhaustionSide.NONE, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                    false, 0, Double.NaN, Double.NaN, Double.NaN, 0, 0);
        }

        Metrics withQualification(boolean replacement, double falsePositiveRate) {
            return new Metrics(available, replacement, replacement ? qualifiedSide : LPPLExhaustionSide.NONE,
                    empiricalNullPValue, nullSignalRate, falsePositiveRate, trendControlScore, beatsTrendControl,
                    rollingSnapshotCount, historicalRawSignalRate, historicalCurrentSideRate, currentAbsScorePercentile,
                    sideChangeCount, currentSameSideRunSessions);
        }
    }

    record Metadata(int schemaVersion, String snapshotDate, String dataDigest, String profileFingerprint, long seed,
            int permutations, int rollingStep, int rollingWarmup, double rawSideThreshold, double alpha) {
    }

    record NullScore(String entityType, String group, String key, int path, double rawRegimeScore,
            LPPLExhaustionSide rawSide) {
    }

    record RollingScore(String entityType, String group, String key, LocalDate date, int endIndex,
            double rawRegimeScore, LPPLExhaustionSide rawSide) {
    }

    private record GroupNullResult(String group, Map<String, double[]> instrumentScores, double[] groupScores) {
    }

    private record HistoricalMetrics(int snapshotCount, double rawSignalRate, double currentSideRate,
            double currentAbsScorePercentile, int sideChangeCount, int currentSameSideRunSessions) {

        static HistoricalMetrics empty() {
            return new HistoricalMetrics(0, Double.NaN, Double.NaN, Double.NaN, 0, 0);
        }
    }

    record EntityMetrics(String key, Metrics metrics) {
    }

    record Manifest(Metadata metadata, List<EntityMetrics> instruments, List<EntityMetrics> groups) {

        static Manifest from(Metadata metadata, Map<String, Metrics> instruments, Map<String, Metrics> groups) {
            List<EntityMetrics> instrumentValues = instruments.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> new EntityMetrics(entry.getKey(), entry.getValue()))
                    .toList();
            List<EntityMetrics> groupValues = groups.entrySet()
                    .stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> new EntityMetrics(entry.getKey(), entry.getValue()))
                    .toList();
            return new Manifest(metadata, instrumentValues, groupValues);
        }

        Result toResult() {
            Map<String, Metrics> instrumentValues = new LinkedHashMap<>();
            for (EntityMetrics value : instruments) {
                instrumentValues.put(value.key(), value.metrics());
            }
            Map<String, Metrics> groupValues = new LinkedHashMap<>();
            for (EntityMetrics value : groups) {
                groupValues.put(value.key(), value.metrics());
            }
            return new Result(true, metadata, Map.copyOf(instrumentValues), Map.copyOf(groupValues), List.of(),
                    List.of(), this);
        }
    }

    record Result(boolean available, Metadata metadata, Map<String, Metrics> instrumentMetrics,
            Map<String, Metrics> groupMetrics, List<NullScore> nullScores, List<RollingScore> rollingScores,
            Manifest manifest) {

        static Result unavailable() {
            return new Result(false, null, Map.of(), Map.of(), List.of(), List.of(), null);
        }

        void writeManifest(Path resourceDirectory) throws IOException {
            if (!available || manifest == null) {
                throw new IllegalStateException("A completed LPPL benchmark is required to update resources");
            }
            Files.createDirectories(resourceDirectory);
            Files.writeString(resourceDirectory.resolve(MANIFEST_NAME), GSON.toJson(manifest) + '\n',
                    StandardCharsets.UTF_8);
        }

        void writeArtifacts(Path outputDirectory) throws IOException {
            Files.createDirectories(outputDirectory);
            Files.writeString(outputDirectory.resolve("lppl-null-benchmarks.csv"), renderNullCsv(),
                    StandardCharsets.UTF_8);
            Files.writeString(outputDirectory.resolve("lppl-rolling-snapshots.csv"), renderRollingCsv(),
                    StandardCharsets.UTF_8);
            Files.writeString(outputDirectory.resolve("lppl-benchmark-summary.txt"), renderSummary(),
                    StandardCharsets.UTF_8);
            if (available && manifest != null) {
                Files.writeString(outputDirectory.resolve(MANIFEST_NAME), GSON.toJson(manifest) + '\n',
                        StandardCharsets.UTF_8);
            }
        }

        String renderSummary() {
            if (!available) {
                return "LPPL false-positive baseline\nbaseline_available=false\n"
                        + "benchmark_qualified_side=NONE (no calibrated conclusion)\n";
            }
            int instrumentTrials = metadata.permutations() * instrumentMetrics.size();
            int groupTrials = metadata.permutations() * groupMetrics.size();
            int instrumentFalsePositives = (int) Math.round(
                    instrumentMetrics.values().stream().mapToDouble(Metrics::estimatedGatedFalsePositiveRate).sum()
                            * metadata.permutations());
            int groupFalsePositives = (int) Math
                    .round(groupMetrics.values().stream().mapToDouble(Metrics::estimatedGatedFalsePositiveRate).sum()
                            * metadata.permutations());
            int instrumentRawSignals = (int) Math
                    .round(instrumentMetrics.values().stream().mapToDouble(Metrics::nullSignalRate).sum()
                            * metadata.permutations());
            int groupRawSignals = (int) Math
                    .round(groupMetrics.values().stream().mapToDouble(Metrics::nullSignalRate).sum()
                            * metadata.permutations());
            long instrumentTrendSignals = instrumentMetrics.values()
                    .stream()
                    .filter(metrics -> Math.abs(metrics.trendControlScore()) >= metadata.rawSideThreshold())
                    .count();
            long groupTrendSignals = groupMetrics.values()
                    .stream()
                    .filter(metrics -> Math.abs(metrics.trendControlScore()) >= metadata.rawSideThreshold())
                    .count();
            return "LPPL false-positive baseline\n" + "baseline_available=true\n" + "snapshot_date="
                    + metadata.snapshotDate() + "\nseed=" + metadata.seed() + "\nrandomized_paths="
                    + metadata.permutations() + "\nrolling_step_sessions=" + metadata.rollingStep()
                    + "\ndetailed_benchmark_artifacts=" + (!nullScores.isEmpty() && !rollingScores.isEmpty())
                    + "\nraw_side_threshold=" + SectorLPPLExhaustionMapDemo.format(metadata.rawSideThreshold())
                    + "\nalpha=" + SectorLPPLExhaustionMapDemo.format(metadata.alpha())
                    + "\ninstrument_raw_null_signal_rate="
                    + SectorLPPLExhaustionMapDemo.format(instrumentRawSignals / (double) instrumentTrials)
                    + "\ngroup_raw_null_signal_rate="
                    + SectorLPPLExhaustionMapDemo.format(groupRawSignals / (double) groupTrials)
                    + "\ninstrument_trend_control_signal_rate="
                    + SectorLPPLExhaustionMapDemo.format(instrumentTrendSignals / (double) instrumentMetrics.size())
                    + "\ngroup_trend_control_signal_rate="
                    + SectorLPPLExhaustionMapDemo.format(groupTrendSignals / (double) groupMetrics.size())
                    + "\ninstrument_gated_false_positive_rate="
                    + SectorLPPLExhaustionMapDemo.format(instrumentFalsePositives / (double) instrumentTrials)
                    + "\ngroup_gated_false_positive_rate="
                    + SectorLPPLExhaustionMapDemo.format(groupFalsePositives / (double) groupTrials) + "\n";
        }

        private String renderNullCsv() {
            StringBuilder builder = new StringBuilder("entity_type,group,key,path,raw_regime_score,raw_side\n");
            for (NullScore score : nullScores) {
                builder.append(score.entityType())
                        .append(',')
                        .append(score.group())
                        .append(',')
                        .append(score.key())
                        .append(',')
                        .append(score.path())
                        .append(',')
                        .append(SectorLPPLExhaustionMapDemo.format(score.rawRegimeScore()))
                        .append(',')
                        .append(SectorLPPLExhaustionMapDemo.sideLabel(score.rawSide()))
                        .append('\n');
            }
            return builder.toString();
        }

        private String renderRollingCsv() {
            StringBuilder builder = new StringBuilder(
                    "entity_type,group,key,date,end_index,raw_regime_score,raw_side\n");
            for (RollingScore score : rollingScores) {
                builder.append(score.entityType())
                        .append(',')
                        .append(score.group())
                        .append(',')
                        .append(score.key())
                        .append(',')
                        .append(score.date())
                        .append(',')
                        .append(score.endIndex())
                        .append(',')
                        .append(SectorLPPLExhaustionMapDemo.format(score.rawRegimeScore()))
                        .append(',')
                        .append(SectorLPPLExhaustionMapDemo.sideLabel(score.rawSide()))
                        .append('\n');
            }
            return builder.toString();
        }
    }
}
