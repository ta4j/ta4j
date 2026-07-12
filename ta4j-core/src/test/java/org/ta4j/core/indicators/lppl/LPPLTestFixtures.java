/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import java.util.Arrays;

import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

final class LPPLTestFixtures {

    static final int WINDOW = 80;

    private LPPLTestFixtures() {
    }

    static LPPLCalibrationProfile compactProfile() {
        return LPPLCalibrationProfile.defaults()
                .withWindows(WINDOW)
                .withExponentSearch(0.1, 0.9, 5)
                .withFrequencySearch(7.5, 8.5, 3)
                .withCriticalTimeSearch(10, 30, 5)
                .withActionableCriticalTimeRange(10, 30)
                .withOptimizerSettings(80, 0.6);
    }

    static BarSeries syntheticSeries(double b) {
        return syntheticSeries(b, 20);
    }

    static BarSeries syntheticSeries(double b, int criticalOffset) {
        return new MockBarSeriesBuilder().withData(syntheticPrices(b, criticalOffset)).build();
    }

    static double[] syntheticPrices(double b) {
        return syntheticPrices(b, 20);
    }

    private static double[] syntheticPrices(double b, int criticalOffset) {
        double[] prices = new double[WINDOW];
        double criticalTime = WINDOW - 1 + criticalOffset;
        double a = 4.6;
        double c1 = 0.01;
        double c2 = -0.006;
        double m = 0.5;
        double omega = 8.0;
        for (int i = 0; i < prices.length; i++) {
            double dt = criticalTime - i;
            double power = Math.pow(dt, m);
            double logDt = Math.log(dt);
            double logPrice = a + b * power + c1 * power * Math.cos(omega * logDt)
                    + c2 * power * Math.sin(omega * logDt);
            prices[i] = Math.exp(logPrice);
        }
        return Arrays.copyOf(prices, prices.length);
    }
}
