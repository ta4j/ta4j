/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NumFactory;

public class BasePerformanceReportTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    public void equalsAndHashCodeUseAllMetrics() {
        BasePerformanceReport report = report(10, 5, 12, -2);
        BasePerformanceReport same = report(10, 5, 12, -2);
        BasePerformanceReport differentLoss = report(10, 5, 12, -3);

        assertEquals(report, same);
        assertEquals(report.hashCode(), same.hashCode());
        assertNotEquals(report, differentLoss);
    }

    @Test
    public void compareToUsesRemainingMetricsAsTieBreakers() {
        BasePerformanceReport lowerPercentage = report(10, 5, 12, -2);
        BasePerformanceReport higherPercentage = report(10, 6, 12, -2);

        assertTrue(lowerPercentage.compareTo(higherPercentage) < 0);
        assertEquals(0, lowerPercentage.compareTo(report(10, 5, 12, -2)));
    }

    private static BasePerformanceReport report(int totalProfitLoss, int totalProfitLossPercentage, int totalProfit,
            int totalLoss) {
        return new BasePerformanceReport(NUM_FACTORY.numOf(totalProfitLoss),
                NUM_FACTORY.numOf(totalProfitLossPercentage), NUM_FACTORY.numOf(totalProfit),
                NUM_FACTORY.numOf(totalLoss));
    }
}
