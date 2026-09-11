/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.backtest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import org.ta4j.core.FuturesContract;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Validates and rounds simulated futures order quantities against the
 * constraints declared by a {@link FuturesContract}.
 *
 * <p>
 * Only simulated orders are constrained here. Exchange-observed fills and
 * synthetic accounting slices keep the quantity they actually executed, so a
 * partial fill that is not a whole multiple of the quantity increment is never
 * rounded and never rejected.
 * </p>
 *
 * <p>
 * {@code priceIncrement} is deliberately left unused: it is native contract
 * metadata, not a matching rule, and no tick-rounding model is invented here.
 * </p>
 *
 * @since 0.25.1
 */
final class FuturesOrderQuantitySupport {

    /**
     * Relative tolerance applied when a quantity is compared with an increment
     * multiple: binary floating point quantities represent an exact multiple, such
     * as {@code 0.3 = 3 * 0.1}, as a neighbouring value.
     */
    private static final BigDecimal MULTIPLE_TOLERANCE = BigDecimal.valueOf(1E-9);

    private FuturesOrderQuantitySupport() {
    }

    /**
     * Rounds a quantity down to the nearest declared increment multiple.
     *
     * @param contract contract declaring the increment, never {@code null}
     * @param quantity nonnegative and finite quantity
     * @return the largest increment multiple not meaningfully above
     *         {@code quantity}
     * @since 0.25.1
     */
    static Num roundDown(FuturesContract contract, Num quantity) {
        Objects.requireNonNull(contract, "contract");
        requireNonNegativeFinite(quantity, "quantity");
        Num increment = toNum(contract.quantityIncrement(), quantity.getNumFactory());
        if (increment == null) {
            return quantity;
        }
        BigDecimal steps = quantity.bigDecimalValue().divide(increment.bigDecimalValue(), 0, RoundingMode.FLOOR);
        return increment.multipliedBy(quantity.getNumFactory().numOf(steps));
    }

    /**
     * Rounds a quantity down to the largest tradable quantity within all declared
     * quantity and notional maxima, or zero when none is tradable.
     *
     * @param contract contract declaring the constraints, never {@code null}
     * @param quantity nonnegative and finite upper bound
     * @param price    positive and finite execution quote
     * @return a tradable quantity not above {@code quantity}, or zero
     * @since 0.25.1
     */
    static Num largestTradable(FuturesContract contract, Num quantity, Num price) {
        Objects.requireNonNull(contract, "contract");
        requirePositiveFinite(price, "execution price");
        Num bounded = capToMaximumQuantity(contract, quantity);
        bounded = capToMaximumNotional(contract, bounded, price);
        Num rounded = roundDown(contract, bounded);
        if (!isTradable(contract, rounded, price)) {
            return quantity.getNumFactory().zero();
        }
        return rounded;
    }

    /**
     * Returns whether a quantity is tradable at an execution quote under every
     * declared constraint.
     *
     * @param contract contract declaring the constraints, may be {@code null} for a
     *                 spot simulation
     * @param quantity candidate order quantity in contracts
     * @param price    execution quote
     * @return {@code true} when the quantity satisfies every declared constraint
     * @since 0.25.1
     */
    static boolean isTradable(FuturesContract contract, Num quantity, Num price) {
        if (contract == null) {
            return true;
        }
        if (quantity == null || !isFinite(quantity) || !quantity.isPositive()) {
            return false;
        }
        if (price == null || !isFinite(price) || !price.isPositive()) {
            return false;
        }
        return violation(contract, quantity, price) == null;
    }

    /**
     * Requires a quantity to be tradable at an execution quote.
     *
     * @param contract contract declaring the constraints, may be {@code null} for a
     *                 spot simulation
     * @param quantity requested order quantity in contracts
     * @param price    selected execution quote
     * @throws IllegalArgumentException when the contract is present and the
     *                                  quantity or the quote violates a declared
     *                                  constraint
     * @since 0.25.1
     */
    static void requireTradable(FuturesContract contract, Num quantity, Num price) {
        if (contract == null) {
            return;
        }
        requirePositiveFinite(price, "execution price");
        requirePositiveFinite(quantity, "order quantity");
        String violation = violation(contract, quantity, price);
        if (violation != null) {
            throw new IllegalArgumentException(violation);
        }
    }

