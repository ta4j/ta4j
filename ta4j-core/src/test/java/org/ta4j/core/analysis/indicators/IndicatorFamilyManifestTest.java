/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.indicators;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class IndicatorFamilyManifestTest {

    @Test
    public void resolvesIndicatorsIntoStableOrder() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7, 8, 9).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        SMAIndicator movingAverage = new SMAIndicator(closePrice, 3);

        IndicatorFamilyManifest manifest = new IndicatorFamilyManifest("manifest", "v1", "dataset",
                List.of(new IndicatorFamilyManifest.IndicatorManifestItem("close", closePrice.toJson(),
                        Map.of("role", "primary")),
                        new IndicatorFamilyManifest.IndicatorManifestItem("sma", movingAverage.toJson(),
                                Map.of("role", "smoothed"))),
                Map.of("source", "unit-test"));

        List<IndicatorFamilyManifest.ResolvedManifestItem> resolvedItems = manifest.resolveIndicators(series);
        assertThat(resolvedItems).hasSize(2);
        assertThat(resolvedItems.get(0).indicatorId()).isEqualTo("close");
        assertThat(resolvedItems.get(1).indicatorId()).isEqualTo("sma");
        assertThat(resolvedItems.get(0).indicator().getBarSeries()).isEqualTo(series);
        assertThat(resolvedItems.get(1).indicator().getBarSeries()).isEqualTo(series);
        assertThat(resolvedItems.get(0).indicator().toDescriptor()).isEqualTo(closePrice.toDescriptor());
        assertThat(resolvedItems.get(1).indicator().toDescriptor()).isEqualTo(movingAverage.toDescriptor());
    }

    @Test
    public void configHashIsIndependentOfMetadataOrder() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        IndicatorFamilyManifest manifestA = manifest(closePrice, metadata("a", "1", "b", "2"));
        IndicatorFamilyManifest manifestB = manifest(closePrice, metadata("b", "2", "a", "1"));

        assertThat(manifestA.configHash()).isEqualTo(manifestB.configHash());
        assertThat(manifestA.configHash()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    public void rejectsNullIndicatorItems() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        List<IndicatorFamilyManifest.IndicatorManifestItem> items = new ArrayList<>();
        items.add(new IndicatorFamilyManifest.IndicatorManifestItem("close", closePrice.toJson(), Map.of()));
        items.add(null);

        assertThatThrownBy(() -> new IndicatorFamilyManifest("manifest", "v1", "dataset", items, Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("indicators[1]");
    }

    private static IndicatorFamilyManifest manifest(ClosePriceIndicator closePrice, Map<String, String> metadata) {
        return new IndicatorFamilyManifest("manifest", "v1", "dataset",
                List.of(new IndicatorFamilyManifest.IndicatorManifestItem("close", closePrice.toJson(),
                        Map.of("role", "primary")),
                        new IndicatorFamilyManifest.IndicatorManifestItem("copy", closePrice.toJson(), metadata)),
                Map.of("dataset", "unit-test"));
    }

    private static Map<String, String> metadata(String firstKey, String firstValue, String secondKey,
            String secondValue) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(firstKey, firstValue);
        metadata.put(secondKey, secondValue);
        return metadata;
    }
}
