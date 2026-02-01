/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.LiveTrade;
import org.ta4j.core.LiveTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An indicator that visualizes an {@link AnalysisCriterion} over time by
 * calculating the criterion value at each bar index using a partial trading
 * record.
 *
 * <p>
 * For each bar index, this indicator creates a partial trading record
 * containing only positions that have been entered (and optionally closed) up
 * to that index, then calculates the criterion value for that partial record.
 * This allows analysis criteria to be visualized as time series on charts.
 * </p>
 *
 * @since 0.19
 */
public class AnalysisCriterionIndicator extends CachedIndicator<Num> {

    private final AnalysisCriterion criterion;
    private final TradingRecord fullTradingRecord;
    private final List<Trade> allTrades;
    private final String label;

    /**
     * Constructs an AnalysisCriterionIndicator.
     *
     * @param series        the bar series
     * @param criterion     the analysis criterion to visualize
     * @param tradingRecord the full trading record
     * @throws NullPointerException if any parameter is null
     */
    public AnalysisCriterionIndicator(BarSeries series, AnalysisCriterion criterion, TradingRecord tradingRecord) {
        super(series);
        this.criterion = Objects.requireNonNull(criterion, "Criterion cannot be null");
        this.fullTradingRecord = Objects.requireNonNull(tradingRecord, "Trading record cannot be null");
        this.allTrades = new ArrayList<>(tradingRecord.getTrades());
        this.label = deriveLabel(criterion);
    }

    @Override
    protected Num calculate(int index) {
        // Create a partial trading record with trades up to this index
        TradingRecord partialRecord = createPartialTradingRecord(index);

        // Calculate criterion value for the partial record
        return criterion.calculate(getBarSeries(), partialRecord);
    }

    @Override
    public int getCountOfUnstableBars() {
        // Analysis criteria don't have unstable bars - they calculate from trading
        // records
        return 0;
    }

    /**
     * Creates a partial trading record containing only trades that have occurred up
     * to the specified index.
     *
     * @param upToIndex the maximum bar index to include
     * @return a partial trading record
     */
    private TradingRecord createPartialTradingRecord(int upToIndex) {
        if (fullTradingRecord instanceof LiveTradingRecord liveRecord) {
            return createPartialLiveTradingRecord(liveRecord, upToIndex);
        }
        // Filter trades where trade index <= upToIndex
        List<Trade> partialTrades = new ArrayList<>();
        for (Trade trade : allTrades) {
            if (trade.getIndex() <= upToIndex) {
                partialTrades.add(trade);
            }
        }

        // Create new trading record from filtered trades
        if (partialTrades.isEmpty()) {
            // Return empty record with same cost models and starting type
            return new BaseTradingRecord(fullTradingRecord.getStartingType(),
                    fullTradingRecord.getTransactionCostModel(), fullTradingRecord.getHoldingCostModel());
        }

        Trade[] tradesArray = partialTrades.toArray(new Trade[0]);
        return new BaseTradingRecord(fullTradingRecord.getTransactionCostModel(),
                fullTradingRecord.getHoldingCostModel(), tradesArray);
    }

    private TradingRecord createPartialLiveTradingRecord(LiveTradingRecord liveRecord, int upToIndex) {
        LiveTradingRecord partialRecord = new LiveTradingRecord(liveRecord.getStartingType(),
                liveRecord.getMatchPolicy(), liveRecord.getTransactionCostModel(), liveRecord.getHoldingCostModel(),
                liveRecord.getStartIndex(), liveRecord.getEndIndex());
        partialRecord.setName(liveRecord.getName());
        for (Trade trade : allTrades) {
            if (trade.getIndex() <= upToIndex) {
                if (trade instanceof LiveTrade liveTrade) {
                    partialRecord.recordFill(trade.getIndex(), liveTrade);
                } else {
                    throw new IllegalArgumentException("LiveTradingRecord must provide LiveTrade trades");
                }
            }
        }
        return partialRecord;
    }

    @Override
    public String toString() {
        return label;
    }

    private static String deriveLabel(AnalysisCriterion criterion) {
        String simpleName = criterion.getClass().getSimpleName();
        if (simpleName.endsWith("Criterion") && simpleName.length() > "Criterion".length()) {
            simpleName = simpleName.substring(0, simpleName.length() - "Criterion".length());
        }
        return simpleName.isEmpty() ? criterion.getClass().getSimpleName() : simpleName;
    }
}
