/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectors;

class ElliottStudyProtocolTest {
    private static final String PINNED_PROTOCOL_FINGERPRINT = "320d6be02a7459e93ff91a5c3583f275472c3adbeb340a157754c717c7535deb";

    /**
     * The protocol is immutable and its dataset digests are verified on load; parse
     * and hash the pinned resources once for the whole class instead of repeating
     * the heaviest fixture I/O in every test.
     */
    private static final ElliottStudyProtocol PROTOCOL = ElliottStudyProtocol.load();
    private static final Logger LOG = LogManager.getLogger(ElliottStudyProtocolTest.class);

    @Test
    void loadsFrozenProtocolAndPinnedFingerprint() {
        ElliottStudyProtocol protocol = PROTOCOL;

        assertEquals(1, protocol.schemaVersion());
        assertEquals("cf525-elliott-hypothesis-study", protocol.protocolId());
        assertEquals("1.0.0", protocol.version());
        assertEquals(LocalDate.parse("2026-08-24"), protocol.frozenAt());
        assertEquals("retrospective-exploratory", protocol.studyDesign());
        assertEquals(PINNED_PROTOCOL_FINGERPRINT, protocol.fingerprintSha256());

        assertEquals("fractal-w5", protocol.primaryDetector());
        ElliottStudyProtocol.MomentumSpec momentum = protocol.momentumIndicator();
        assertEquals("RSI", momentum.type());
        assertEquals(14, momentum.barCount());

        assertThrows(IllegalArgumentException.class, () -> new ElliottStudyProtocol.MomentumSpec("MACD", 14));
        assertThrows(IllegalArgumentException.class, () -> new ElliottStudyProtocol.MomentumSpec("RSI", 1));
    }

    @Test
    void verifiesPinnedDatasetBytesBeforeParsing() {
        final ElliottStudyProtocol.DatasetSpec dataset = PROTOCOL.datasets().get(0);
        final BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottStudyProtocolTest.class,
                dataset.resource(), dataset.asset(), dataset.sha256(), LOG);

        assertNotNull(series);
        assertTrue(series.getBarCount() > 1_000);

