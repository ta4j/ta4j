/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectors;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Executes a verified {@link ElliottStudyProtocol} end to end.
 *
 * <p>
 * This is the package-private test-plane path for frozen protocols: the
 * classpath-verified JSON is translated into a {@link StudyRunner}
 * configuration, every protocol dataset is evaluated, and each report is
 * written under {@code build/study-reports/}. No dataset, detector, grammar,
 * rule, momentum indicator, null parameter, or seed may be chosen at runtime;
 * anything not encoded by the protocol fails to load or resolve.
 * </p>
 *
 * @since 0.24.2
 */
final class FrozenProtocolStudy {

    private static final Logger LOG = LogManager.getLogger(FrozenProtocolStudy.class);
    private static final List<String> FROZEN_COMPETING_MODES = List.of("3+3", "5+5", "7+3", "change-point-baseline");
    private static final List<String> FROZEN_METRICS = List.of("matchRate", "ambiguousRate", "noMatchRate",
            "confirmationLagBars", "labelStabilityJaccard", "evidencePassRate", "jointPassRate");

    private FrozenProtocolStudy() {
    }

    /**
     * Runs the bundled CF-525 protocol over all of its datasets.
     *
     * @param args ignored
     * @throws IOException when reports cannot be written
     */
    public static void main(String[] args) throws IOException {
        ElliottStudyProtocol protocol = ElliottStudyProtocol.load();
        run(protocol, Path.of("build", "study-reports"));
    }

    /**
     * Evaluates every dataset of the supplied verified protocol and writes one JSON
     * report per dataset.
     *
     * @param protocol  verified study protocol
     * @param reportDir directory receiving {@code <dataset>.json} reports
     * @throws IOException when reports cannot be written
     */
    static void run(final ElliottStudyProtocol protocol, final Path reportDir) throws IOException {
        validateExecutableProtocol(protocol);
        Supplier<SwingDetector> primaryDetector = resolveDetector(protocol, protocol.primaryDetector());
        List<DetectorRobustnessMatrix.DetectorSpec> robustness = new ArrayList<>();
        for (ElliottStudyProtocol.DetectorConfiguration detector : protocol.detectorConfigurations()) {
            String name = detector.name();
            Supplier<SwingDetector> resolved = resolveDetector(protocol, name);
            robustness.add(new DetectorRobustnessMatrix.DetectorSpec(name, resolved));
        }
        Function<BarSeries, Indicator<Num>> momentumFactory = momentumFactory(protocol.momentumIndicator());

        Files.createDirectories(reportDir);
        for (ElliottStudyProtocol.DatasetSpec dataset : protocol.datasets()) {
            BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(FrozenProtocolStudy.class, dataset.resource(),
                    dataset.asset(), dataset.sha256(), LOG);
            if (series == null) {
                throw new IllegalStateException("protocol dataset could not be loaded: " + dataset.id());
            }
            // The frozen protocol executes exactly its declared competing
            // set; undeclared kernel experiments stay out of the report.
            List<String> competingModes = protocol.competingGrammars();
            StudyRunner.Configuration configuration = new StudyRunner.Configuration(partitions(protocol),
                    protocol.fingerprintSha256(), protocol.nullEnsemble().seed(),
                    protocol.nullEnsemble().blockLengths(), protocol.nullEnsemble().ensembleSize(),
                    List.copyOf(robustness), protocol.primaryDetector(), competingModes);
            StudyRunner runner = StudyRunner.frozenPreregistered(primaryDetector, momentumFactory, configuration,
                    protocol.ablationSet());
            LOG.info("evaluating dataset {} ({}): ensemble {} members per block length {}, competing modes {}",
                    dataset.id(), dataset.asset(), protocol.nullEnsemble().ensembleSize(),
                    protocol.nullEnsemble().blockLengths(), competingModes);
            StudyReport report = runner.evaluate(dataset.asset(), series, series.getBeginIndex(), series.getEndIndex());
            Path target = reportDir.resolve(dataset.id() + ".json");
            Files.writeString(target, report.toJson());
            LOG.info("wrote study report {} for {}", target, dataset.asset());
        }
    }

    private static StudyRunner.Partitions partitions(ElliottStudyProtocol protocol) {
        List<StudyRunner.Partition> entries = new ArrayList<>();
        entries.add(new StudyRunner.Partition("calibration", protocol.partitions().calibrationStart(),
                protocol.partitions().calibrationEnd()));
        entries.add(new StudyRunner.Partition("validation", protocol.partitions().validationStart(),
                protocol.partitions().validationEnd()));
        entries.add(new StudyRunner.Partition("holdout", protocol.partitions().holdoutStart(),
                protocol.partitions().holdoutEnd()));
        return new StudyRunner.Partitions(entries, protocol.partitions().forbiddenCalibrationStart());
    }

    private static Supplier<SwingDetector> resolveDetector(ElliottStudyProtocol protocol, String name) {
        for (ElliottStudyProtocol.DetectorConfiguration detector : protocol.detectorConfigurations()) {
            if (!detector.name().equals(name)) {
                continue;
            }
            List<Integer> params = detector.params();
            return switch (detector.factory()) {
            case "fractal" -> {
                yield () -> SwingDetectors.fractal(requiredParam(detector, params));
            }
            case "slopeChange" -> {
                yield () -> SwingDetectors.slopeChange(requiredParam(detector, params));
            }
            case "prominence" -> SwingDetectors::prominence;
            default ->
                throw new IllegalStateException("protocol detector factory is not executable: " + detector.factory());
            };
        }
        throw new IllegalArgumentException("unknown detector configuration: " + name);
    }

