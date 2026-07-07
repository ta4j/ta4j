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
 * Portfolio state at one aligned bar after any scheduled rebalance has been
 * applied.
 *
 * @param index           aligned portfolio index
 * @param endTime         aligned bar end time
 * @param prices          close prices used for valuation
 * @param holdings        asset units held after any rebalance
 * @param cash            cash balance after any rebalance and transaction costs
 * @param portfolioValue  cash plus marked-to-market holdings
 * @param periodReturn    return since the previous snapshot, net of costs
 * @param transactionCost transaction costs paid at this snapshot
 * @param turnover        gross notional traded at this snapshot, excluding
 *                        costs
 * @since 0.22.9
 */
public record PortfolioSnapshot(int index, Instant endTime, Map<PortfolioAsset, Num> prices,
        Map<PortfolioAsset, Num> holdings, Num cash, Num portfolioValue, Num periodReturn, Num transactionCost,
        Num turnover) {

    /**
     * Creates a portfolio snapshot.
     *
     * @since 0.22.9
     */
    public PortfolioSnapshot {
        Objects.requireNonNull(endTime, "endTime");
        prices = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(prices, "prices")));
        holdings = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(holdings, "holdings")));
        Objects.requireNonNull(cash, "cash");
        Objects.requireNonNull(portfolioValue, "portfolioValue");
        Objects.requireNonNull(periodReturn, "periodReturn");
        Objects.requireNonNull(transactionCost, "transactionCost");
        Objects.requireNonNull(turnover, "turnover");
    }

    /**
     * Returns marked-to-market value for one asset.
     *
     * @param asset asset id
     * @return asset value
     * @since 0.22.9
     */
    public Num assetValue(PortfolioAsset asset) {
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
     * @param asset asset id
     * @return actual asset weight, or zero when portfolio value is zero
     * @since 0.22.9
     */
    public Num assetWeight(PortfolioAsset asset) {
        if (portfolioValue.isZero()) {
            return portfolioValue.getNumFactory().zero();
        }
        return assetValue(asset).dividedBy(portfolioValue);
    }
}