        final String wrongSha256 = dataset.sha256().substring(0, 63) + (dataset.sha256().charAt(63) == '0' ? '1' : '0');
        assertNull(OssifiedElliottWaveSeriesLoader.loadSeries(ElliottStudyProtocolTest.class, dataset.resource(),
                dataset.asset(), wrongSha256, LOG));
    }

    @Test
    void loadsAllPinnedDatasetsInChronologicalOrder() {
        for (final ElliottStudyProtocol.DatasetSpec dataset : PROTOCOL.datasets()) {
            final BarSeries series = OssifiedElliottWaveSeriesLoader.loadSeries(ElliottStudyProtocolTest.class,
                    dataset.resource(), dataset.asset(), dataset.sha256(), LOG);
            assertNotNull(series, dataset.asset());
            assertTrue(series.getBar(series.getBeginIndex())
                    .getEndTime()
                    .isBefore(series.getBar(series.getEndIndex()).getEndTime()), dataset.asset());
        }
    }

    @Test
    void exposesDistinctHypotheses() {
        ElliottStudyProtocol protocol = PROTOCOL;

        assertEquals(2, protocol.hypotheses().size());
        assertNotSame(protocol.h1(), protocol.h2());
        assertEquals("H1", protocol.h1().id());
        assertEquals("MOTIVE_5", protocol.h1().grammar());
        assertEquals("H2", protocol.h2().id());
        assertEquals("CYCLE_5_3", protocol.h2().grammar());
    }

    @Test
    void enforcesCalibrationEmbargo() {
        ElliottStudyProtocol protocol = PROTOCOL;

        assertTrue(protocol.isCalibrationAllowed(LocalDate.parse("2023-12-31")));
        assertFalse(protocol.isCalibrationAllowed(LocalDate.parse("2024-01-01")));
        assertFalse(protocol.partitions().isCalibrationAllowed(LocalDate.parse("2024-01-01")));
        assertEquals(LocalDate.parse("2010-01-01"), protocol.partitions().calibrationStart());
        assertEquals(LocalDate.parse("2019-12-31"), protocol.partitions().calibrationEnd());
        assertEquals(LocalDate.parse("2020-01-01"), protocol.partitions().validationStart());
        assertEquals(LocalDate.parse("2023-06-15"), protocol.partitions().validationEnd());
        assertEquals(LocalDate.parse("2023-06-16"), protocol.partitions().holdoutStart());
        assertEquals(LocalDate.parse("2026-03-06"), protocol.partitions().holdoutEnd());
    }

    @Test
    void verifiesDatasetIntegrityDuringLoad() {
        ElliottStudyProtocol protocol = PROTOCOL;

        assertEquals(2, protocol.datasets().size());
        for (ElliottStudyProtocol.DatasetSpec dataset : protocol.datasets()) {
            assertTrue(dataset.resource().startsWith("/"));
            assertEquals(64, dataset.sha256().length());
        }
        assertEquals("primary", protocol.datasets().get(0).role());
        assertEquals("BTC-USD", protocol.datasets().get(0).asset());
        assertEquals("PT1D", protocol.datasets().get(0).barSize());
        assertEquals("transfer-portability", protocol.datasets().get(1).role());
        assertEquals("ETH-USD", protocol.datasets().get(1).asset());
        assertEquals("PT1D", protocol.datasets().get(1).barSize());
    }

    @Test
    void derivesDistinctDeterministicNullSeeds() {
        ElliottStudyProtocol.NullSpec nullSpec = PROTOCOL.nullEnsemble();

        long expectedSeed0 = 5_252_026L * 1_000_003L;
        assertEquals(expectedSeed0, nullSpec.seedFor(0));
        assertEquals(expectedSeed0, nullSpec.seedFor(0));
        assertNotEquals(nullSpec.seedFor(0), nullSpec.seedFor(1));
        assertEquals(expectedSeed0 + 1L, nullSpec.seedFor(1));
    }

    @Test
    void resolvesEveryConfiguredDetectorFactory() {
        ElliottStudyProtocol protocol = PROTOCOL;

        for (ElliottStudyProtocol.DetectorConfiguration configuration : protocol.detectorConfigurations()) {
            SwingDetector detector;
            switch (configuration.factory()) {
            case "fractal":
                detector = SwingDetectors.fractal(configuration.params().get(0));
                break;
            case "prominence":
                detector = SwingDetectors.prominence();
                break;
            case "slopeChange":
                detector = SwingDetectors.slopeChange(configuration.params().get(0));
                break;
            default:
                throw new AssertionError("Unknown detector factory: " + configuration.factory());
            }
            assertNotNull(detector, configuration.name());
        }
    }

    @Test
    public void defaultDatasetListenerRethrowsReportWriteFailureAsChecked(@TempDir final Path directory)
            throws IOException {
        final String datasetId = PROTOCOL.datasets().get(0).id();
        final Path target = Files.createDirectory(directory.resolve(datasetId + ".json"));
        final Path protectedFile = Files.writeString(target.resolve("keep"), "do not delete");
        final IOException[] writeFailure = new IOException[1];
        final List<Path> attempted = new ArrayList<>();
        final SingleBarExecution execution = new SingleBarExecution() {
            @Override
            public void write(final Path reportFile, final String contents) throws IOException {
                attempted.add(reportFile);
                try {
                    super.write(reportFile, contents);
                } catch (final IOException failure) {
                    writeFailure[0] = failure;
                    throw failure;
                }
            }
        };

        final IOException propagated = assertThrows(IOException.class, () -> FrozenProtocolStudy.run(PROTOCOL,
                directory, FrozenProtocolStudy.DatasetListener.NONE, execution));

        assertNotNull(writeFailure[0]);
        assertSame(writeFailure[0], propagated);
        assertEquals(List.of(target), attempted);
        assertTrue(Files.isDirectory(target));
        assertEquals("do not delete", Files.readString(protectedFile));
        assertFalse(Files.exists(directory.resolve(PROTOCOL.datasets().get(1).id() + ".json")));
    }

    @Test
    public void defaultDatasetListenerRethrowsObserverOpenFailureAsChecked(@TempDir final Path directory)
            throws IOException {
        final ElliottStudyProtocol.DatasetSpec dataset = PROTOCOL.datasets().get(0);
        final Path target = Files.createDirectory(directory.resolve(dataset.id() + "-real.jsonl"));
        final IOException[] openFailure = new IOException[1];
        final List<String> attempted = new ArrayList<>();
        final FrozenProtocolStudy.DatasetListener listener = new FrozenProtocolStudy.DatasetListener() {
            @Override
            public StudyObserver observer(final String datasetId) throws IOException {
                attempted.add(datasetId);
                try {
                    return ElliottResearchTrace.open(target, datasetId, "test-revision", PROTOCOL.fingerprintSha256(),
                            dataset.sha256(), ElliottResearchTrace.MODE_REAL, -1, -1);
                } catch (final IOException failure) {
                    openFailure[0] = failure;
                    throw failure;
                }
            }
        };

        final IOException propagated = assertThrows(IOException.class,
                () -> FrozenProtocolStudy.run(PROTOCOL, directory, listener));

        assertNotNull(openFailure[0]);
        assertSame(openFailure[0], propagated);
        assertEquals(List.of(dataset.id()), attempted);
        assertTrue(Files.isDirectory(target));
        assertFalse(Files.exists(directory.resolve(dataset.id() + ".json")));
        assertFalse(Files.exists(directory.resolve(PROTOCOL.datasets().get(1).id() + ".json")));
    }

    @Test
    public void defaultDatasetListenerRethrowsOtherRuntimeFailuresUnchanged(@TempDir final Path directory) {
        final IllegalStateException failure = new IllegalStateException("evaluation failed",
                new IOException("not an unchecked I/O wrapper"));
        final List<String> attempted = new ArrayList<>();
        final FrozenProtocolStudy.DatasetListener listener = new FrozenProtocolStudy.DatasetListener() {
            @Override
            public StudyObserver observer(final String datasetId) {
                attempted.add(datasetId);
                throw failure;
            }
        };

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> FrozenProtocolStudy.run(PROTOCOL, directory, listener)));
        assertEquals(List.of(PROTOCOL.datasets().get(0).id()), attempted);
    }

    @Test
    public void customDatasetListenerReceivesUncheckedIoFailuresAndContinuesCallbacks(@TempDir final Path directory)
            throws IOException {
        FrozenProtocolStudy.run(PROTOCOL, directory, FrozenProtocolStudy.DatasetListener.NONE,
                new SingleBarExecution());
        final Map<String, ElliottStudyProtocol.DatasetSpec> datasets = new HashMap<>();
        for (final ElliottStudyProtocol.DatasetSpec dataset : PROTOCOL.datasets()) {
            datasets.put(dataset.id(), dataset);
            Files.createDirectory(directory.resolve(dataset.id() + "-real.jsonl"));
            final JsonObject previous = JsonParser
                    .parseString(Files.readString(directory.resolve(dataset.id() + ".json")))
                    .getAsJsonObject();
            assertEquals(dataset.asset(), previous.get("assetId").getAsString());
            assertEquals(PINNED_PROTOCOL_FINGERPRINT, previous.get("protocolFingerprint").getAsString());
        }
        final List<String> datasetIds = new ArrayList<>();
        final List<RuntimeException> failures = new ArrayList<>();
        final FrozenProtocolStudy.DatasetListener listener = new FrozenProtocolStudy.DatasetListener() {
            @Override
            public StudyObserver observer(final String datasetId) throws IOException {
                final ElliottStudyProtocol.DatasetSpec dataset = datasets.get(datasetId);
                return ElliottResearchTrace.open(directory.resolve(datasetId + "-real.jsonl"), datasetId,
                        "test-revision", PROTOCOL.fingerprintSha256(), dataset.sha256(), ElliottResearchTrace.MODE_REAL,
                        -1, -1);
            }

            @Override
            public void failed(final String datasetId, final RuntimeException failure) {
                datasetIds.add(datasetId);
                failures.add(failure);
            }
        };
        FrozenProtocolStudy.run(PROTOCOL, directory, listener);

        assertEquals(List.of("primary", "transfer-portability"), datasetIds);
        assertEquals(datasetIds.size(), failures.size());
        for (int index = 0; index < datasetIds.size(); index++) {
            final String datasetId = datasetIds.get(index);
            final RuntimeException failure = failures.get(index);
            assertTrue(failure instanceof UncheckedIOException, failure.toString());
            assertTrue(failure.getCause() instanceof IOException, failure.toString());
            assertTrue(failure.getMessage().contains(datasetId), failure.getMessage());
            assertTrue(Files.isDirectory(directory.resolve(datasetId + "-real.jsonl")));
            assertFalse(Files.exists(directory.resolve(datasetId + ".json")));
        }
        try (Stream<Path> entries = Files.list(directory)) {
            assertEquals(2L, entries.count());
        }
    }

    @Test
    public void handledEvaluationFailureRemovesOnlyItsOldReportAndContinues(@TempDir final Path directory)
            throws IOException {
        FrozenProtocolStudy.run(PROTOCOL, directory, FrozenProtocolStudy.DatasetListener.NONE,
                new SingleBarExecution());
        final ElliottStudyProtocol.DatasetSpec first = PROTOCOL.datasets().get(0);
        final ElliottStudyProtocol.DatasetSpec second = PROTOCOL.datasets().get(1);
        final Path traceFile = directory.resolve(first.id() + "-real.jsonl");
        final ElliottResearchTrace closedTrace = ElliottResearchTrace.open(traceFile, first.id(), "test-revision",
                PROTOCOL.fingerprintSha256(), first.sha256(), ElliottResearchTrace.MODE_REAL, -1, -1);
        closedTrace.close();
        final Path protectedFile = Files.writeString(directory.resolve("keep.json"), "{\"keep\":true}");
        final List<String> failed = new ArrayList<>();
        final List<String> completed = new ArrayList<>();
        final FrozenProtocolStudy.DatasetListener listener = new FrozenProtocolStudy.DatasetListener() {
            @Override
            public StudyObserver observer(final String datasetId) {
                return first.id().equals(datasetId) ? closedTrace : null;
            }

            @Override
            public void failed(final String datasetId, final RuntimeException failure) {
                failed.add(datasetId);
                assertTrue(failure instanceof IllegalStateException, failure.toString());
                assertTrue(failure.getMessage().contains("trace already closed"), failure.getMessage());
            }

            @Override
            public void completed(final String datasetId, final StudyRunner runner, final BarSeries series,
                    final StudyReport report, final Path reportFile) throws IOException {
                completed.add(datasetId);
                assertWrittenReport(reportFile, report);
                assertEquals(second.asset(), report.assetId());
            }
        };

        FrozenProtocolStudy.run(PROTOCOL, directory, listener, new SingleBarExecution());

        assertEquals(List.of(first.id()), failed);
        assertEquals(List.of(second.id()), completed);
        assertFalse(Files.exists(directory.resolve(first.id() + ".json")));
        assertTrue(Files.isRegularFile(directory.resolve(second.id() + ".json")));
        assertTrue(Files.isRegularFile(traceFile));
        assertEquals("{\"keep\":true}", Files.readString(protectedFile));
    }

    @Test
    public void handledReportWriteFailurePreservesTargetDirectoryAndContinues(@TempDir final Path directory)
            throws IOException {
        final ElliottStudyProtocol.DatasetSpec first = PROTOCOL.datasets().get(0);
        final ElliottStudyProtocol.DatasetSpec second = PROTOCOL.datasets().get(1);
        final Path target = Files.createDirectory(directory.resolve(first.id() + ".json"));
        final List<String> failed = new ArrayList<>();
        final List<String> completed = new ArrayList<>();
        final FrozenProtocolStudy.DatasetListener listener = new FrozenProtocolStudy.DatasetListener() {
            @Override
            public void failed(final String datasetId, final RuntimeException failure) {
                failed.add(datasetId);
                assertTrue(failure instanceof UncheckedIOException, failure.toString());
                assertTrue(failure.getCause() instanceof IOException, failure.toString());
                assertTrue(failure.getMessage().contains(target.toString()), failure.getMessage());
            }

            @Override
            public void completed(final String datasetId, final StudyRunner runner, final BarSeries series,
                    final StudyReport report, final Path reportFile) throws IOException {
                completed.add(datasetId);
                assertWrittenReport(reportFile, report);
                assertEquals(second.asset(), report.assetId());
            }
        };

        FrozenProtocolStudy.run(PROTOCOL, directory, listener, new SingleBarExecution());

        assertEquals(List.of(first.id()), failed);
        assertEquals(List.of(second.id()), completed);
        assertTrue(Files.isDirectory(target));
        try (Stream<Path> entries = Files.list(target)) {
            assertEquals(0L, entries.count());
        }
        assertTrue(Files.isRegularFile(directory.resolve(second.id() + ".json")));
    }

    @Test
    public void handledPartialReportWriteFailureRemovesTruncatedReportWithoutDeletingCompletedReport(
            @TempDir final Path directory) throws IOException {
        FrozenProtocolStudy.run(PROTOCOL, directory, FrozenProtocolStudy.DatasetListener.NONE,
                new SingleBarExecution());
        final ElliottStudyProtocol.DatasetSpec first = PROTOCOL.datasets().get(0);
        final ElliottStudyProtocol.DatasetSpec second = PROTOCOL.datasets().get(1);
        final Path target = directory.resolve(second.id() + ".json");
        final JsonObject previous = JsonParser.parseString(Files.readString(target)).getAsJsonObject();
        assertEquals(second.asset(), previous.get("assetId").getAsString());
        final IOException[] writeFailure = new IOException[1];
        final SingleBarExecution execution = new SingleBarExecution() {
            @Override
            public void write(final Path reportFile, final String contents) throws IOException {
                if (!target.equals(reportFile)) {
                    super.write(reportFile, contents);
                    return;
                }
                // A real truncated file and closed writer make mid-write I/O failure
                // deterministic without filesystem-permission or disk-space assumptions.
                final BufferedWriter partialWriter = Files.newBufferedWriter(reportFile);
                try (partialWriter) {
                    partialWriter.write('{');
                }
                try {
                    partialWriter.write(contents);
                } catch (final IOException failure) {
                    writeFailure[0] = failure;
                    throw failure;
                }
            }
        };
        final List<String> failed = new ArrayList<>();
        final List<String> completed = new ArrayList<>();
        final List<StudyReport> completedReports = new ArrayList<>();
        final FrozenProtocolStudy.DatasetListener listener = new FrozenProtocolStudy.DatasetListener() {
            @Override
            public void failed(final String datasetId, final RuntimeException failure) throws IOException {
                failed.add(datasetId);
                assertTrue(failure instanceof UncheckedIOException, failure.toString());
                assertSame(writeFailure[0], failure.getCause());
                assertEquals("{", Files.readString(target));
            }

            @Override
            public void completed(final String datasetId, final StudyRunner runner, final BarSeries series,
                    final StudyReport report, final Path reportFile) throws IOException {
                completed.add(datasetId);
                completedReports.add(report);
                assertWrittenReport(reportFile, report);
                assertEquals(first.asset(), report.assetId());
            }
        };

        FrozenProtocolStudy.run(PROTOCOL, directory, listener, execution);

        assertNotNull(writeFailure[0]);
        assertEquals(List.of(second.id()), failed);
        assertEquals(List.of(first.id()), completed);
        assertFalse(Files.exists(target));
        assertEquals(1, completedReports.size());
        assertWrittenReport(directory.resolve(first.id() + ".json"), completedReports.get(0));
    }

    @Test
    public void successfulRerunsOverwriteValidReportsAndCompleteWithExactEvaluationIdentity(
            @TempDir final Path directory) throws IOException {
        final Map<String, StudyRunner> runners = new HashMap<>();
        final Map<String, BarSeries> sources = new HashMap<>();
        final Map<String, StudyReport> reports = new HashMap<>();
        final SingleBarExecution execution = new SingleBarExecution() {
            @Override
            public StudyReport evaluate(final StudyRunner runner, final String assetId, final BarSeries series,
                    final StudyObserver observer) {
                final StudyReport report = super.evaluate(runner, assetId, series, observer);
                runners.put(assetId, runner);
                sources.put(assetId, series);
                reports.put(assetId, report);
                return report;
            }
        };
        final List<String> completed = new ArrayList<>();
        final FrozenProtocolStudy.DatasetListener listener = new FrozenProtocolStudy.DatasetListener() {
            @Override
            public void completed(final String datasetId, final StudyRunner runner, final BarSeries series,
                    final StudyReport report, final Path reportFile) throws IOException {
                completed.add(datasetId);
                assertSame(runners.get(report.assetId()), runner);
                assertSame(sources.get(report.assetId()), series);
                assertSame(reports.get(report.assetId()), report);
                assertEquals(directory.resolve(datasetId + ".json"), reportFile);
                assertWrittenReport(reportFile, report);
            }
        };

        for (int pass = 0; pass < 2; pass++) {
            completed.clear();
            FrozenProtocolStudy.run(PROTOCOL, directory, listener, execution);
            assertEquals(List.of("primary", "transfer-portability"), completed);
            if (pass == 0) {
                for (final ElliottStudyProtocol.DatasetSpec dataset : PROTOCOL.datasets()) {
                    final Path target = directory.resolve(dataset.id() + ".json");
                    final String previous = Files.readString(target) + " ".repeat(128);
                    Files.writeString(target, previous);
                    assertEquals(dataset.asset(),
                            JsonParser.parseString(previous).getAsJsonObject().get("assetId").getAsString());
                }
            }
        }
        try (Stream<Path> entries = Files.list(directory)) {
            assertEquals(2L, entries.count());
        }
    }

    @Test
    public void completionFailurePreservesWrittenReportAndDoesNotBecomeDatasetFailure(@TempDir final Path directory)
            throws IOException {
        final ElliottStudyProtocol.DatasetSpec first = PROTOCOL.datasets().get(0);
        final IOException completionFailure = new IOException("follow-up artifact failed");
        final List<String> failed = new ArrayList<>();
        final List<StudyReport> completed = new ArrayList<>();
        final FrozenProtocolStudy.DatasetListener listener = new FrozenProtocolStudy.DatasetListener() {
            @Override
            public void failed(final String datasetId, final RuntimeException failure) {
                failed.add(datasetId);
            }

            @Override
            public void completed(final String datasetId, final StudyRunner runner, final BarSeries series,
                    final StudyReport report, final Path reportFile) throws IOException {
                completed.add(report);
                assertEquals(first.id(), datasetId);
                assertWrittenReport(reportFile, report);
                throw completionFailure;
            }
        };

        assertSame(completionFailure, assertThrows(IOException.class,
                () -> FrozenProtocolStudy.run(PROTOCOL, directory, listener, new SingleBarExecution())));

        assertTrue(failed.isEmpty());
        assertEquals(1, completed.size());
        assertWrittenReport(directory.resolve(first.id() + ".json"), completed.get(0));
        assertFalse(Files.exists(directory.resolve(PROTOCOL.datasets().get(1).id() + ".json")));
    }

    private static void assertWrittenReport(final Path target, final StudyReport report) throws IOException {
        final String written = Files.readString(target);
        assertEquals(report.toJson(), written);
        final JsonObject json = JsonParser.parseString(written).getAsJsonObject();
        assertEquals(report.assetId(), json.get("assetId").getAsString());
        assertEquals(PINNED_PROTOCOL_FINGERPRINT, json.get("protocolFingerprint").getAsString());
    }

    /**
     * Keeps lifecycle tests on a real in-partition bar, not the exhaustive study.
     */
    private static class SingleBarExecution implements FrozenProtocolStudy.DatasetExecution {
        @Override
        public StudyReport evaluate(final StudyRunner runner, final String assetId, final BarSeries series,
                final StudyObserver observer) {
            final BarSeries singleBar = series.getSubSeries(series.getEndIndex(), series.getEndIndex() + 1);
            return runner.evaluate(assetId, singleBar, singleBar.getBeginIndex(), singleBar.getEndIndex(), observer);
        }
    }
}