    private static String violation(FuturesContract contract, Num quantity, Num price) {
        NumFactory numFactory = quantity.getNumFactory();
        Num increment = toNum(contract.quantityIncrement(), numFactory);
        if (increment != null && !isMultipleOf(quantity, increment)) {
            return "order quantity " + quantity + " must be a multiple of the quantity increment " + increment;
        }
        Num minimumQuantity = toNum(contract.minimumQuantity(), numFactory);
        if (minimumQuantity != null && quantity.isLessThan(minimumQuantity)) {
            return "order quantity " + quantity + " is below the minimum quantity " + minimumQuantity;
        }
        Num maximumQuantity = toNum(contract.maximumQuantity(), quantity.getNumFactory());
        if (maximumQuantity != null && quantity.isGreaterThan(maximumQuantity)) {
            return "order quantity " + quantity + " is above the maximum quantity " + maximumQuantity;
        }
        Num quoteNotional = toNum(contract.quoteNotional(quantity, price), quantity.getNumFactory());
        Num minimumNotional = toNum(contract.minimumNotional(), quantity.getNumFactory());
        if (minimumNotional != null && quoteNotional.isLessThan(minimumNotional)) {
            return "order notional " + quoteNotional + " is below the minimum notional " + minimumNotional;
        }
        Num maximumNotional = toNum(contract.maximumNotional(), quantity.getNumFactory());
        if (maximumNotional != null && quoteNotional.isGreaterThan(maximumNotional)) {
            return "order notional " + quoteNotional + " is above the maximum notional " + maximumNotional;
        }
        return null;
    }

    private static Num capToMaximumQuantity(FuturesContract contract, Num quantity) {
        Num maximumQuantity = toNum(contract.maximumQuantity(), quantity.getNumFactory());
        if (maximumQuantity == null || quantity.isLessThanOrEqual(maximumQuantity)) {
            return quantity;
        }
        return maximumQuantity;
    }

    private static Num capToMaximumNotional(FuturesContract contract, Num quantity, Num price) {
        Num maximumNotional = toNum(contract.maximumNotional(), quantity.getNumFactory());
        if (maximumNotional == null) {
            return quantity;
        }
        Num perContract = toNum(contract.quoteNotional(quantity.getNumFactory().one(), price),
                quantity.getNumFactory());
        if (!perContract.isPositive()) {
            return quantity;
        }
        Num notionalBound = quantity.getNumFactory().numOf(maximumNotional.getDelegate()).dividedBy(perContract);
        if (notionalBound.isNaN() || notionalBound.isNegativeOrZero() || quantity.isLessThanOrEqual(notionalBound)) {
            return quantity;
        }
        return notionalBound;
    }

    static Num toNum(Num value, NumFactory numFactory) {
        return value == null ? null : numFactory.numOf(value.getDelegate());
    }

    private static boolean isMultipleOf(Num quantity, Num increment) {
        requirePositiveFinite(increment, "quantityIncrement");
        BigDecimal divisor = increment.bigDecimalValue();
        BigDecimal remainder = quantity.bigDecimalValue().remainder(divisor).abs();
        BigDecimal tolerance = divisor.abs().multiply(MULTIPLE_TOLERANCE);
        return remainder.compareTo(tolerance) <= 0 || divisor.subtract(remainder).compareTo(tolerance) <= 0;
    }

    private static boolean isFinite(Num value) {
        return value != null && !value.isNaN() && value.bigDecimalValue() != null;
    }

    private static void requirePositiveFinite(Num value, String name) {
        Objects.requireNonNull(value, name);
        if (!isFinite(value) || !value.isPositive()) {
            throw new IllegalArgumentException(name + " must be positive and finite");
        }
    }

    private static void requireNonNegativeFinite(Num value, String name) {
        Objects.requireNonNull(value, name);
        if (!isFinite(value) || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be nonnegative and finite");
        }
    }
}
