/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

public class IndicatorFamilyAnalysisEngineTest {

    @Test
    public void clustersFamiliesAcrossAbsoluteAndSignedModes() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).build();
        Indicator<Num> close = new ClosePriceIndicator(series);
        Indicator<Num> smoothed = new SMAIndicator(close, 3);
        Indicator<Num> closeInverse = BinaryOperationIndicator.product(close, -1);
        Indicator<Num> smoothedInverse = new SMAIndicator(closeInverse, 3);

        IndicatorFamilyManifest manifest = new IndicatorFamilyManifest("indicators", "v1", "dataset", List.of(
                new IndicatorFamilyManifest.IndicatorManifestItem("close", close.toJson(), Map.of("role", "base")),
                new IndicatorFamilyManifest.IndicatorManifestItem("sma", smoothed.toJson(), Map.of("role", "trend")),
                new IndicatorFamilyManifest.IndicatorManifestItem("closeInverse", closeInverse.toJson(),
                        Map.of("role", "inverse")),
                new IndicatorFamilyManifest.IndicatorManifestItem("smaInverse", smoothedInverse.toJson(),
                        Map.of("role", "inverseTrend"))),
                Map.of("source", "test"));

        IndicatorFamilyAnalysisConfig absolute = new IndicatorFamilyAnalysisConfig("absolute", 5, 0.93,
                IndicatorFamilyAnalysisConfig.SimilarityMode.ABSOLUTE);
        IndicatorFamilyCatalog absoluteCatalog = IndicatorFamilyAnalysisEngine.runSingleConfig(series, manifest,
                absolute);

        IndicatorFamilyAnalysisConfig signed = new IndicatorFamilyAnalysisConfig("signed", 5, 0.93,
                IndicatorFamilyAnalysisConfig.SimilarityMode.SIGNED);
        IndicatorFamilyCatalog signedCatalog = IndicatorFamilyAnalysisEngine.runSingleConfig(series, manifest, signed);

        assertThat(absoluteCatalog.stableIndex()).isEqualTo(6);
        assertThat(absoluteCatalog.familyCount()).isEqualTo(1);
        assertThat(absoluteCatalog.familyByIndicator().values()).containsOnly("family-001");

        assertThat(signedCatalog.stableIndex()).isEqualTo(6);
        assertThat(signedCatalog.familyCount()).isEqualTo(2);
        assertThat(signedCatalog.familyByIndicator().get("close")).isEqualTo("family-001");
        assertThat(signedCatalog.familyByIndicator().get("sma")).isEqualTo("family-001");
        assertThat(signedCatalog.familyByIndicator().get("closeInverse")).isEqualTo("family-002");
        assertThat(signedCatalog.familyByIndicator().get("smaInverse")).isEqualTo("family-002");
        assertThat(signedCatalog.familyByIndicator().get("close"))
                .isNotEqualTo(signedCatalog.familyByIndicator().get("closeInverse"));
        assertThat(signedCatalog.pairSimilarity().keySet()).containsExactly("close/sma", "close/closeInverse",
                "close/smaInverse", "sma/closeInverse", "sma/smaInverse", "closeInverse/smaInverse");

        IndicatorFamilyAnalysisResult repeated = IndicatorFamilyAnalysisEngine.run(series, manifest,
                List.of(absolute, signed));
        assertThat(repeated.drifts()).hasSize(1);
        assertThat(repeated.drifts().get(0).changedCount()).isEqualTo(2);
        assertThat(repeated.catalogs().get(0).pairwiseFingerprint()).isEqualTo(absoluteCatalog.pairwiseFingerprint());
        assertThat(absoluteCatalog.pairwiseFingerprint()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(repeated.catalogs().get(0).pairwiseFingerprint()).isEqualTo(
                IndicatorFamilyAnalysisEngine.runSingleConfig(series, manifest, absolute).pairwiseFingerprint());
    }

    @Test
    public void runProducesDeterministicCatalogArtifacts() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 20, 30, 40, 50).build();
        Indicator<Num> close = new ClosePriceIndicator(series);
        IndicatorFamilyManifest manifest = new IndicatorFamilyManifest("det", "v1", "dataset",
                List.of(new IndicatorFamilyManifest.IndicatorManifestItem("close", close.toJson(), Map.of())),
                Map.of("dataset", "unit-test"));
        IndicatorFamilyAnalysisConfig config = IndicatorFamilyAnalysisConfig.defaultMode("default");

        IndicatorFamilyCatalog first = IndicatorFamilyAnalysisEngine.runSingleConfig(series, manifest, config);
        IndicatorFamilyCatalog second = IndicatorFamilyAnalysisEngine.runSingleConfig(series, manifest, config);

        assertThat(first).isEqualTo(second);
    }

    @Test
    public void runRejectsNullConfigEntries() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 20, 30, 40, 50).build();
        Indicator<Num> close = new ClosePriceIndicator(series);
        IndicatorFamilyManifest manifest = new IndicatorFamilyManifest("det", "v1", "dataset",
                List.of(new IndicatorFamilyManifest.IndicatorManifestItem("close", close.toJson(), Map.of())),
                Map.of("dataset", "unit-test"));

        List<IndicatorFamilyAnalysisConfig> configs = new ArrayList<>();
        configs.add(IndicatorFamilyAnalysisConfig.defaultMode("default"));
        configs.add(null);

        assertThatThrownBy(() -> IndicatorFamilyAnalysisEngine.run(series, manifest, configs))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("configs[1]");
    }

    @Test
    public void similarityModesClampRawCorrelation() {
        assertThat(IndicatorFamilyAnalysisConfig.SimilarityMode.ABSOLUTE.score(-1.2)).isEqualTo(1.0);
        assertThat(IndicatorFamilyAnalysisConfig.SimilarityMode.SIGNED.score(1.2)).isEqualTo(1.0);
        assertThat(IndicatorFamilyAnalysisConfig.SimilarityMode.SIGNED.score(-1.2)).isEqualTo(-1.0);
    }
}
