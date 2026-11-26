/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.math.MathContext;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.*;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FixedTransactionCostModel;
import org.ta4j.core.analysis.cost.RecordedTradeCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class OpenPositionCostBasisCriterionTest extends AbstractCriterionTest {

    public OpenPositionCostBasisCriterionTest(NumFactory numFactory) {
        super(params -> new OpenPositionCostBasisCriterion(), numFactory);
    }

    @Test
    public void calculateUsesBaseTradingRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);

        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:00Z"), numFactory.hundred(), numFactory.one(),
                numFactory.numOf(0.1), ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(0, Instant.parse("2025-01-01T00:00:01Z"), numFactory.numOf(110), numFactory.one(),
                numFactory.numOf(0.2), ExecutionSide.BUY, null, null));

        Num expected = numFactory.hundred()
                .plus(numFactory.numOf(110))
                .plus(numFactory.numOf(0.1))
                .plus(numFactory.numOf(0.2));
        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result, 1e-12);
    }

    @Test
    public void calculateUsesCurrentPositionForStandardRecord() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var costModel = new FixedTransactionCostModel(1.5);
        var record = new BaseTradingRecord(TradeType.BUY, costModel, new ZeroCostModel());

        record.enter(0, series.getBar(0).getClosePrice(), numFactory.one());

        Num expected = series.getBar(0)
                .getClosePrice()
                .multipliedBy(numFactory.one())
                .plus(costModel.calculate(series.getBar(0).getClosePrice(), numFactory.one()));

        var result = getCriterion().calculate(series, record);

        assertNumEquals(expected, result);
    }

    @Test
    public void returnsZeroWhenNoOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110).build();
        var record = new BaseTradingRecord(TradeType.BUY, new ZeroCostModel(), new ZeroCostModel());

        var result = getCriterion().calculate(series, record);

        assertNumEquals(numFactory.zero(), result);
    }

    @Test
    public void betterThanPrefersLowerCostBasis() {
        var criterion = getCriterion();

        assertTrue(criterion.betterThan(numFactory.one(), numFactory.two()));
        assertFalse(criterion.betterThan(numFactory.two(), numFactory.one()));
    }

    @Test
    public void includesClosedLotsActiveAtLogicalEnd() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = multiLotRecord(false);

        assertNumEquals(numFactory.numOf(210), getCriterion().calculate(series, record));
    }

    @Test
    public void ignoresCurrentLotOpenedAfterLogicalEnd() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = multiLotRecord(true);

        assertNumEquals(numFactory.numOf(210), getCriterion().calculate(series, record));
    }

    @Test
    public void treatsPositionExitedAfterSeriesEndAsOpen() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = multiLotRecord(false);

        assertNumEquals(numFactory.hundred(), getCriterion().calculate(series, record.getPositions().getFirst()));
    }

    @Test
    public void excludesOpenLotEnteredAfterLogicalEndFromMixedOpenLots() {
        BarSeries series = multiLotSeries();
        BaseTradingRecord record = openLotsAcrossLogicalEnd();

        assertTrue(record.getPositions().isEmpty());
        assertNumEquals(numFactory.hundred(), getCriterion().calculate(series, record));
    }

    @Test
    public void aggregateEntrySpotRideAlongIncludesOnlyFillsAtLogicalEnd() {
        var earlySeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100).build();
        var fullSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 200)
                .build();
        var entry = Trade.fromFills(TradeType.BUY, List.of(new TradeFill(0, numFactory.hundred(), numFactory.one()),
                new TradeFill(5, numFactory.numOf(200), numFactory.one())), RecordedTradeCostModel.INSTANCE);
        var position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        var criterion = getCriterion();
        var earlySeriesCriteriaResult = criterion.calculate(earlySeries, position);
        var fullSeriesCriteriaResult = criterion.calculate(fullSeries, position);

        assertNumEquals(numFactory.hundred(), earlySeriesCriteriaResult);
        assertNumEquals(numFactory.numOf(300), fullSeriesCriteriaResult);
    }

    @Test
    public void fullWindowModeledFeeFinitePrecisionUsesScalarPath() {
        var costModel = new FixedTransactionCostModel(1.0);
        var entry = Trade.fromFills(TradeType.BUY, List.of(new TradeFill(0, numFactory.hundred(), numFactory.one()),
                new TradeFill(5, numFactory.numOf(200), numFactory.one())), costModel);
        var position = new Position(entry, costModel, new ZeroCostModel());
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 200)
                .build();
        var criterion = getCriterion();

        Num result = criterion.calculate(series, position);

        assertNumEquals(numFactory.numOf(301), result);
    }

    @Test
    public void unlimitedPrecisionFullWindowUsesScalarPath() {
        NumFactory factory = DecimalNumFactory.getInstance(MathContext.UNLIMITED);
        var series = new MockBarSeriesBuilder().withNumFactory(factory).withData(100, 100, 100, 100, 100, 200).build();
        var costModel = new FixedTransactionCostModel(1.0);
        var entry = Trade.fromFills(TradeType.BUY, List.of(new TradeFill(0, factory.hundred(), factory.one()),
                new TradeFill(5, factory.numOf(200), factory.one())), costModel);
        var position = new Position(entry, costModel, new ZeroCostModel());
        var criterion = getCriterion();

        Num result = criterion.calculate(series, position);

        assertNumEquals(factory.numOf(301), result);
    }

    @Test
    public void unlimitedPrecisionPartialWindowUsesRecordedFees() {
        NumFactory factory = DecimalNumFactory.getInstance(MathContext.UNLIMITED);
        var costModel = new FixedTransactionCostModel(1.0);
        var entry = Trade.fromFills(TradeType.BUY, List.of(new TradeFill(0, factory.hundred(), factory.one()),
                new TradeFill(5, factory.numOf(200), factory.one())), costModel);
        var position = new Position(entry, costModel, new ZeroCostModel());
        var constrained = ConstrainedSeriesSupport.trailingConstrainedSeries("partial", factory,
                0, 100d, 100d, 100d, 100d, 100d, 200d);
        var criterion = getCriterion();

        Num result = criterion.calculate(constrained, position);

        assertNumEquals(factory.numOf(100), result);
    }

    @Test
    public void partialWindowUsesRecordedFeesOfVisibleFills() {
        var constrained = ConstrainedSeriesSupport.trailingConstrainedSeries("partial", numFactory,
                0, 100d, 100d, 100d, 100d, 100d, 200d);
        var entry = Trade.fromFills(TradeType.BUY, List.of(
                        new TradeFill(0, Instant.EPOCH, numFactory.hundred(), numFactory.one(), numFactory.one(),
                                ExecutionSide.BUY, null, null),
                        new TradeFill(5, Instant.EPOCH, numFactory.numOf(200), numFactory.one(), numFactory.numOf(2),
                                ExecutionSide.BUY, null, null)),
                RecordedTradeCostModel.INSTANCE);
        var position = new Position(entry, RecordedTradeCostModel.INSTANCE, new ZeroCostModel());
        var criterion = getCriterion();
        Num result = criterion.calculate(constrained, position);

        assertNumEquals(numFactory.numOf(101), result);
    }

    @Test
    public void partialWindowWithModeledCostUsesRecordedFillFees() {
        var costModel = new ZeroCostModel();
        var entry = Trade.fromFills(TradeType.BUY, List.of(
                        new TradeFill(0, Instant.EPOCH, numFactory.hundred(), numFactory.one(), numFactory.one(),
                                ExecutionSide.BUY, null, null),
                        new TradeFill(5, Instant.EPOCH, numFactory.numOf(200), numFactory.one(), numFactory.numOf(2),
                                ExecutionSide.BUY, null, null)),
                costModel);
        var position = new Position(entry, costModel, new ZeroCostModel());
        var constrained = ConstrainedSeriesSupport.trailingConstrainedSeries("partial", numFactory,
                0, 100d, 100d, 100d, 100d, 100d, 200d);
        var criterion = getCriterion();
        Num result = criterion.calculate(constrained, position);

        assertNumEquals(numFactory.numOf(101), result);
    }

    @Test
    public void legacyEmptyFillListUsesScalarBasis() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 200)
                .build();
        Trade legacyTrade = new Trade() {
            public TradeType getType() {
                return TradeType.BUY;
            }

            public int getIndex() {
                return 0;
            }

            public Num getPricePerAsset() {
                return numFactory.hundred();
            }

            public Num getNetPrice() {
                return numFactory.hundred();
            }

            public Num getAmount() {
                return numFactory.one();
            }

            public Num getCost() {
                return numFactory.zero();
            }

            public CostModel getCostModel() {
                return new ZeroCostModel();
            }

            public List<TradeFill> getFills() {
                return List.of();
            }
        };
        Position position = new Position(legacyTrade, new ZeroCostModel(), new ZeroCostModel());
        var criterion = getCriterion();
        Num result = criterion.calculate(series, position);

        assertNumEquals(numFactory.hundred(), result);
    }

    @Test
    public void partialWindowModeledFeeIndependentOfFutureFill() {
        var costModel = new FixedTransactionCostModel(1.0);
        var entry1 = Trade.fromFills(TradeType.BUY, List.of(new TradeFill(0, numFactory.hundred(), numFactory.one()),
                new TradeFill(5, numFactory.numOf(200), numFactory.one())), costModel);
        var pos1 = new Position(entry1, costModel, new ZeroCostModel());
        var constrained1 = ConstrainedSeriesSupport.trailingConstrainedSeries("p1", numFactory,
                0, 100d, 100d, 100d, 100d, 100d, 200d);
        var criterion = getCriterion();

        Num r1 = criterion.calculate(constrained1, pos1);

        var entry2 = Trade.fromFills(TradeType.BUY, List.of(new TradeFill(0, numFactory.hundred(), numFactory.one()),
                new TradeFill(5, numFactory.numOf(300), numFactory.one())), costModel);
        var pos2 = new Position(entry2, costModel, new ZeroCostModel());
        var constrained2 = ConstrainedSeriesSupport.trailingConstrainedSeries("p2", numFactory,
                0, 100d, 100d, 100d, 100d, 100d, 300d);
        Num r2 = criterion.calculate(constrained2, pos2);

        assertNumEquals(numFactory.numOf(100), r1);
        assertNumEquals(numFactory.numOf(100), r2);
    }

    @Test
    public void partialWindowMixedPrecisionConvertsOnce() {
        NumFactory seriesFactory = DecimalNumFactory.getInstance(2);
        NumFactory fillFactory = DecimalNumFactory.getInstance(4);
        var costModel = new ZeroCostModel();
        var entry = Trade.fromFills(TradeType.BUY, List.of(
                        new TradeFill(0, fillFactory.numOf(1.24), fillFactory.one()),
                        new TradeFill(1, fillFactory.numOf(1.24), fillFactory.one()),
                        new TradeFill(5, fillFactory.numOf(1.24), fillFactory.one())),
                costModel);
        var position = new Position(entry, costModel, new ZeroCostModel());
        var constrained = ConstrainedSeriesSupport.trailingConstrainedSeries("partial", seriesFactory,
                1, 1.24, 1.24, 1.24, 1.24, 1.24, 1.24);
        var criterion = getCriterion();
        Num result = criterion.calculate(constrained, position);

        assertNumEquals(seriesFactory.numOf(2.5), result);
    }

    @Test
    public void partialWindowLowPrecisionFillsRetainSubtotals() {
        NumFactory fillFactory = DecimalNumFactory.getInstance(3);
        NumFactory seriesFactory = DecimalNumFactory.getInstance(4);
        var costModel = new ZeroCostModel();
        var entry = Trade.fromFills(TradeType.BUY, List.of(
                        new TradeFill(0, fillFactory.numOf(100), fillFactory.one()),
                        new TradeFill(1, fillFactory.numOf(0.4), fillFactory.one()),
                        new TradeFill(2, fillFactory.numOf(0.4), fillFactory.one()),
                        new TradeFill(5, fillFactory.numOf(1.0), fillFactory.one())),
                costModel);
        var position = new Position(entry, costModel, new ZeroCostModel());
        var constrained = ConstrainedSeriesSupport.trailingConstrainedSeries("partial", seriesFactory,
                2, 100d, 0.4, 0.4, 1.0, 1.0, 1.0);
        var criterion = getCriterion();
        Num result = criterion.calculate(constrained, position);

        assertNumEquals(seriesFactory.numOf(100.8), result);
    }

    private BaseTradingRecord openLotsAcrossLogicalEnd() {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(new BaseTrade(0, Instant.EPOCH, numFactory.hundred(), numFactory.one(), numFactory.zero(),
                ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(7, Instant.EPOCH.plusSeconds(7), numFactory.numOf(130), numFactory.one(),
                numFactory.zero(), ExecutionSide.BUY, null, null));
        return record;
    }

    private BarSeries multiLotSeries() {
        return ConstrainedSeriesSupport.trailingConstrainedSeries("cost-basis-multi-lot", numFactory, 5, 100d, 110d,
                110d, 110d, 110d, 120d, 120d, 120d, 130d, 130d, 130d, 130d);
    }

    private BaseTradingRecord multiLotRecord(boolean addLaterOpenLot) {
        BaseTradingRecord record = new BaseTradingRecord(TradeType.BUY, ExecutionMatchPolicy.FIFO, new ZeroCostModel(),
                new ZeroCostModel(), null, null);
        record.operate(new BaseTrade(0, Instant.EPOCH, numFactory.hundred(), numFactory.one(), numFactory.zero(),
                ExecutionSide.BUY, null, null));
        record.operate(new BaseTrade(1, Instant.EPOCH.plusSeconds(1), numFactory.numOf(110), numFactory.one(),
                numFactory.zero(), ExecutionSide.BUY, null, null));
        if (addLaterOpenLot) {
            record.operate(new BaseTrade(7, Instant.EPOCH.plusSeconds(7), numFactory.numOf(120), numFactory.one(),
                    numFactory.zero(), ExecutionSide.BUY, null, null));
        }
        record.operate(new BaseTrade(10, Instant.EPOCH.plusSeconds(10), numFactory.numOf(130), numFactory.one(),
                numFactory.zero(), ExecutionSide.SELL, null, null));
        record.operate(new BaseTrade(11, Instant.EPOCH.plusSeconds(11), numFactory.numOf(130), numFactory.one(),
                numFactory.zero(), ExecutionSide.SELL, null, null));
        return record;
    }
}
