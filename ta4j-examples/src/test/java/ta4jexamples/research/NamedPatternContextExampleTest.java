/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.research;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;

public class NamedPatternContextExampleTest {

    @Test
    public void documentedPatternAndContextHoldOnLastBar() {
        BarSeries series = NamedPatternContextExample.buildSeries();
        Rule pattern = NamedPatternContextExample.pattern(series);
        Rule priorDowntrend = NamedPatternContextExample.priorDowntrend(series);
        int index = series.getEndIndex();

        Assertions.assertTrue(pattern.isSatisfied(index));
        Assertions.assertTrue(priorDowntrend.isSatisfied(index));
        Assertions.assertTrue(pattern.and(priorDowntrend).isSatisfied(index));
    }

    @Test
    public void combinedWarmUpBoundaryMatchesTheDocumentedIndex() {
        BarSeries series = NamedPatternContextExample.buildSeries();

        Assertions.assertEquals(21, NamedPatternContextExample.firstReliableIndex(series));
    }
}
