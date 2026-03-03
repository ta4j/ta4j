/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class WalkForwardConfigAndSplitterTest {

    @Test
    void defaultConfigCarriesApprovedHorizonAndTopKPolicy() {
        WalkForwardConfig config = WalkForwardConfig.defaultConfig();

        assertThat(config.primaryHorizonBars()).isEqualTo(60);
        assertThat(config.reportingHorizons()).containsExactly(30, 150);
        assertThat(config.optimizationTopK()).isEqualTo(3);
        assertThat(config.reportingTopKs()).containsExactly(1, 5);
        assertThat(config.allHorizons()).containsExactly(60, 30, 150);
        assertThat(config.allTopKs()).containsExactly(3, 1, 5);
    }

    @Test
    void configRejectsInvalidGeometry() {
        assertThatThrownBy(() -> new WalkForwardConfig(0, 10, 10, 0, 0, 0, 60, List.of(), 3, List.of(), 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("minTrainBars");
    }

    @Test
    void anchoredSplitterCreatesChronologicalFoldsAndHoldout() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(400)).build();
        WalkForwardConfig config = new WalkForwardConfig(120, 50, 50, 5, 5, 40, 60, List.of(30, 150), 3, List.of(1, 5),
                42L);

        WalkForwardSplitter splitter = new AnchoredExpandingWalkForwardSplitter();
        List<WalkForwardSplit> splits = splitter.split(series, config);

        assertThat(splits).isNotEmpty();
        assertThat(splits.get(0).foldId()).isEqualTo("fold-1");
        for (WalkForwardSplit split : splits) {
            assertThat(split.trainStart()).isEqualTo(series.getBeginIndex());
            assertThat(split.trainEnd()).isLessThan(split.testStart());
            assertThat(split.testStart()).isLessThanOrEqualTo(split.testEnd());
        }
        assertThat(splits.get(splits.size() - 1).holdout()).isTrue();
        assertThat(splits.get(splits.size() - 1).testBarCount()).isEqualTo(config.holdoutBars());
    }

    private static double[] prices(int size) {
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            prices[i] = 100 + i;
        }
        return prices;
    }
}
