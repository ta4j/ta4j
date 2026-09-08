/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.research;

import org.junit.Assert;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;

public class NamedPatternContextExampleTest {

    @Test
    public void documentedPatternAndContextHoldOnLastBar() {
        BarSeries series = NamedPatternContextExample.buildSeries();
        Rule pattern = NamedPatternContextExample.pattern(series);
        Rule priorDowntrend = NamedPatternContextExample.priorDowntrend(series);
        int index = series.getEndIndex();

        Assert.assertTrue(pattern.isSatisfied(index));
        Assert.assertTrue(priorDowntrend.isSatisfied(index));
        Assert.assertTrue(pattern.and(priorDowntrend).isSatisfied(index));
    }

    @Test
    public void combinedWarmUpBoundaryMatchesTheDocumentedIndex() {
        BarSeries series = NamedPatternContextExample.buildSeries();

        Assert.assertEquals(21, NamedPatternContextExample.firstReliableIndex(series));
    }
}
