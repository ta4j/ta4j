/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.util.List;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Validates and resolves an amount expressed in one currency against the
 * settlement currency of the value that owns it.
 *
 * <p>
 * Currency codes are compared literally: two distinct codes are never presumed
 * to be interchangeable, so a cross-currency amount requires an explicit
 * converted value. No foreign-exchange rate is inferred here.
 * </p>
 *
 * <p>
 * The sign convention of the owning value is preserved: an amount and its
 * converted value must not carry opposing signs, while an explicitly rounded
 * zero conversion of non-zero dust is allowed.
 * </p>
 *
 * @since 0.25.1
 */
final class SettlementAmountSupport {

    private SettlementAmountSupport() {
    }

    /**
     * Resolves {@code amount}, expressed in {@code currency}, into
     * {@code settlementCurrency}.
     *
     * @param amount             amount in {@code currency}, never {@code null}
     * @param currency           currency of {@code amount}, never blank
     * @param settlementAmount   optional explicit conversion into
     *                           {@code settlementCurrency}
     * @param settlementCurrency settlement currency of the owning value, never
     *                           blank
     * @return the resolved amount in {@code settlementCurrency}
     * @throws IllegalArgumentException when the conversion is missing, differing or
     *                                  sign-opposed
     * @since 0.25.1
     */
    static Num resolve(Num amount, String currency, Num settlementAmount, String settlementCurrency) {
        FuturesValidation.requireFinite(amount, "amount");
        FuturesValidation.requireNonBlank(currency, "currency");
        FuturesValidation.requireNonBlank(settlementCurrency, "settlementCurrency");
        if (settlementAmount != null) {
            FuturesValidation.requireFinite(settlementAmount, "settlementAmount");
        }
        if (currency.equals(settlementCurrency)) {
            if (settlementAmount == null) {
                return amount;
            }
            if (!FuturesValidation.numEquals(settlementAmount, amount)) {
                throw new IllegalArgumentException(
                        "settlementAmount must equal amount when both are expressed in " + settlementCurrency);
            }
            return settlementAmount;
        }
        if (settlementAmount == null) {
            throw new IllegalArgumentException("settlementAmount is required to convert " + currency + " into "
                    + settlementCurrency + ": currency codes are never presumed equal");
        }
        if (amount.isZero()) {
            if (!settlementAmount.isZero()) {
                throw new IllegalArgumentException(
                        "settlementAmount must be zero when the original " + currency + " amount is zero");
            }
            return settlementAmount;
        }
        if (!amount.isZero() && amount.isPositive() != settlementAmount.isPositive() && !settlementAmount.isZero()) {
            throw new IllegalArgumentException("settlementAmount must not oppose the sign of the original amount");
        }
        return settlementAmount;
    }

    /**
     * Sums the resolved settlement amounts of already-bound {@link TradeFee}
     * components using one {@link NumFactory}.
     *
     * @param fees       components with a resolved settlement amount, or
     *                   {@code null} when no fees have been recorded yet
     * @param numFactory factory of the resulting sum
     * @return the total charge in the settlement currency
     * @since 0.25.1
     */
    static Num sumSettlementAmounts(List<TradeFee> fees, NumFactory numFactory) {
        Num sum = numFactory.zero();
        Num compensation = numFactory.zero();
        if (fees == null) {
            return sum;
        }
        for (TradeFee fee : fees) {
            Num settlementAmount = fee.settlementAmount();
            Num normalized = numFactory.numOf(settlementAmount.getDelegate());
            if (!Num.isFinite(normalized)) {
                throw new IllegalArgumentException(
                        "fee settlement amount must be finite and representable in fill number factory");
            }
            if (!settlementAmount.isZero() && normalized.isZero()) {
                throw new IllegalArgumentException(
                        "fee settlement amount must be representable in fill number factory");
            }
            Num nextSum = sum.plus(normalized);
            if (!Num.isFinite(nextSum)) {
                throw new IllegalArgumentException("fee settlement total must be finite in fill number factory");
            }
            Num correction;
            if (sum.abs().isGreaterThanOrEqual(normalized.abs())) {
                correction = sum.minus(nextSum).plus(normalized);
            } else {
                correction = normalized.minus(nextSum).plus(sum);
            }
            Num nextCompensation = compensation.plus(correction);
            if (!Num.isFinite(nextCompensation)) {
                throw new IllegalArgumentException("fee settlement total must be finite in fill number factory");
            }
            sum = nextSum;
            compensation = nextCompensation;
        }
        Num total = sum.plus(compensation);
        if (!Num.isFinite(total)) {
            throw new IllegalArgumentException("fee settlement total must be finite in fill number factory");
        }
        return total;
    }
}
