/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.portfolio.MinimumVarianceOptimizer.CovarianceEstimator.LEDOIT_WOLF;
import static org.ta4j.core.portfolio.MinimumVarianceOptimizer.CovarianceEstimator.SAMPLE;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

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

        PortfolioAllocation allocation = new MinimumVarianceOptimizer(series, 0.6).optimize();

        assertNumEquals(numFactory.numOf(0.6), allocation.getTargetWeight("LOW"), 0.000001);
        assertNumEquals(numFactory.numOf(0.4), allocation.getTargetWeight("HIGH"), 0.000001);
        assertThrows(IllegalArgumentException.class, () -> new MinimumVarianceOptimizer(series, 0.49));
        assertThrows(IllegalArgumentException.class, () -> new MinimumVarianceOptimizer(series, 1.01));
        PortfolioSeries decimalSeries = orthogonalReturnSeries(DecimalNumFactory.getInstance(), false);
        assertEquals(0.6,
                new MinimumVarianceOptimizer(decimalSeries, new BigDecimal("0.6")).optimize()
                        .getTargetWeight("LOW")
                        .doubleValue(),
                0.000001);
        assertThrows(IllegalArgumentException.class, () -> new MinimumVarianceOptimizer(decimalSeries, Double.NaN));
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

    @Test
    public void satisfiesKktConditionsWhenBoundsAndCapBind() {
        for (NumFactory numFactory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            PortfolioSeries series = factorSeries(numFactory);
            // assertKkt checks optimality against the sample covariance.
            PortfolioAllocation uncapped = new MinimumVarianceOptimizer(series).withCovarianceEstimator(SAMPLE)
                    .optimize();
            PortfolioAllocation capped = new MinimumVarianceOptimizer(series, 0.3).withCovarianceEstimator(SAMPLE)
                    .optimize();

            assertKkt(series, uncapped, 1.0);
            assertKkt(series, capped, 0.3);
            assertTrue("a high-beta asset should be excluded",
                    uncapped.getTargetWeights().values().stream().anyMatch(Num::isZero));
            assertTrue("the cap should bind",
                    capped.getTargetWeights().values().stream().anyMatch(weight -> weight.doubleValue() == 0.3));
        }
    }

    @Test
    public void ledoitWolfMatchesScikitLearnAndDiversifiesShortWindows() {
        for (NumFactory numFactory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            PortfolioSeries series = shortWindowSeries(numFactory);
            MinimumVarianceOptimizer shrunk = new MinimumVarianceOptimizer(series);
            MinimumVarianceOptimizer sample = shrunk.withCovarianceEstimator(SAMPLE);

            // sklearn.covariance.LedoitWolf().fit(returns).covariance_ (shrinkage
            // 0.10631429193176394).
            double[][] expected = {
                    { 3.046347632177808e-4, 3.8478231678591994e-4, -1.8687352931656933e-4, 6.359082257700359e-4,
                            6.974182077110434e-5 },
                    { 3.8478231678591994e-4, 6.898259479693941e-4, -3.007257124887523e-4, 1.0291400191776844e-3,
                            1.1277304805115729e-4 },
                    { -1.8687352931656933e-4, -3.007257124887523e-4, 2.7776001146416235e-4, -5.01397411988546e-4,
                            -5.6500842315157095e-5 },
                    { 6.359082257700359e-4, 1.0291400191776844e-3, -5.01397411988546e-4, 1.7728620562621155e-3,
                            1.8650943472134682e-4 },
                    { 6.974182077110434e-5, 1.1277304805115729e-4, -5.6500842315157095e-5, 1.8650943472134682e-4,
                            8.708640681416195e-5 } };
            Num[][] covariance = shrunk.covarianceMatrix();
            for (int row = 0; row < expected.length; row++) {
                for (int column = 0; column < expected.length; column++) {
                    assertEquals(expected[row][column], covariance[row][column].doubleValue(), 1e-15);
                }
            }

            // scipy.optimize SLSQP on the shrunk covariance, uncapped and capped at 35%.
            assertWeights(shrunk.optimize(), 0.203832142, 0.023694976, 0.362084844, 0, 0.410388038);
            assertWeights(new MinimumVarianceOptimizer(series, 0.35).optimize(), 0.296695947, 0.003304053, 0.35, 0,
                    0.35);
            // The sample estimate concentrates on two assets.
            assertWeights(sample.optimize(), 0, 0, 0.223381106, 0, 0.776618894);
            // Ledoit-Wolf is the default.
            assertEquals(shrunk.optimize().getTargetWeights(),
                    sample.withCovarianceEstimator(LEDOIT_WOLF).optimize().getTargetWeights());
        }
        assertThrows(NullPointerException.class,
                () -> new MinimumVarianceOptimizer(shortWindowSeries(DoubleNumFactory.getInstance()))
                        .withCovarianceEstimator(null));
    }

    @Test
    public void ledoitWolfLeavesZeroVarianceWindowsAtEqualWeight() {
        PortfolioSeries flat = new PortfolioSeries(series("FLAT_A", DoubleNumFactory.getInstance(), 100, 100, 100),
                series("FLAT_B", DoubleNumFactory.getInstance(), 50, 50, 50));

        PortfolioAllocation allocation = new MinimumVarianceOptimizer(flat).optimize();

        assertNumEquals(0.5, allocation.getTargetWeight("FLAT_A"));
        assertNumEquals(0.5, allocation.getTargetWeight("FLAT_B"));
    }

    @Test
    public void handlesSingleAssetAndZeroVarianceWindows() {
        NumFactory numFactory = DoubleNumFactory.getInstance();
        PortfolioSeries single = new PortfolioSeries(series("ONLY", numFactory, 100, 101, 99, 102));
        PortfolioSeries flat = new PortfolioSeries(series("FLAT_A", numFactory, 100, 100, 100),
                series("FLAT_B", numFactory, 50, 50, 50));

        assertNumEquals(1, new MinimumVarianceOptimizer(single).optimize().getTargetWeight("ONLY"));
        PortfolioAllocation equal = new MinimumVarianceOptimizer(flat).optimize();
        assertNumEquals(0.5, equal.getTargetWeight("FLAT_A"));
        assertNumEquals(0.5, equal.getTargetWeight("FLAT_B"));
    }

    /**
     * KKT conditions of min w'Cw subject to sum w = 1 and 0 <= w <= cap: with
     * gradient g = 2Cw there is a multiplier lambda such that g_i = lambda for
     * interior weights, g_i >= lambda at zero, and g_i <= lambda at the cap.
     */
    private static void assertKkt(PortfolioSeries series, PortfolioAllocation allocation, double cap) {
        List<String> assets = series.getAssets();
        double[][] returns = new double[assets.size()][series.getBarCount() - 1];
        for (int asset = 0; asset < assets.size(); asset++) {
            for (int bar = 1; bar < series.getBarCount(); bar++) {
                returns[asset][bar - 1] = series.getClosePrice(assets.get(asset), bar).doubleValue()
                        / series.getClosePrice(assets.get(asset), bar - 1).doubleValue() - 1;
            }
        }
        double[] weights = new double[assets.size()];
        double total = 0;
        for (int asset = 0; asset < assets.size(); asset++) {
            weights[asset] = allocation.getTargetWeight(assets.get(asset)).doubleValue();
            total += weights[asset];
            assertTrue(weights[asset] >= -1e-12 && weights[asset] <= cap + 1e-12);
        }
        assertEquals(1, total, 1e-9);

        double[] gradient = new double[assets.size()];
        for (int row = 0; row < assets.size(); row++) {
            for (int column = 0; column < assets.size(); column++) {
                gradient[row] += 2 * covariance(returns[row], returns[column]) * weights[column];
            }
        }
        double lambda = Double.NaN;
        for (int asset = 0; asset < assets.size(); asset++) {
            if (weights[asset] > 1e-9 && weights[asset] < cap - 1e-9) {
                lambda = gradient[asset];
            }
        }
        assertTrue("expected at least one interior weight", Double.isFinite(lambda));
        double tolerance = 1e-7 * Math.abs(lambda);
        for (int asset = 0; asset < assets.size(); asset++) {
            if (weights[asset] <= 1e-9) {
                assertTrue(gradient[asset] >= lambda - tolerance);
            } else if (weights[asset] >= cap - 1e-9) {
                assertTrue(gradient[asset] <= lambda + tolerance);
            } else {
                assertEquals(lambda, gradient[asset], tolerance);
            }
        }
    }

    private static double covariance(double[] first, double[] second) {
        double firstMean = Arrays.stream(first).average().orElseThrow();
        double secondMean = Arrays.stream(second).average().orElseThrow();
        double sum = 0;
        for (int index = 0; index < first.length; index++) {
            sum += (first[index] - firstMean) * (second[index] - secondMean);
        }
        return sum / first.length;
    }

    private static void assertWeights(PortfolioAllocation allocation, double... expected) {
        List<Num> weights = new ArrayList<>(allocation.getTargetWeights().values());
        for (int asset = 0; asset < expected.length; asset++) {
            assertEquals(expected[asset], weights.get(asset).doubleValue(), 1e-6);
        }
    }

    /** Five assets with six returns each: short enough for shrinkage to matter. */
    private static PortfolioSeries shortWindowSeries(NumFactory numFactory) {
        return new PortfolioSeries(series("A", numFactory, 100, 101, 99.5, 102, 103, 101.5, 104),
                series("B", numFactory, 50, 50.8, 49.6, 51.5, 52.3, 51.0, 53.1),
                series("C", numFactory, 80, 79.2, 80.5, 78.9, 80.1, 81.0, 79.6),
                series("D", numFactory, 20, 20.6, 19.7, 20.9, 21.4, 20.5, 21.8),
                series("E", numFactory, 120, 120.4, 119.9, 120.8, 121.1, 120.6, 121.5));
    }

    /**
     * Six assets on one market factor with rising betas and idiosyncratic noise.
     */
    private static PortfolioSeries factorSeries(NumFactory numFactory) {
        Random random = new Random(42);
        int bars = 60;
        double[] market = new double[bars];
        for (int bar = 0; bar < bars; bar++) {
            market[bar] = random.nextGaussian() * 0.01;
        }
        List<BarSeries> assets = new ArrayList<>();
        for (int asset = 0; asset < 6; asset++) {
            double beta = 0.2 + asset * 0.4;
            double noise = 0.004 + asset * 0.001;
            double[] closes = new double[bars + 1];
            closes[0] = 100;
            for (int bar = 0; bar < bars; bar++) {
                closes[bar + 1] = closes[bar] * (1 + beta * market[bar] + random.nextGaussian() * noise);
            }
            assets.add(series("ASSET" + asset, numFactory, closes));
        }
        return new PortfolioSeries(assets);
    }

    private static void assertMinimumVarianceAllocation(NumFactory numFactory) {
        PortfolioSeries series = orthogonalReturnSeries(numFactory, false);

        PortfolioAllocation allocation = new MinimumVarianceOptimizer(series, 4, 4).withCovarianceEstimator(SAMPLE)
                .optimize();

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
