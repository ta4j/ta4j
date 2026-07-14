/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

final class LPPLTestFixtures {

    static final int WINDOW = 80;

    private LPPLTestFixtures() {
    }

    static LPPLCalibrationProfile compactProfile() {
        return LPPLCalibrationProfile.defaults()
                .withWindow(WINDOW)
                .withExponentSearch(0.1, 0.9, 5)
                .withFrequencySearch(7.5, 8.5, 3)
                .withCriticalTimeSearch(10, 30, 5)
                .withOptimizerSettings(80, 0.6);
    }

    static BarSeries syntheticSeries(double b) {
        return syntheticSeries(b, 0.0);
    }

    static BarSeries syntheticSeries(double b, double evaluationShock) {
        return new MockBarSeriesBuilder().withData(syntheticPrices(b, evaluationShock)).build();
    }

    static double[] syntheticPrices(double b, double evaluationShock) {
        double[] prices = new double[WINDOW + 2];
        double criticalTime = WINDOW + 20.0;
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
            if (i == WINDOW) {
                logPrice += evaluationShock;
            }
            prices[i] = Math.exp(logPrice);
        }
        return prices;
    }
}
