/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.study;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.elliott.DetectorRobustnessMatrix;
import org.ta4j.core.analysis.elliott.StudyRunner;
import org.ta4j.core.analysis.elliott.StudyReport;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectors;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import ta4jexamples.analysis.elliottwave.support.OssifiedElliottWaveSeriesLoader;

/**
 * Executes a verified {@link ElliottStudyProtocol} end to end.
 *
 * <p>
 * This is the production path for frozen protocols: the classpath-verified JSON
 * is translated into a {@link StudyRunner} configuration, every protocol
 * dataset is evaluated, and each report is written under
 * {@code build/study-reports/}. No dataset, detector, grammar, rule, momentum
 * indicator, null parameter, or seed may be chosen at runtime; anything not
 * encoded by the protocol fails to load or resolve.
 * </p>
 */
public final class FrozenProtocolStudy {

    private static final Logger LOG = LogManager.getLogger(FrozenProtocolStudy.class);

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
    public static void run(ElliottStudyProtocol protocol, Path reportDir) throws IOException {
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
                    dataset.asset(), LOG);
            if (series == null) {
                throw new IllegalStateException("protocol dataset could not be loaded: " + dataset.id());
            }
            StudyRunner.Configuration configuration = new StudyRunner.Configuration(partitions(protocol),
                    protocol.fingerprintSha256(), protocol.nullEnsemble().seed(),
                    protocol.nullEnsemble().blockLengths(), protocol.nullEnsemble().ensembleSize(),
                    List.copyOf(robustness), protocol.primaryDetector());
            StudyRunner runner = StudyRunner.frozenPreregistered(primaryDetector, momentumFactory, configuration);
            StudyReport report = runner.evaluate(dataset.id(), series, series.getBeginIndex(), series.getEndIndex());
            Path target = reportDir.resolve(dataset.id() + ".json");
            Files.writeString(target, report.toJson());
            LOG.info("wrote study report {}", target);
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
            case "fractal" -> () -> SwingDetectors.fractal(params.get(0));
            case "slopeChange" -> () -> SwingDetectors.slopeChange(params.get(0));
            case "prominence" -> SwingDetectors::prominence;
            default ->
                throw new IllegalStateException("protocol detector factory is not executable: " + detector.factory());
            };
        }
        throw new IllegalArgumentException("unknown detector configuration: " + name);
    }

    private static Function<BarSeries, Indicator<Num>> momentumFactory(ElliottStudyProtocol.MomentumSpec momentum) {
        if (!"RSI".equals(momentum.type())) {
            throw new IllegalStateException("unsupported momentum type: " + momentum.type());
        }
        int barCount = momentum.barCount();
        return series -> new RSIIndicator(new ClosePriceIndicator(series), barCount);
    }
}
