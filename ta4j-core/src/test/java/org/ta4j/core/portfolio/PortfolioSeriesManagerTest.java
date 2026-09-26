/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.portfolio.PortfolioFixtures.assertNumClose;
import static org.ta4j.core.portfolio.PortfolioFixtures.series;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.portfolio.PortfolioSnapshot.RebalanceStatus;

public class PortfolioSeriesManagerTest {

    private static final double TOLERANCE = 1e-6;

    @Test
    public void defaultRunIsBuyAndHold() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 110, 120 }, new double[] { 50, 40, 60 });

        PortfolioExecutionResult result = manager.run(weights(0.6, 0.4), 1000);

        List<PortfolioSnapshot> snapshots = result.getSnapshots();
        assertEquals(List.of(RebalanceStatus.COMPLETED, RebalanceStatus.NOT_SCHEDULED, RebalanceStatus.NOT_SCHEDULED),
                snapshots.stream().map(PortfolioSnapshot::getRebalanceStatus).toList());
        assertNumEquals(6, snapshots.get(0).getHoldings().get("ALPHA"));
        assertNumEquals(8, snapshots.get(0).getHoldings().get("BETA"));
        assertNumEquals(0, snapshots.get(0).getCash());
        assertNumEquals(1, snapshots.get(0).getTurnover());
        assertNumEquals(-0.02, snapshots.get(1).getPeriodReturn());
        assertNumEquals(1200, result.getFinalValue());
        assertNumEquals(0.2, result.getTotalReturn());
        assertEquals(List.of("ALPHA", "BETA"), List.copyOf(result.getFinalWeights().keySet()));
        assertNumEquals(0.6, result.getFinalWeights().get("ALPHA"));
    }

    @Test
    public void everyBarRebalanceRestoresTargetWeightsAfterPriceDrift() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 200 }, new double[] { 100, 50 });

        PortfolioExecutionResult result = manager.run(weights(0.5, 0.5), 1000, RebalancePolicy.everyBar());

        PortfolioSnapshot last = result.getFinalSnapshot();
        assertNumEquals(3.125, last.getHoldings().get("ALPHA"));
        assertNumEquals(12.5, last.getHoldings().get("BETA"));
        assertNumEquals(1250, last.getPortfolioValue());
        assertNumEquals(750, last.getTradedNotional());
        assertNumEquals(0.6, last.getTurnover());
        assertEquals(RebalanceStatus.COMPLETED, last.getRebalanceStatus());
    }

    @Test
    public void scheduleControlsInitialInvestment() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 100, 100, 110 }, new double[] { 50, 50, 50, 50 });

        PortfolioExecutionResult delayed = manager.run(weights(0.5, 0.5), 1000, RebalancePolicy.onIndexes(2));
        PortfolioExecutionResult investedThenRebalanced = manager.run(weights(0.5, 0.5), 1000,
                RebalancePolicy.atStart().or(RebalancePolicy.onIndexes(2)));

        assertNumEquals(1000, delayed.getSnapshots().get(1).getCash());
        assertEquals(RebalanceStatus.NOT_SCHEDULED, delayed.getSnapshots().get(1).getRebalanceStatus());
        assertEquals(RebalanceStatus.COMPLETED, delayed.getSnapshots().get(2).getRebalanceStatus());
        assertNumEquals(0, investedThenRebalanced.getSnapshots().get(1).getCash());
        assertNumEquals(0, investedThenRebalanced.getSnapshots().get(2).getTradedNotional());
        assertNumEquals(1050, delayed.getFinalValue());
    }

    @Test
    public void singleAssetWithCashSleeveIsABenchmark() {
        PortfolioSeriesManager manager = new PortfolioSeriesManager(new PortfolioSeries(series("SPY", 100, 110)));

        PortfolioExecutionResult buyAndHold = manager.run(new PortfolioAllocation(Map.of("SPY", 0.6)), 1000);
        PortfolioExecutionResult rebalanced = manager.run(new PortfolioAllocation(Map.of("SPY", 0.6)), 1000,
                RebalancePolicy.everyBar());

        assertNumEquals(1060, buyAndHold.getFinalValue());
        assertNumEquals(400, buyAndHold.getFinalSnapshot().getCash());
        assertNumEquals(0.4, rebalanced.getFinalSnapshot().getCashWeight());
        assertNumEquals(0.6, rebalanced.getFinalSnapshot().getAssetWeight("SPY"));
    }

    @Test
    public void proportionalCostsAreFundedFromThePostCostValue() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 100 }, new double[] { 50, 50 },
                new LinearTransactionCostModel(0.01));

        PortfolioSnapshot fullyInvested = manager.run(weights(0.6, 0.4), 1000).getSnapshots().getFirst();
        PortfolioSnapshot cashSleeve = manager.run(weights(0.6, 0.3), 1000).getSnapshots().getFirst();

        assertNumClose(0, fullyInvested.getCash(), TOLERANCE);
        assertNumClose(990.0990099, fullyInvested.getPortfolioValue(), TOLERANCE);
        assertNumClose(9.9009901, fullyInvested.getTransactionCost(), TOLERANCE);
        assertNumClose(-0.00990099, fullyInvested.getPeriodReturn(), TOLERANCE);
        assertNumClose(991.0802775, cashSleeve.getPortfolioValue(), TOLERANCE);
        assertNumClose(8.9197225, cashSleeve.getTransactionCost(), TOLERANCE);
        assertNumClose(0.6, cashSleeve.getAssetWeight("ALPHA"), TOLERANCE);
        assertNumClose(0.1, cashSleeve.getCashWeight(), TOLERANCE);
        assertEquals(RebalanceStatus.COMPLETED, cashSleeve.getRebalanceStatus());
    }

    @Test
    public void flatPricesWithFixedFeesNeverChurnAfterTheInitialInvestment() {
        for (NumFactory numFactory : List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance())) {
            PortfolioSeries series = new PortfolioSeries(series("ALPHA", numFactory, 100, 100, 100, 100),
                    series("BETA", numFactory, 100, 100, 100, 100));
            PortfolioSeriesManager manager = new PortfolioSeriesManager(series, new FixedTransactionCostModel(5));

            PortfolioExecutionResult result = manager.run(weights(0.6, 0.3), 1000, RebalancePolicy.everyBar());

            assertNumEquals(10, result.getSnapshots().getFirst().getTransactionCost());
            for (PortfolioSnapshot snapshot : result.getSnapshots()) {
                assertNumClose(990, snapshot.getPortfolioValue(), TOLERANCE);
                assertNumClose(0.1, snapshot.getCashWeight(), TOLERANCE);
                assertEquals(RebalanceStatus.COMPLETED, snapshot.getRebalanceStatus());
            }
            for (PortfolioSnapshot snapshot : result.getSnapshots().subList(1, 4)) {
                assertNumEquals(0, snapshot.getTradedNotional());
                assertNumEquals(0, snapshot.getTransactionCost());
            }
        }
    }

    @Test
    public void executionIsIndependentOfAssetPresentationOrder() {
        CostModel fixedFee = new FixedTransactionCostModel(50);
        BarSeries alpha = series("ALPHA", 100, 101);
        BarSeries beta = series("BETA", 100, 100);
        PortfolioAllocation allocation = weights(0.01, 0.99);

        PortfolioSnapshot alphaFirst = new PortfolioSeriesManager(new PortfolioSeries(alpha, beta), fixedFee)
                .run(allocation, 1000, RebalancePolicy.everyBar())
                .getFinalSnapshot();
        PortfolioSnapshot betaFirst = new PortfolioSeriesManager(new PortfolioSeries(beta, alpha), fixedFee)
                .run(allocation, 1000, RebalancePolicy.everyBar())
                .getFinalSnapshot();

        for (String asset : List.of("ALPHA", "BETA")) {
            assertNumEquals(alphaFirst.getHoldings().get(asset), betaFirst.getHoldings().get(asset));
        }
        assertNumEquals(alphaFirst.getCash(), betaFirst.getCash());
        assertNumEquals(alphaFirst.getTransactionCost(), betaFirst.getTransactionCost());
        assertEquals(alphaFirst.getRebalanceStatus(), betaFirst.getRebalanceStatus());
        assertTrue(alphaFirst.getCash().isPositiveOrZero());
    }

    @Test
    public void unaffordableRebalanceIsReportedAsSkippedWithoutTrading() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 100 }, new double[] { 50, 50 },
                new FixedTransactionCostModel(60));

        PortfolioExecutionResult result = manager.run(weights(0.5, 0.5), 100);

        PortfolioSnapshot first = result.getSnapshots().getFirst();
        assertEquals(RebalanceStatus.SKIPPED, first.getRebalanceStatus());
        assertNumEquals(100, first.getCash());
        assertNumEquals(0, first.getTransactionCost());
        assertEquals(List.of(first), result.getSnapshots(RebalanceStatus.SKIPPED));
        assertEquals(1, result.getRebalanceCount(RebalanceStatus.SKIPPED));
    }

    @Test
    public void accountingIsInvariantToMonetaryScale() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        PortfolioSnapshot tiny = scaledRun(numFactory, "1E-14", "1E-13");
        PortfolioSnapshot ordinary = scaledRun(numFactory, "100", "1000");

        assertNumEquals(ordinary.getAssetWeight("ALPHA"), tiny.getAssetWeight("ALPHA"), 1e-12);
        assertNumEquals(ordinary.getAssetWeight("BETA"), tiny.getAssetWeight("BETA"), 1e-12);
        assertNumEquals(ordinary.getCashWeight(), tiny.getCashWeight(), 1e-12);
        assertNumClose(0, tiny.getCashWeight(), 1e-12);
        assertNumEquals(ordinary.getTransactionCost().dividedBy(numFactory.numOf(1000)),
                tiny.getTransactionCost().dividedBy(numFactory.numOf("1E-13")), 1e-12);
    }

    @Test
    public void convertsInputsToThePortfolioNumFactory() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 110 }, new double[] { 50, 55 });
        Map<String, Num> decimalWeights = new LinkedHashMap<>();
        decimalWeights.put("ALPHA", DecimalNumFactory.getInstance().numOf("0.6"));
        decimalWeights.put("BETA", DecimalNumFactory.getInstance().numOf("0.4"));

        PortfolioExecutionResult result = manager.run(
                new PortfolioAllocation(decimalWeights, DecimalNumFactory.getInstance()),
                DecimalNumFactory.getInstance().numOf(1000), RebalancePolicy.atStart());

        assertNumEquals(1100, result.getFinalValue());
        assertTrue(DoubleNumFactory.getInstance().produces(result.getFinalValue()));
    }

    @Test
    public void rejectsInvalidRunInputs() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 100 }, new double[] { 50, 50 });
        PortfolioAllocation allocation = weights(0.5, 0.5);

        assertThrows(IllegalArgumentException.class,
                () -> manager.run(new PortfolioAllocation(Map.of("MISSING", 0.5)), 1000));
        assertThrows(NullPointerException.class, () -> manager.run(allocation, (Num) null));
        assertThrows(IllegalArgumentException.class, () -> manager.run(allocation, 0));
        assertThrows(NullPointerException.class, () -> manager.run(allocation, 1000, null));
    }

    @Test
    public void rejectsNonPositivePricesWithAssetAndTime() {
        PortfolioSeriesManager manager = manager(new double[] { 100, 0 }, new double[] { 50, 50 });

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> manager.run(weights(0.5, 0.5), 1000));

        assertTrue(exception.getMessage().contains("ALPHA at 2026-01-02T00:00:00Z"));
    }

    private static PortfolioSnapshot scaledRun(NumFactory numFactory, String price, String capital) {
        PortfolioSeries series = new PortfolioSeries(series("ALPHA", numFactory, 0, price, price),
                series("BETA", numFactory, 0, price, price));
        return new PortfolioSeriesManager(series, new LinearTransactionCostModel(0.01))
                .run(weights(0.6, 0.4), numFactory.numOf(capital), RebalancePolicy.atStart())
                .getFinalSnapshot();
    }

    private static PortfolioAllocation weights(double alpha, double beta) {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("ALPHA", alpha);
        weights.put("BETA", beta);
        return new PortfolioAllocation(weights);
    }

    private static PortfolioSeriesManager manager(double[] alphaCloses, double[] betaCloses) {
        return new PortfolioSeriesManager(
                new PortfolioSeries(series("ALPHA", alphaCloses), series("BETA", betaCloses)));
    }

    private static PortfolioSeriesManager manager(double[] alphaCloses, double[] betaCloses, CostModel costModel) {
        return new PortfolioSeriesManager(new PortfolioSeries(series("ALPHA", alphaCloses), series("BETA", betaCloses)),
                costModel);
    }
}
