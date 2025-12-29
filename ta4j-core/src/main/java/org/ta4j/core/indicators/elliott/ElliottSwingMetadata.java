/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.elliott;

import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Immutable snapshot of swing statistics helpful for Elliott wave validation.
 *
 * @since 0.22.0
 */
public final class ElliottSwingMetadata {

    private final List<ElliottSwing> swings;
    private final boolean valid;
    private final Num highestPrice;
    private final Num lowestPrice;

    private ElliottSwingMetadata(final List<ElliottSwing> swings, final boolean valid, final Num highestPrice,
            final Num lowestPrice) {
        this.swings = swings;
        this.valid = valid;
        this.highestPrice = highestPrice;
        this.lowestPrice = lowestPrice;
    }

    /**
     * Builds metadata around the provided swing history.
     *
     * @param swings     ordered swing list produced by
     *                   {@link ElliottSwingIndicator}
     * @param numFactory number factory backing the swings
     * @return immutable metadata view
     * @since 0.22.0
     */
    public static ElliottSwingMetadata of(final List<ElliottSwing> swings, final NumFactory numFactory) {
        Objects.requireNonNull(numFactory, "numFactory");
        if (swings == null || swings.isEmpty()) {
            return new ElliottSwingMetadata(List.of(), true, numFactory.zero(), numFactory.zero());
        }
        boolean valid = true;
        Num highest = null;
        Num lowest = null;
        for (final ElliottSwing swing : swings) {
            if (swing == null) {
                valid = false;
                break;
            }
            final Num from = swing.fromPrice();
            final Num to = swing.toPrice();
            if (!Num.isValid(from) || !Num.isValid(to)) {
                valid = false;
                break;
            }
            final Num swingHigh = from.max(to);
            final Num swingLow = from.min(to);
            highest = highest == null ? swingHigh : highest.max(swingHigh);
            lowest = lowest == null ? swingLow : lowest.min(swingLow);
        }
        final List<ElliottSwing> copy = valid ? List.copyOf(swings) : List.of();
        if (!valid) {
            highest = numFactory.zero();
            lowest = numFactory.zero();
        }
        return new ElliottSwingMetadata(copy, valid, highest, lowest);
    }

    /**
     * @return whether the snapshot contains valid prices (not null and not NaN) for
     *         each swing
     * @since 0.22.0
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * @return number of swings represented
     * @since 0.22.0
     */
    public int size() {
        return swings.size();
    }

    /**
     * @return {@code true} when no swings are present
     * @since 0.22.0
     */
    public boolean isEmpty() {
        return swings.isEmpty();
    }

    /**
     * @return highest price touched by any swing or {@code zero()} when invalid
     * @since 0.22.0
     */
    public Num highestPrice() {
        return highestPrice;
    }

    /**
     * @return lowest price touched by any swing or {@code zero()} when invalid
     * @since 0.22.0
     */
    public Num lowestPrice() {
        return lowestPrice;
    }

    /**
     * @param length requested prefix length
     * @return immutable leading portion of the swing list (trimmed to available
     *         size)
     * @since 0.22.0
     */
    public List<ElliottSwing> leading(final int length) {
        if (swings.isEmpty() || length <= 0) {
            return emptyList();
        }
        final int toIndex = Math.min(length, swings.size());
        return List.copyOf(swings.subList(0, toIndex));
    }

    /**
     * @param length requested suffix length
     * @return immutable trailing portion of the swing list (trimmed to available
     *         size)
     * @since 0.22.0
     */
    public List<ElliottSwing> trailing(final int length) {
        if (swings.isEmpty() || length <= 0) {
            return emptyList();
        }
        final int fromIndex = Math.max(0, swings.size() - length);
        return List.copyOf(swings.subList(fromIndex, swings.size()));
    }

    /**
     * @param fromIndex inclusive starting index
     * @param toIndex   exclusive ending index
     * @return immutable sub-list bounded to the available range
     * @since 0.22.0
     */
    public List<ElliottSwing> subList(final int fromIndex, final int toIndex) {
        if (swings.isEmpty()) {
            return emptyList();
        }
        final int safeFrom = Math.max(0, Math.min(fromIndex, swings.size()));
        final int safeTo = Math.max(safeFrom, Math.min(toIndex, swings.size()));
        if (safeFrom == safeTo) {
            return emptyList();
        }
        return List.copyOf(swings.subList(safeFrom, safeTo));
    }

    /**
     * @param index index within the swing list
     * @return swing at the requested index
     * @since 0.22.0
     */
    public ElliottSwing swing(final int index) {
        return swings.get(index);
    }

    /**
     * @return immutable copy of all swings
     * @since 0.22.0
     */
    public List<ElliottSwing> swings() {
        return List.copyOf(swings);
    }
}
