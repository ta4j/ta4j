/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

public class GeneticStrategySearchExampleTest {

    @Test
    public void runSearchIsDeterministic() {
        BarSeries series = new MockBarSeriesBuilder()
                .withData(100d, 101d, 104d, 108d, 105d, 111d, 109d, 116d, 118d, 121d, 119d, 124d, 126d, 129d, 127d,
                        133d, 136d, 138d, 140d, 143d)
                .build();

        GeneticStrategySearchExample.SearchRun first = GeneticStrategySearchExample.runSearch(series);
        GeneticStrategySearchExample.SearchRun second = GeneticStrategySearchExample.runSearch(series);

        List<String> firstIds = first.searchResult().topCandidates().stream().map(candidate -> candidate.id()).toList();
        List<String> secondIds = second.searchResult()
                .topCandidates()
                .stream()
                .map(candidate -> candidate.id())
                .toList();

        assertEquals(firstIds, secondIds);
        assertFalse(first.topStatements().isEmpty());
        assertEquals(first.topStatements().get(0).getStrategy().getName(),
                second.topStatements().get(0).getStrategy().getName());
    }
}
