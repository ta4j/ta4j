/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

/**
 * Alignment band of {@link DynamicTimeWarpingDistanceIndicator}.
 *
 * <p>
 * The default form is a Sakoe–Chiba band: the optimal path may only visit cells
 * whose row/column indexes differ by at most {@code radius}. A radius of zero
 * restricts the path to the diagonal and makes the warped distance the plain
 * pointwise distance. Unconstrained warping is an explicit opt-in that costs
 * {@code O(W^2)} time instead of {@code O(W * min(W, 2r + 1))}.
 * </p>
 *
 * @param radius       the Sakoe–Chiba radius, {@code >= 0}; must be {@code 0}
 *                     when {@code unrestricted} is {@code true}
 * @param unrestricted {@code true} when every monotonic alignment is allowed
 * @since 0.24.1
 */
public record WarpingWindow(int radius, boolean unrestricted) {

    /**
     * Creates the bounded Sakoe–Chiba window.
     *
     * @param radius the band radius, {@code >= 0}
     * @return the bounded window
     * @throws IllegalArgumentException if {@code radius < 0}
     */
    public static WarpingWindow sakoeChiba(int radius) {
        return new WarpingWindow(radius, false);
    }

    /**
     * Creates the unconstrained window. Prefer a Sakoe–Chiba band unless the full
     * {@code O(W^2)} alignment is required.
     *
     * @return the unconstrained window
     */
    public static WarpingWindow unconstrained() {
        return new WarpingWindow(0, true);
    }

    /**
     * Validates the window.
     *
     * @throws IllegalArgumentException if {@code radius < 0}, or if
     *                                  {@code radius != 0} while
     *                                  {@code unrestricted} is {@code true}
     */
    public WarpingWindow {
        if (radius < 0 || (unrestricted && radius != 0)) {
            throw new IllegalArgumentException("radius must be >= 0 and must be 0 when the window is unconstrained");
        }
    }

    /**
     * @param firstIndex  row index
     * @param secondIndex column index
     * @return {@code true} when the cell lies inside the alignment band
     */
    public boolean inBand(int firstIndex, int secondIndex) {
        return unrestricted || Math.abs((long) firstIndex - secondIndex) <= radius;
    }
}
