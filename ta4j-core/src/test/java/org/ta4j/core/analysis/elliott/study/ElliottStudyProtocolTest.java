/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.study;

import org.ta4j.core.analysis.elliott.rules.*;
import org.ta4j.core.analysis.elliott.topology.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.ta4j.core.analysis.elliott.swing.SwingDetector;
import org.ta4j.core.analysis.elliott.swing.SwingDetectors;

class ElliottStudyProtocolTest {
    private static final String PINNED_PROTOCOL_FINGERPRINT = "51698bdb1ac4a385024cd9909a2c5ced74af254a53eafda5ab5aa12010e3bdb8";

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
}
