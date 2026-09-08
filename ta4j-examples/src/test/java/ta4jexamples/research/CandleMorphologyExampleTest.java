/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.research;

import org.junit.Assert;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;

public class CandleMorphologyExampleTest {

    @Test
    public void documentedMorphologyHoldsOnLastBar() {
        BarSeries series = CandleMorphologyExample.buildSeries();
        Rule customMorphology = CandleMorphologyExample.customMorphology(series);

        Assert.assertTrue(customMorphology.isSatisfied(series.getEndIndex()));
    }
}