    private static void validateExecutableProtocol(final ElliottStudyProtocol protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol must not be null");
        }
        if (!"cf525-elliott-hypothesis-study".equals(protocol.protocolId()) || !"1.0.0".equals(protocol.version())
                || !"H1".equals(protocol.h1().id()) || !"MOTIVE_5".equals(protocol.h1().grammar())
                || !"H2".equals(protocol.h2().id()) || !"CYCLE_5_3".equals(protocol.h2().grammar())) {
            throw new IllegalArgumentException("protocol hypotheses are not the frozen CF-525 path");
        }
        if (!FROZEN_COMPETING_MODES.equals(protocol.competingGrammars()) || !FROZEN_METRICS.equals(protocol.metrics())
                || !RuleAblation.frozenModeNames().equals(protocol.ablationSet())) {
            throw new IllegalArgumentException("protocol grammar, metric, or ablation set is not frozen");
        }
        if (!"fractal-w5".equals(protocol.primaryDetector()) || !"RSI".equals(protocol.momentumIndicator().type())
                || protocol.momentumIndicator().barCount() != 14
                || !"stationary-block-bootstrap".equals(protocol.nullEnsemble().type())
                || !List.of(20, 60).equals(protocol.nullEnsemble().blockLengths())
                || protocol.nullEnsemble().ensembleSize() != 200 || protocol.nullEnsemble().seed() != 5_252_026L) {
            throw new IllegalArgumentException("protocol executable specifications are not the frozen CF-525 path");
        }
        final ElliottStudyProtocol.Partitions partitions = protocol.partitions();
        if (!LocalDate.of(2010, 1, 1).equals(partitions.calibrationStart())
                || !LocalDate.of(2019, 12, 31).equals(partitions.calibrationEnd())
                || !LocalDate.of(2020, 1, 1).equals(partitions.validationStart())
                || !LocalDate.of(2023, 6, 15).equals(partitions.validationEnd())
                || !LocalDate.of(2023, 6, 16).equals(partitions.holdoutStart())
                || !LocalDate.of(2026, 3, 6).equals(partitions.holdoutEnd())
                || !LocalDate.of(2024, 1, 1).equals(partitions.forbiddenCalibrationStart())) {
            throw new IllegalArgumentException("protocol partitions are not the frozen CF-525 windows");
        }
        final List<ElliottStudyProtocol.DetectorConfiguration> detectors = protocol.detectorConfigurations();
        if (detectors.size() != 4 || !detectorMatches(detectors.get(0), "fractal-w3", "fractal", List.of(3))
                || !detectorMatches(detectors.get(1), "fractal-w5", "fractal", List.of(5))
                || !detectorMatches(detectors.get(2), "prominence-default", "prominence", List.of())
                || !detectorMatches(detectors.get(3), "slope-change-w5", "slopeChange", List.of(5))) {
            throw new IllegalArgumentException("protocol detector matrix is not the frozen CF-525 matrix");
        }
        if (protocol.datasets().size() != 2
                || !datasetMatches(protocol.datasets().get(0), "primary",
                        "/TradingView-INDEX_BTCUSD-PT1D-20091005_20260306.json", "primary", "BTC-USD", "PT1D",
                        "19621e98cb310fce9bf013d4963da0f6c7ab7f26f89a42af16a1a2408254e2fe")
                || !datasetMatches(protocol.datasets().get(1), "transfer-portability",
                        "/Coinbase-ETH-USD-PT1D-20160517_20251028.json", "transfer-portability", "ETH-USD", "PT1D",
                        "11f81500ef33b08fb6fe8d03c75b4afa343668521448afe345611ee83171a384")) {
            throw new IllegalArgumentException("protocol datasets are not the frozen CF-525 assets");
        }
    }

    private static boolean detectorMatches(final ElliottStudyProtocol.DetectorConfiguration configuration,
            final String name, final String factory, final List<Integer> params) {
        return name.equals(configuration.name()) && factory.equals(configuration.factory())
                && params.equals(configuration.params());
    }

    private static boolean datasetMatches(final ElliottStudyProtocol.DatasetSpec dataset, final String id,
            final String resource, final String role, final String asset, final String barSize, final String sha256) {
        return id.equals(dataset.id()) && resource.equals(dataset.resource()) && role.equals(dataset.role())
                && asset.equals(dataset.asset()) && barSize.equals(dataset.barSize())
                && sha256.equals(dataset.sha256());
    }

    private static int requiredParam(ElliottStudyProtocol.DetectorConfiguration detector, List<Integer> params) {
        if (params.isEmpty()) {
            throw new IllegalStateException("protocol detector requires one parameter: " + detector.name());
        }
        return params.get(0);
    }

    private static Function<BarSeries, Indicator<Num>> momentumFactory(ElliottStudyProtocol.MomentumSpec momentum) {
        if (!"RSI".equals(momentum.type())) {
            throw new IllegalStateException("unsupported momentum type: " + momentum.type());
        }
        int barCount = momentum.barCount();
        return series -> new RSIIndicator(new ClosePriceIndicator(series), barCount);
    }
}
