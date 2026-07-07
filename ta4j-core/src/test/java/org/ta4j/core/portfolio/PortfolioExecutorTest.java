/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;

public class PortfolioExecutorTest {

    @Test
    public void runsStaticTargetWeightsWithoutChangingSingleSeriesApis() {
        Fixture fixture = fixture(new double[] { 100, 110, 120 }, new double[] { 50, 40, 60 });
        PortfolioAllocation allocation = allocation(fixture, 0.6, 0.4);

        PortfolioExecutionResult result = new PortfolioExecutor(fixture.series(), allocation, fixture.num(1000),
                RebalancePolicy.atStart()).run();

        assertEquals(3, result.snapshots().size());
        assertNumEquals(6, result.snapshots().get(0).holdings().get(fixture.alpha()));
        assertNumEquals(8, result.snapshots().get(0).holdings().get(fixture.beta()));
        assertNumEquals(0, result.snapshots().get(0).cash());
        assertNumEquals(1000, result.snapshots().get(0).portfolioValue());
        assertNumEquals(-0.02, result.snapshots().get(1).periodReturn());
        assertNumEquals(1200, result.finalValue());
        assertNumEquals(0.2, result.totalReturn());
        assertNumEquals(0.6, result.finalWeights().get(fixture.alpha()));
        assertNumEquals(0.4, result.finalWeights().get(fixture.beta()));
    }

    @Test
    public void everyBarRebalanceRestoresTargetWeightsAfterPriceDrift() {
        Fixture fixture = fixture(new double[] { 100, 200 }, new double[] { 100, 50 });
        PortfolioAllocation allocation = allocation(fixture, 0.5, 0.5);

        PortfolioExecutionResult result = new PortfolioExecutor(fixture.series(), allocation, fixture.num(1000),
                RebalancePolicy.everyBar()).run();

        PortfolioSnapshot finalSnapshot = result.finalSnapshot();
        assertNumEquals(3.125, finalSnapshot.holdings().get(fixture.alpha()));
        assertNumEquals(12.5, finalSnapshot.holdings().get(fixture.beta()));
        assertNumEquals(1250, finalSnapshot.portfolioValue());
        assertNumEquals(0.5, finalSnapshot.assetWeight(fixture.alpha()));
        assertNumEquals(0.5, finalSnapshot.assetWeight(fixture.beta()));
        assertNumEquals(750, finalSnapshot.turnover());
    }

    @Test
    public void transactionCostsScaleInitialBuysSoCashDoesNotGoNegative() {
        Fixture fixture = fixture(new double[] { 100, 100 }, new double[] { 50, 50 });
        PortfolioAllocation allocation = allocation(fixture, 0.6, 0.4);

        PortfolioExecutionResult result = new PortfolioExecutor(fixture.series(), allocation, fixture.num(1000),
                RebalancePolicy.atStart(), new LinearTransactionCostModel(0.01)).run();

        PortfolioSnapshot firstSnapshot = result.snapshots().getFirst();
        assertNumEquals(fixture.num(0), firstSnapshot.cash(), 0.0001);
        assertNumEquals(fixture.num(990.0990099), firstSnapshot.portfolioValue(), 0.0001);
        assertNumEquals(fixture.num(9.9009901), firstSnapshot.transactionCost(), 0.0001);
        assertNumEquals(fixture.num(990.0990099), firstSnapshot.turnover(), 0.0001);
        assertNumEquals(fixture.num(-0.00990099), firstSnapshot.periodReturn(), 0.0001);
    }

    @Test
    public void rejectsAllocationAssetsMissingFromAlignedSeries() {
        Fixture fixture = fixture(new double[] { 100, 100 }, new double[] { 50, 50 });
        PortfolioAllocation allocation = PortfolioAllocation.targetWeights(
                Map.of(PortfolioAsset.of("MISSING"), fixture.num(0.5)), fixture.alphaSeries().numFactory());

        assertThrows(IllegalArgumentException.class, () -> new PortfolioExecutor(fixture.series(), allocation,
                fixture.num(1000), RebalancePolicy.atStart()));
    }

    @Test
    public void convertsAllocationWeightsToPortfolioNumFactory() {
        Fixture fixture = fixture(new double[] { 100, 110 }, new double[] { 50, 55 });
        Map<PortfolioAsset, Num> weights = new LinkedHashMap<>();
        weights.put(fixture.alpha(), DecimalNumFactory.getInstance().numOf(0.6));
        weights.put(fixture.beta(), DecimalNumFactory.getInstance().numOf(0.4));
        PortfolioAllocation allocation = PortfolioAllocation.targetWeights(weights, DecimalNumFactory.getInstance());

        PortfolioExecutionResult result = new PortfolioExecutor(fixture.series(), allocation, fixture.num(1000),
                RebalancePolicy.atStart()).run();

        assertNumEquals(1100, result.finalValue());
    }

    @Test
    public void exportsPortfolioValueSeriesForExistingAnalysisFlows() {
        Fixture fixture = fixture(new double[] { 100, 110, 120 }, new double[] { 50, 40, 60 });
        PortfolioAllocation allocation = allocation(fixture, 0.6, 0.4);
        PortfolioExecutionResult result = new PortfolioExecutor(fixture.series(), allocation, fixture.num(1000),
                RebalancePolicy.atStart()).run();

        BarSeries valueSeries = result.toPortfolioValueSeries("portfolio-value");

        assertEquals("portfolio-value", valueSeries.getName());
        assertEquals(3, valueSeries.getBarCount());
        assertEquals(fixture.series().endTimes().get(2), valueSeries.getBar(2).getEndTime());
        assertNumEquals(1200, valueSeries.getBar(2).getClosePrice());
    }

    private static PortfolioAllocation allocation(Fixture fixture, double alphaWeight, double betaWeight) {
        Map<PortfolioAsset, Num> weights = new LinkedHashMap<>();
        weights.put(fixture.alpha(), fixture.num(alphaWeight));
        weights.put(fixture.beta(), fixture.num(betaWeight));
        return PortfolioAllocation.targetWeights(weights, fixture.alphaSeries().numFactory());
    }

    private static Fixture fixture(double[] alphaCloses, double[] betaCloses) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");
        BarSeries alphaSeries = series("alpha", start, alphaCloses);
        BarSeries betaSeries = series("beta", start, betaCloses);
        AlignedPortfolioSeries series = AlignedPortfolioSeries
                .of(List.of(new PortfolioSeries(alpha, alphaSeries), new PortfolioSeries(beta, betaSeries)));
        return new Fixture(alpha, beta, alphaSeries, betaSeries, series);
    }

    private static BarSeries series(String name, Instant start, double[] closes) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).build();
        Num zero = series.numFactory().zero();
        for (int i = 0; i < closes.length; i++) {
            Num close = series.numFactory().numOf(closes[i]);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(i)))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(zero)
                    .add();
        }
        return series;
    }

    private record Fixture(PortfolioAsset alpha, PortfolioAsset beta, BarSeries alphaSeries, BarSeries betaSeries,
            AlignedPortfolioSeries series) {

        Num num(Number value) {
            return alphaSeries.numFactory().numOf(value);
        }
    }
}
