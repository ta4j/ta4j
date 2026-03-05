/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.walkforward;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

class WalkForwardSplitterEdgeCaseTest {

    @Test
    void splitReturnsEmptyWhenSeriesCannotSupportEvaluationWindow() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(30)).build();
        WalkForwardConfig config = new WalkForwardConfig(20, 10, 10, 0, 0, 20, 5, List.of(), 1, List.of(), 1L);

        WalkForwardSplitter splitter = new AnchoredExpandingWalkForwardSplitter();
        List<WalkForwardSplit> splits = splitter.split(series, config);

        assertThat(splits).isEmpty();
    }

    @Test
    void splitOmitsHoldoutWhenHoldoutBarsAreDisabled() {
        BarSeries series = new MockBarSeriesBuilder().withData(prices(120)).build();
        WalkForwardConfig config = new WalkForwardConfig(60, 10, 10, 1, 1, 0, 5, List.of(), 1, List.of(), 1L);

        WalkForwardSplitter splitter = new AnchoredExpandingWalkForwardSplitter();
        List<WalkForwardSplit> splits = splitter.split(series, config);

        assertThat(splits).isNotEmpty();
        assertThat(splits.stream().noneMatch(WalkForwardSplit::holdout)).isTrue();
    }

    private static double[] prices(int size) {
        double[] prices = new double[size];
        for (int i = 0; i < size; i++) {
            prices[i] = 100 + i;
        }
        return prices;
    }
}
