/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.alwaysInvested;
import static org.ta4j.core.criteria.RatioCriterionTestSupport.buildDailySeries;

import java.time.Instant;
import java.util.Optional;
import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.ExecutionSide;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.TradeFill;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class OmegaRatioCriterionTest extends AbstractCriterionTest {

    public OmegaRatioCriterionTest(NumFactory numFactory) {
        super(params -> new OmegaRatioCriterion((double) params[0]), numFactory);
    }

    @Test
    public void calculatesExpectedValueForMixedReturnsTradingRecord() {
        double[] closes = new double[] { 100d, 120d, 90d, 99d };
        BarSeries series = buildSeries("omega_mixed", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceOmega(returnsFromCloses(closes), 0d);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void calculatesExpectedValueForCustomThreshold() {
        double threshold = 0.05d;
        double[] closes = new double[] { 100d, 120d, 90d, 99d };
        BarSeries series = buildSeries("omega_threshold", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        OmegaRatioCriterion criterion = new OmegaRatioCriterion(threshold);
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceOmega(returnsFromCloses(closes), threshold);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void returnsPercentageRepresentation() {
        double[] closes = new double[] { 100d, 120d, 90d, 99d };
        BarSeries series = buildSeries("omega_percentage", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        OmegaRatioCriterion criterion = new OmegaRatioCriterion(ReturnRepresentation.PERCENTAGE);
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceOmega(returnsFromCloses(closes), 0d);

        assertNumEquals(numFactory.numOf(expected * 100d), actual, 1e-12);
    }

    @Test
    public void returnsMultiplicativeRepresentation() {
        double[] closes = new double[] { 100d, 120d, 90d, 99d };
        BarSeries series = buildSeries("omega_multiplicative", closes);
        TradingRecord tradingRecord = alwaysInvested(series);

        OmegaRatioCriterion criterion = new OmegaRatioCriterion(ReturnRepresentation.MULTIPLICATIVE);
        Num actual = criterion.calculate(series, tradingRecord);
        double expected = referenceOmega(returnsFromCloses(closes), 0d);

        assertNumEquals(numFactory.numOf(1d + expected), actual, 1e-12);
    }

    @Test
    public void validateThresholdMustBeFinite() {
        assertThrows(IllegalArgumentException.class, () -> new OmegaRatioCriterion(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> new OmegaRatioCriterion(Double.NaN));
    }

    @Test
    public void rejectsNullReturnRepresentation() {
        assertThrows(NullPointerException.class, () -> new OmegaRatioCriterion((ReturnRepresentation) null));
    }

    @Test
    public void returnsNaNWhenTradingRecordHasOnlyUpsideReturns() {
        BarSeries series = buildSeries("omega_positive_only", new double[] { 100d, 110d, 121d });
        TradingRecord tradingRecord = alwaysInvested(series);

        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);
        Num actual = criterion.calculate(series, tradingRecord);

        assertTrue(actual.isNaN());
    }

    @Test
    public void returnsZeroWhenTradingRecordHasOnlyDownsideReturns() {
        BarSeries series = buildSeries("omega_negative_only", new double[] { 100d, 90d, 81d });
        TradingRecord tradingRecord = alwaysInvested(series);

        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);
        Num actual = criterion.calculate(series, tradingRecord);

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void returnsZeroWhenTradingRecordHasNoPositions() {
        BarSeries series = buildSeries("omega_no_positions", new double[] { 100d, 120d, 90d, 99d });
        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);

        Num actual = criterion.calculate(series, new BaseTradingRecord());

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void returnsZeroWhenThereAreNoReturnObservations() {
        BarSeries series = buildSeries("omega_one_bar", new double[] { 100d });
        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);

        Num actual = criterion.calculate(series, new BaseTradingRecord());

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void returnsZeroWhenTradingRecordIsNull() {
        BarSeries series = buildSeries("omega_null_record", new double[] { 100d, 110d });
        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);

        Num actual = criterion.calculate(series, (TradingRecord) null);

        assertNumEquals(numFactory.zero(), actual, 0d);
    }

    @Test
    public void calculatesExpectedValueForClosedPosition() {
        double[] closes = new double[] { 100d, 120d, 90d, 99d };
        BarSeries series = buildSeries("omega_closed_position", closes);

        BaseTradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(),
                numFactory.one());
        tradingRecord.exit(series.getEndIndex(), series.getBar(series.getEndIndex()).getClosePrice(), numFactory.one());
        Position position = tradingRecord.getPositions().getFirst();

        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);
        Num actual = criterion.calculate(series, position);
        double expected = referenceOmega(returnsFromCloses(closes), 0d);

        assertNumEquals(numFactory.numOf(expected), actual, 1e-12);
    }

    @Test
    public void openPositionHandlingIgnoreReturnsZeroForOpenPosition() {
        BarSeries series = buildSeries("omega_open_position", new double[] { 100d, 120d, 80d });
        BaseTradingRecord tradingRecord = new BaseTradingRecord();
        tradingRecord.enter(series.getBeginIndex(), series.getBar(series.getBeginIndex()).getClosePrice(),
                numFactory.one());

        OmegaRatioCriterion markToMarket = new OmegaRatioCriterion(0d, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        OmegaRatioCriterion ignoreOpen = new OmegaRatioCriterion(0d, EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.IGNORE);
        OmegaRatioCriterion realized = new OmegaRatioCriterion(0d, EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);

        Num markToMarketValue = markToMarket.calculate(series, tradingRecord);
        Num ignoreOpenValue = ignoreOpen.calculate(series, tradingRecord);
        Num realizedValue = realized.calculate(series, tradingRecord);

        assertNumEquals(numFactory.numOf(0.6d), markToMarketValue, 1e-12);
        assertNumEquals(numFactory.zero(), ignoreOpenValue, 0d);
        assertNumEquals(numFactory.zero(), realizedValue, 0d);
    }

    @Test
    public void betterThanUsesHigherValuesAsBetter() {
        OmegaRatioCriterion criterion = (OmegaRatioCriterion) getCriterion(0d);

        assertTrue(criterion.betterThan(numFactory.one(), numFactory.zero()));
        assertFalse(criterion.betterThan(numFactory.zero(), numFactory.one()));
    }

    @Test
    public void exposesReturnRepresentation() {
        OmegaRatioCriterion criterion = new OmegaRatioCriterion(ReturnRepresentation.PERCENTAGE);

        assertEquals(Optional.of(ReturnRepresentation.PERCENTAGE), criterion.getReturnRepresentation());
    }

    private BarSeries buildSeries(String name, double[] closes) {
        return buildDailySeries(getBarSeries(name), closes, Instant.parse("2024-01-01T00:00:00Z"));
    }

    private double[] returnsFromCloses(double[] closes) {
        double[] returns = new double[Math.max(closes.length - 1, 0)];
        for (int i = 1; i < closes.length; i++) {
            returns[i - 1] = (closes[i] / closes[i - 1]) - 1d;
        }
        return returns;
    }

    @Test
    public void retainedSingleBarIncludesGenuineFuturesReturn() {
        BarSeries series = buildSeries("omega_retained_return", new double[] { 100, 100, 110 });
        series.setMaximumBarCount(1);
        FuturesContract contract = futuresContract();
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(1000))
                .build();
        record.operate(futuresFill(contract, 2, ExecutionSide.BUY, 1000, 100));
        assertTrue(new OmegaRatioCriterion(0).calculate(series, record).isNaN());
    }

    @Test
    public void retainedFuturesHeadExcludesAccumulatedEquitySeed() {
        BarSeries series = buildSeries("omega_retained_seed", new double[] { 100, 50, 50, 55, 49.5 });
        series.setMaximumBarCount(3);
        FuturesContract contract = futuresContract();
        BaseTradingRecord record = BaseTradingRecord.builder()
                .futuresContract(contract)
                .initialCapital(numFactory.numOf(1000))
                .build();
        record.operate(futuresFill(contract, 0, ExecutionSide.BUY, 1000, 100));
        record.operate(futuresFill(contract, 1, ExecutionSide.SELL, 1000, 50));
        record.operate(futuresFill(contract, 2, ExecutionSide.BUY, 1000, 50));
        record.operate(futuresFill(contract, 4, ExecutionSide.SELL, 1000, 49.5));
        assertNumEquals(numFactory.one(), new OmegaRatioCriterion(0).calculate(series, record), 1e-12);
    }

    @Test
    public void futuresPositionUsesEntryNotionalFallback() {
        BarSeries series = buildSeries("omega_futures_position", new double[] { 100, 120, 90, 99 });
        FuturesContract contract = futuresContract();
        BaseTradingRecord record = BaseTradingRecord.builder().futuresContract(contract).build();
        record.operate(futuresFill(contract, 0, ExecutionSide.BUY, 1000, 100));
        record.operate(futuresFill(contract, 3, ExecutionSide.SELL, 1000, 99));
        assertNumEquals(numFactory.numOf(1.2),
                new OmegaRatioCriterion(0).calculate(series, record.getPositions().getFirst()), 1e-12);
        assertThrows(IllegalStateException.class, () -> new OmegaRatioCriterion(0).calculate(series, record));
    }

    private FuturesContract futuresContract() {
        return FuturesContract.builder()
                .venue("CDE")
                .symbol("BTC-PERP")
                .productType(FuturesContract.ProductType.PERPETUAL)
                .settlementType(FuturesContract.SettlementType.LINEAR)
                .baseCurrency("BTC")
                .quoteCurrency("USD")
                .settlementCurrency("USD")
                .contractSize(numFactory.numOf(0.01))
                .build();
    }

    private TradeFill futuresFill(FuturesContract contract, int index, ExecutionSide side, double amount,
            double price) {
        return TradeFill.builder()
                .index(index)
                .time(Instant.parse("2024-01-01T00:00:00Z").plusSeconds(index))
                .price(numFactory.numOf(price))
                .amount(numFactory.numOf(amount))
                .side(side)
                .futuresContract(contract)
                .fees(java.util.List.of())
                .build();
    }

    private double referenceOmega(double[] returns, double threshold) {
        double upsideExcess = 0d;
        double downsideShortfall = 0d;
        for (double value : returns) {
            double excess = value - threshold;
            if (excess > 0d) {
                upsideExcess += excess;
            } else if (excess < 0d) {
                downsideShortfall += -excess;
            }
        }
        if (downsideShortfall == 0d) {
            return upsideExcess == 0d ? 0d : Double.NaN;
        }
        return upsideExcess / downsideShortfall;
    }
}
