/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MinimumVarianceOptimizerTest {

    @Test
    public void matchesAnalyticalAndBruteForceMinimumWithBothNumFactories() {
        assertMinimumVarianceAllocation(DoubleNumFactory.getInstance());
        assertMinimumVarianceAllocation(DecimalNumFactory.getInstance());
    }

    @Test
    public void appliesMaximumWeightAndRejectsInfeasibleCaps() {
        PortfolioSeries series = orthogonalReturnSeries(DoubleNumFactory.getInstance(), false);
        NumFactory numFactory = series.numFactory();

        PortfolioAllocation allocation = new MinimumVarianceOptimizer(series, numFactory.numOf(0.6)).optimize();

        assertNumEquals(numFactory.numOf(0.6), allocation.getTargetWeight("LOW"), 0.000001);
        assertNumEquals(numFactory.numOf(0.4), allocation.getTargetWeight("HIGH"), 0.000001);
        assertThrows(IllegalArgumentException.class,
                () -> new MinimumVarianceOptimizer(series, numFactory.numOf(0.49)));
    }

    @Test
    public void explicitWindowDoesNotReadFutureBars() {
        PortfolioSeries stableFuture = orthogonalReturnSeries(DoubleNumFactory.getInstance(), false);
        PortfolioSeries changedFuture = orthogonalReturnSeries(DoubleNumFactory.getInstance(), true);

        PortfolioAllocation stable = new MinimumVarianceOptimizer(stableFuture, 4, 4).optimize();
        PortfolioAllocation changed = new MinimumVarianceOptimizer(changedFuture, 4, 4).optimize();
        PortfolioAllocation fullHistory = new MinimumVarianceOptimizer(changedFuture).optimize();

        assertNumEquals(stable.getTargetWeight("LOW"), changed.getTargetWeight("LOW"), 0.000001);
        assertNumEquals(stable.getTargetWeight("HIGH"), changed.getTargetWeight("HIGH"), 0.000001);
        assertNotEquals(changed.getTargetWeight("LOW").doubleValue(), fullHistory.getTargetWeight("LOW").doubleValue(),
                0.01);
    }

    @Test
    public void rejectsInvalidPricesAndInsufficientObservations() {
        NumFactory numFactory = DoubleNumFactory.getInstance();
        PortfolioSeries invalid = new PortfolioSeries(series("LOW", numFactory, 100, 110, 0, 105),
                series("HIGH", numFactory, 100, 90, 95, 100));

        assertThrows(IllegalArgumentException.class, () -> new MinimumVarianceOptimizer(invalid).optimize());
        assertThrows(IllegalArgumentException.class, () -> new MinimumVarianceOptimizer(invalid, 1, 1));
    }

    @Test
    public void rejectsCovarianceOverflowExplicitly() {
        NumFactory numFactory = DoubleNumFactory.getInstance();
        PortfolioSeries series = new PortfolioSeries(series("ALPHA", numFactory, 1e-308, 1, 1e-308),
                series("BETA", numFactory, 1e-308, 0.5, 1e-308));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new MinimumVarianceOptimizer(series).optimize());

        assertTrue(exception.getMessage().contains("covariance matrix"));
    }

    private static void assertMinimumVarianceAllocation(NumFactory numFactory) {
        PortfolioSeries series = orthogonalReturnSeries(numFactory, false);

        PortfolioAllocation allocation = new MinimumVarianceOptimizer(series, 4, 4).optimize();

        assertNumEquals(numFactory.numOf(0.8), allocation.getTargetWeight("LOW"), 0.000001);
        assertNumEquals(numFactory.numOf(0.2), allocation.getTargetWeight("HIGH"), 0.000001);
        assertNumEquals(numFactory.one(), allocation.getTotalWeight(), 0.000001);

        double bestGridWeight = 0;
        double bestGridVariance = Double.POSITIVE_INFINITY;
        for (int step = 0; step <= 1000; step++) {
            double lowWeight = step / 1000.0;
            double variance = lowWeight * lowWeight * 0.01 + (1 - lowWeight) * (1 - lowWeight) * 0.04;
            if (variance < bestGridVariance) {
                bestGridVariance = variance;
                bestGridWeight = lowWeight;
            }
        }
        assertNumEquals(numFactory.numOf(bestGridWeight), allocation.getTargetWeight("LOW"), 0.001001);
    }

    private static PortfolioSeries orthogonalReturnSeries(NumFactory numFactory, boolean changeFuture) {
        double[] lowReturns = { 0.1, -0.1, 0.1, -0.1, changeFuture ? 2.0 : 0.1 };
        double[] highReturns = { 0.2, 0.2, -0.2, -0.2, changeFuture ? -0.9 : 0.2 };
        return new PortfolioSeries(series("LOW", numFactory, closes(100, lowReturns)),
                series("HIGH", numFactory, closes(100, highReturns)));
    }

    private static double[] closes(double initialClose, double[] returns) {
        double[] closes = new double[returns.length + 1];
        closes[0] = initialClose;
        for (int index = 0; index < returns.length; index++) {
            closes[index + 1] = closes[index] * (1 + returns[index]);
        }
        return closes;
    }

    private static BarSeries series(String name, NumFactory numFactory, double... closes) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).withNumFactory(numFactory).build();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Num zero = numFactory.zero();
        for (int index = 0; index < closes.length; index++) {
            Num close = numFactory.numOf(closes[index]);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index)))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(zero)
                    .add();
        }
        return series;
    }
}
