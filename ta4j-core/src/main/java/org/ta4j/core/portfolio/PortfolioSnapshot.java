/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.num.Num;

/**
 * Immutable portfolio state at one aligned bar after any scheduled rebalance.
 *
 * @since 0.23.1
 */
public final class PortfolioSnapshot {

    private final int index;
    private final Instant endTime;
    private final Map<String, Num> prices;
    private final Map<String, Num> holdings;
    private final Num cash;
    private final Num portfolioValue;
    private final Num periodReturn;
    private final Num transactionCost;
    private final Num turnover;

    PortfolioSnapshot(int index, Instant endTime, Map<String, Num> prices, Map<String, Num> holdings, Num cash,
            Num portfolioValue, Num periodReturn, Num transactionCost, Num turnover) {
        this.index = index;
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        this.prices = immutableMap(prices, "prices");
        this.holdings = immutableMap(holdings, "holdings");
        this.cash = Objects.requireNonNull(cash, "cash");
        this.portfolioValue = Objects.requireNonNull(portfolioValue, "portfolioValue");
        this.periodReturn = Objects.requireNonNull(periodReturn, "periodReturn");
        this.transactionCost = Objects.requireNonNull(transactionCost, "transactionCost");
        this.turnover = Objects.requireNonNull(turnover, "turnover");
    }

    /**
     * @return aligned portfolio index
     * @since 0.23.1
     */
    public int getIndex() {
        return index;
    }

    /**
     * @return aligned bar end time
     * @since 0.23.1
     */
    public Instant getEndTime() {
        return endTime;
    }

    /**
     * @return close prices used for valuation
     * @since 0.23.1
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "prices is an unmodifiable defensive copy")
    public Map<String, Num> getPrices() {
        return prices;
    }

    /**
     * @return asset units held after any rebalance
     * @since 0.23.1
     */
    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "holdings is an unmodifiable defensive copy")
    public Map<String, Num> getHoldings() {
        return holdings;
    }

    /**
     * @return cash after any rebalance and transaction costs
     * @since 0.23.1
     */
    public Num getCash() {
        return cash;
    }

    /**
     * @return cash plus marked-to-market holdings
     * @since 0.23.1
     */
    public Num getPortfolioValue() {
        return portfolioValue;
    }

    /**
     * @return return since the previous snapshot, net of costs
     * @since 0.23.1
     */
    public Num getPeriodReturn() {
        return periodReturn;
    }

    /**
     * @return transaction costs paid at this snapshot
     * @since 0.23.1
     */
    public Num getTransactionCost() {
        return transactionCost;
    }

    /**
     * @return gross notional traded at this snapshot, excluding costs
     * @since 0.23.1
     */
    public Num getTurnover() {
        return turnover;
    }

    /**
     * Returns marked-to-market value for one asset.
     *
     * @param asset asset name
     * @return asset value
     * @since 0.23.1
     */
    public Num getAssetValue(String asset) {
        Objects.requireNonNull(asset, "asset");
        Num price = prices.get(asset);
        Num holding = holdings.get(asset);
        if (price == null || holding == null) {
            return portfolioValue.getNumFactory().zero();
        }
        return price.multipliedBy(holding);
    }

    /**
     * Returns actual marked-to-market asset weight for one asset.
     *
     * @param asset asset name
     * @return actual asset weight, or zero when portfolio value is zero
     * @since 0.23.1
     */
    public Num getAssetWeight(String asset) {
        if (portfolioValue.isZero()) {
            return portfolioValue.getNumFactory().zero();
        }
        return getAssetValue(asset).dividedBy(portfolioValue);
    }

    private static Map<String, Num> immutableMap(Map<String, Num> values, String name) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(values, name)));
    }
}
