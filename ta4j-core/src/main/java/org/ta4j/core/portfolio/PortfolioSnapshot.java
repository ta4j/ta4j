/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.ta4j.core.num.Num;

/**
 * Immutable portfolio state at one aligned bar, after any rebalance at that
 * bar's close.
 *
 * <p>
 * Returns and weights are fractions ({@code 0.05} means 5%). Holdings are
 * fractional, long-only units valued at the aligned close price.
 * </p>
 *
 * @since 0.25.1
 */
public final class PortfolioSnapshot {

    /**
     * Outcome of the rebalance decision at one aligned bar.
     *
     * @since 0.25.1
     */
    public enum RebalanceStatus {

        /** The rebalance policy did not select this bar; no trades were made. */
        NOT_SCHEDULED,

        /**
         * Every asset and the cash balance ended at its target weight of the
         * post-cost portfolio value (possibly without trading when the holdings were
         * already there).
         */
        COMPLETED,

        /**
         * Trades were made, but transaction costs or available cash prevented
         * reaching every target; the achieved weights differ from the targets.
         */
        PARTIAL,

        /**
         * The targets could not be reached after transaction costs, so no trades
         * were made and the holdings are unchanged (for example when a fixed fee
         * exceeds what the rebalance could achieve).
         */
        SKIPPED
    }

    private final int index;
    private final Instant endTime;
    private final Map<String, Num> prices;
    private final Map<String, Num> holdings;
    private final Num cash;
    private final Num portfolioValue;
    private final Num periodReturn;
    private final Num transactionCost;
    private final Num tradedNotional;
    private final Num turnover;
    private final RebalanceStatus rebalanceStatus;

    /**
     * Takes ownership of {@code prices} and {@code holdings}: callers pass maps
     * they never modify afterwards, which lets consecutive snapshots share one
     * holdings map between rebalances.
     */
    PortfolioSnapshot(int index, Instant endTime, Map<String, Num> prices, Map<String, Num> holdings, Num cash,
            Num portfolioValue, Num periodReturn, Num transactionCost, Num tradedNotional, Num turnover,
            RebalanceStatus rebalanceStatus) {
        this.index = index;
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        this.prices = Collections.unmodifiableMap(Objects.requireNonNull(prices, "prices"));
        this.holdings = Collections.unmodifiableMap(Objects.requireNonNull(holdings, "holdings"));
        this.cash = Objects.requireNonNull(cash, "cash");
        this.portfolioValue = Objects.requireNonNull(portfolioValue, "portfolioValue");
        this.periodReturn = Objects.requireNonNull(periodReturn, "periodReturn");
        this.transactionCost = Objects.requireNonNull(transactionCost, "transactionCost");
        this.tradedNotional = Objects.requireNonNull(tradedNotional, "tradedNotional");
        this.turnover = Objects.requireNonNull(turnover, "turnover");
        this.rebalanceStatus = Objects.requireNonNull(rebalanceStatus, "rebalanceStatus");
    }

    /**
     * @return aligned portfolio index
     * @since 0.25.1
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return aligned bar end time
     * @since 0.25.1
     */
    public Instant getEndTime() {
        return endTime;
    }

    /**
     * @return close prices used for trading and valuation, in portfolio order
     * @since 0.25.1
     */
    public Map<String, Num> getPrices() {
        return prices;
    }

    /**
     * @return asset units held after any rebalance, in portfolio order
     * @since 0.25.1
     */
    public Map<String, Num> getHoldings() {
        return holdings;
    }

    /**
     * @return cash after any rebalance and transaction costs
     * @since 0.25.1
     */
    public Num getCash() {
        return cash;
    }

    /**
     * @return cash plus holdings marked to the close price
     * @since 0.25.1
     */
    public Num getPortfolioValue() {
        return portfolioValue;
    }

    /**
     * @return fractional return since the previous snapshot (or since the initial
     *         cash for the first snapshot), net of transaction costs
     * @since 0.25.1
     */
    public Num getPeriodReturn() {
        return periodReturn;
    }

    /**
     * @return transaction costs paid at this bar
     * @since 0.25.1
     */
    public Num getTransactionCost() {
        return transactionCost;
    }

    /**
     * @return gross notional traded at this bar (buys plus sells, excluding
     *         costs), in currency units
     * @since 0.25.1
     */
    public Num getTradedNotional() {
        return tradedNotional;
    }

    /**
     * Returns the two-sided turnover ratio: {@link #getTradedNotional()} divided
     * by the portfolio value before trading at this bar. The initial investment of
     * a fully invested allocation has a turnover of {@code 1}.
     *
     * @return turnover as a fraction of pre-trade portfolio value
     * @since 0.25.1
     */
    public Num getTurnover() {
        return turnover;
    }

    /**
     * @return outcome of the rebalance decision at this bar
     * @since 0.25.1
     */
    public RebalanceStatus getRebalanceStatus() {
        return rebalanceStatus;
    }

    /**
     * Returns the marked-to-market value of one asset.
     *
     * @param asset asset name
     * @return asset value
     * @throws IllegalArgumentException if the asset is not in the portfolio
     * @since 0.25.1
     */
    public Num getAssetValue(String asset) {
        Objects.requireNonNull(asset, "asset");
        Num holding = holdings.get(asset);
        if (holding == null) {
            throw new IllegalArgumentException("asset is not in this portfolio: " + asset);
        }
        return prices.get(asset).multipliedBy(holding);
    }

    /**
     * Returns the achieved weight of one asset.
     *
     * @param asset asset name
     * @return asset value as a fraction of portfolio value, or zero when the
     *         portfolio value is zero
     * @throws IllegalArgumentException if the asset is not in the portfolio
     * @since 0.25.1
     */
    public Num getAssetWeight(String asset) {
        Num assetValue = getAssetValue(asset);
        if (portfolioValue.isZero()) {
            return portfolioValue.getNumFactory().zero();
        }
        return assetValue.dividedBy(portfolioValue);
    }

    /**
     * @return achieved asset weights in portfolio order; compare with
     *         {@link PortfolioAllocation#getTargetWeights()}
     * @since 0.25.1
     */
    public Map<String, Num> getAssetWeights() {
        Map<String, Num> weights = new LinkedHashMap<>();
        for (String asset : holdings.keySet()) {
            weights.put(asset, getAssetWeight(asset));
        }
        return Collections.unmodifiableMap(weights);
    }

    /**
     * @return cash as a fraction of portfolio value, or zero when the portfolio
     *         value is zero
     * @since 0.25.1
     */
    public Num getCashWeight() {
        if (portfolioValue.isZero()) {
            return portfolioValue.getNumFactory().zero();
        }
        return cash.dividedBy(portfolioValue);
    }

    /**
     * @return compact one-line description of this snapshot
     */
    @Override
    public String toString() {
        return "PortfolioSnapshot{index=" + index + ", endTime=" + endTime + ", value=" + portfolioValue + ", cash="
                + cash + ", periodReturn=" + periodReturn + ", transactionCost=" + transactionCost
                + ", tradedNotional=" + tradedNotional + ", rebalance=" + rebalanceStatus + '}';
    }
}
