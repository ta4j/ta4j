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
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PortfolioSeriesManagerTest {

    @Test
    public void runsStaticTargetWeightsWithoutChangingSingleSeriesApis() {
        Fixture fixture = fixture(new double[] { 100, 110, 120 }, new double[] { 50, 40, 60 });
        PortfolioAllocation allocation = allocation(fixture, 0.6, 0.4);

        PortfolioSeriesManager manager = new PortfolioSeriesManager(fixture.series());
        PortfolioExecutionResult result = manager.run(allocation, fixture.num(1000));

        assertEquals(fixture.series(), manager.getPortfolioSeries());
        assertEquals(3, result.getSnapshots().size());
        assertNumEquals(6, result.getSnapshots().get(0).getHoldings().get(fixture.alpha()));
        assertNumEquals(8, result.getSnapshots().get(0).getHoldings().get(fixture.beta()));
        assertNumEquals(0, result.getSnapshots().get(0).getCash());
        assertNumEquals(1000, result.getSnapshots().get(0).getPortfolioValue());
        assertNumEquals(-0.02, result.getSnapshots().get(1).getPeriodReturn());
        assertNumEquals(1200, result.getFinalValue());
        assertNumEquals(0.2, result.getTotalReturn());
        assertEquals(List.of(fixture.alpha(), fixture.beta()), List.copyOf(result.getFinalWeights().keySet()));
        assertNumEquals(0.6, result.getFinalWeights().get(fixture.alpha()));
        assertNumEquals(0.4, result.getFinalWeights().get(fixture.beta()));
    }

    @Test
    public void everyBarRebalanceRestoresTargetWeightsAfterPriceDrift() {
        Fixture fixture = fixture(new double[] { 100, 200 }, new double[] { 100, 50 });
        PortfolioAllocation allocation = allocation(fixture, 0.5, 0.5);

        PortfolioExecutionResult result = new PortfolioSeriesManager(fixture.series()).run(allocation,
                fixture.num(1000), RebalancePolicy.everyBar());

        PortfolioSnapshot finalSnapshot = result.getFinalSnapshot();
        assertNumEquals(3.125, finalSnapshot.getHoldings().get(fixture.alpha()));
        assertNumEquals(12.5, finalSnapshot.getHoldings().get(fixture.beta()));
        assertNumEquals(1250, finalSnapshot.getPortfolioValue());
        assertNumEquals(0.5, finalSnapshot.getAssetWeight(fixture.alpha()));
        assertNumEquals(0.5, finalSnapshot.getAssetWeight(fixture.beta()));
        assertNumEquals(750, finalSnapshot.getTurnover());
    }

    @Test
    public void transactionCostsScaleInitialBuysSoCashDoesNotGoNegative() {
        Fixture fixture = fixture(new double[] { 100, 100 }, new double[] { 50, 50 });
        PortfolioAllocation allocation = allocation(fixture, 0.6, 0.4);

        PortfolioExecutionResult result = new PortfolioSeriesManager(fixture.series(),
                new LinearTransactionCostModel(0.01)).run(allocation, fixture.num(1000));

        PortfolioSnapshot firstSnapshot = result.getSnapshots().getFirst();
        assertNumEquals(fixture.num(0), firstSnapshot.getCash(), 0.0001);
        assertNumEquals(fixture.num(990.0990099), firstSnapshot.getPortfolioValue(), 0.0001);
        assertNumEquals(fixture.num(9.9009901), firstSnapshot.getTransactionCost(), 0.0001);
        assertNumEquals(fixture.num(990.0990099), firstSnapshot.getTurnover(), 0.0001);
        assertNumEquals(fixture.num(-0.00990099), firstSnapshot.getPeriodReturn(), 0.0001);
    }

    @Test
    public void transactionCostsPreserveCashSleeveTargetWeight() {
        Fixture fixture = fixture(new double[] { 100, 100 }, new double[] { 50, 50 });
        PortfolioAllocation allocation = allocation(fixture, 0.6, 0.3);

        PortfolioExecutionResult result = new PortfolioSeriesManager(fixture.series(),
                new LinearTransactionCostModel(0.01)).run(allocation, fixture.num(1000));

        PortfolioSnapshot firstSnapshot = result.getSnapshots().getFirst();
        assertNumEquals(fixture.num(991.0802775), firstSnapshot.getPortfolioValue(), 0.0001);
        assertNumEquals(fixture.num(99.1080278), firstSnapshot.getCash(), 0.0001);
        assertNumEquals(fixture.num(8.9197225), firstSnapshot.getTransactionCost(), 0.0001);
        assertNumEquals(fixture.num(891.9722498), firstSnapshot.getTurnover(), 0.0001);
        assertNumEquals(fixture.num(0.6), firstSnapshot.getAssetWeight(fixture.alpha()), 0.0001);
        assertNumEquals(fixture.num(0.3), firstSnapshot.getAssetWeight(fixture.beta()), 0.0001);
        assertNumEquals(fixture.num(0.1), firstSnapshot.getCash().dividedBy(firstSnapshot.getPortfolioValue()), 0.0001);
    }

    @Test
    public void fixedTransactionCostsPreserveCashSleeveTargetWeight() {
        Fixture fixture = fixture(new double[] { 100, 100 }, new double[] { 50, 50 });
        PortfolioAllocation allocation = allocation(fixture, 0.6, 0.3);

        PortfolioExecutionResult result = new PortfolioSeriesManager(fixture.series(), new FixedTransactionCostModel(5))
                .run(allocation, fixture.num(1000));

        PortfolioSnapshot firstSnapshot = result.getSnapshots().getFirst();
        assertNumEquals(fixture.num(990), firstSnapshot.getPortfolioValue(), 0.0001);
        assertNumEquals(fixture.num(99), firstSnapshot.getCash(), 0.0001);
        assertNumEquals(10, firstSnapshot.getTransactionCost());
        assertNumEquals(fixture.num(891), firstSnapshot.getTurnover(), 0.0001);
        assertNumEquals(0.6, firstSnapshot.getAssetWeight(fixture.alpha()));
        assertNumEquals(0.3, firstSnapshot.getAssetWeight(fixture.beta()));
        assertNumEquals(fixture.num(0.1), firstSnapshot.getCash().dividedBy(firstSnapshot.getPortfolioValue()), 0.0001);
    }

    @Test
    public void fixedTransactionCostsSkipSellThatWouldSpendCashNegative() {
        Fixture fixture = fixture(new double[] { 100, 101 }, new double[] { 100, 100 });
        PortfolioAllocation allocation = allocation(fixture, 0.01, 0.99);

        PortfolioExecutionResult result = new PortfolioSeriesManager(fixture.series(),
                new FixedTransactionCostModel(50)).run(allocation, fixture.num(1000), RebalancePolicy.everyBar());

        PortfolioSnapshot firstSnapshot = result.getSnapshots().get(0);
        PortfolioSnapshot secondSnapshot = result.getSnapshots().get(1);
        assertNumEquals(firstSnapshot.getHoldings().get(fixture.alpha()),
                secondSnapshot.getHoldings().get(fixture.alpha()));
        assertNumEquals(fixture.num(48.9109), secondSnapshot.getCash(), 0.0001);
        assertNumEquals(fixture.num(50), secondSnapshot.getTransactionCost(), 0.0001);
    }

    @Test
    public void rejectsAllocationAssetsMissingFromAlignedSeries() {
        Fixture fixture = fixture(new double[] { 100, 100 }, new double[] { 50, 50 });
        PortfolioAllocation allocation = new PortfolioAllocation(Map.of("MISSING", fixture.num(0.5)),
                fixture.alphaSeries().numFactory());
        PortfolioSeriesManager manager = new PortfolioSeriesManager(fixture.series());

        assertThrows(IllegalArgumentException.class, () -> manager.run(allocation, fixture.num(1000)));
    }

    @Test
    public void rejectsNullInitialCash() {
        Fixture fixture = fixture(new double[] { 100, 100 }, new double[] { 50, 50 });
        PortfolioAllocation allocation = allocation(fixture, 0.6, 0.4);

        PortfolioSeriesManager manager = new PortfolioSeriesManager(fixture.series());

        assertThrows(NullPointerException.class, () -> manager.run(allocation, null));
    }

    @Test
    public void convertsAllocationWeightsToPortfolioNumFactory() {
        Fixture fixture = fixture(new double[] { 100, 110 }, new double[] { 50, 55 });
        Map<String, Num> weights = new LinkedHashMap<>();
        weights.put(fixture.alpha(), DecimalNumFactory.getInstance().numOf(0.6));
        weights.put(fixture.beta(), DecimalNumFactory.getInstance().numOf(0.4));
        PortfolioAllocation allocation = new PortfolioAllocation(weights, DecimalNumFactory.getInstance());

        PortfolioExecutionResult result = new PortfolioSeriesManager(fixture.series()).run(allocation,
                fixture.num(1000));

        assertNumEquals(1100, result.getFinalValue());
    }

    @Test
    public void acceptsFiniteHighPrecisionDecimalInputs() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        String alpha = "ALPHA";
        String beta = "BETA";
        BarSeries alphaSeries = decimalSeries(alpha, numFactory, "1E400", "1E400");
        BarSeries betaSeries = decimalSeries(beta, numFactory, "2E400", "2E400");
        PortfolioSeries portfolioSeries = new PortfolioSeries(alphaSeries, betaSeries);
        Map<String, Num> weights = new LinkedHashMap<>();
        weights.put(alpha, numFactory.numOf("0.5"));
        weights.put(beta, numFactory.numOf("0.5"));
        PortfolioAllocation allocation = new PortfolioAllocation(weights, numFactory);

        PortfolioExecutionResult result = new PortfolioSeriesManager(portfolioSeries).run(allocation,
                numFactory.numOf("1E410"));

        assertEquals(2, result.getSnapshots().size());
    }

    @Test
    public void exportsPortfolioValueSeriesForExistingAnalysisFlows() {
        Fixture fixture = fixture(new double[] { 100, 110, 120 }, new double[] { 50, 40, 60 });
        PortfolioAllocation allocation = allocation(fixture, 0.6, 0.4);
        PortfolioExecutionResult result = new PortfolioSeriesManager(fixture.series()).run(allocation,
                fixture.num(1000));

        BarSeries valueSeries = result.toPortfolioValueSeries("portfolio-value");

        assertEquals("portfolio-value", valueSeries.getName());
        assertEquals(3, valueSeries.getBarCount());
        assertEquals(fixture.series().getEndTimes().get(2), valueSeries.getBar(2).getEndTime());
        assertNumEquals(1200, valueSeries.getBar(2).getClosePrice());
    }

    private static PortfolioAllocation allocation(Fixture fixture, double alphaWeight, double betaWeight) {
        Map<String, Num> weights = new LinkedHashMap<>();
        weights.put(fixture.alpha(), fixture.num(alphaWeight));
        weights.put(fixture.beta(), fixture.num(betaWeight));
        return new PortfolioAllocation(weights, fixture.alphaSeries().numFactory());
    }

    private static Fixture fixture(double[] alphaCloses, double[] betaCloses) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        String alpha = "ALPHA";
        String beta = "BETA";
        BarSeries alphaSeries = series(alpha, start, alphaCloses);
        BarSeries betaSeries = series(beta, start, betaCloses);
        PortfolioSeries series = new PortfolioSeries(alphaSeries, betaSeries);
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

    private static BarSeries decimalSeries(String name, NumFactory numFactory, String... closes) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        BarSeries series = new BaseBarSeriesBuilder().withName(name).withNumFactory(numFactory).build();
        Num zero = numFactory.zero();
        for (int i = 0; i < closes.length; i++) {
            Num close = numFactory.numOf(closes[i]);
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

    private record Fixture(String alpha, String beta, BarSeries alphaSeries, BarSeries betaSeries,
            PortfolioSeries series) {

        Num num(Number value) {
            return alphaSeries.numFactory().numOf(value);
        }
    }
}
