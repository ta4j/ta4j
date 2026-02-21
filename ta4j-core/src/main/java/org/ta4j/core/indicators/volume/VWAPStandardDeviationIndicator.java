/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.volume;

import org.ta4j.core.num.Num;
import org.ta4j.core.serialization.ComponentDescriptor;
import org.ta4j.core.serialization.ComponentSerialization;

/**
 * Volume-weighted standard deviation of price within a VWAP window.
 * <p>
 * This indicator is useful for building VWAP bands that highlight value areas
 * and extensions away from the anchored or rolling VWAP. Uses the same VWAP
 * window/anchor definition as the supplied reference indicator.
 *
 * @since 0.19
 */
public class VWAPStandardDeviationIndicator extends AbstractVWAPIndicator {

    private final AbstractVWAPIndicator reference;

    /**
     * Constructor.
     *
     * @param reference the VWAP indicator sharing the same price, volume and window
     *                  definition
     *
     * @since 0.19
     */
    public VWAPStandardDeviationIndicator(AbstractVWAPIndicator reference) {
        super(reference.priceIndicator, reference.volumeIndicator);
        this.reference = reference;
    }

    /**
     * Resolves window start index.
     */
    @Override
    protected int resolveWindowStartIndex(int index) {
        return reference.getWindowStartIndex(index);
    }

    /**
     * Maps the intermediate VWAP values to the exposed indicator value.
     */
    @Override
    protected Num map(VWAPValues values) {
        Num mean = values.mean();
        Num expectedSquare = values.weightedSquareMean();
        Num variance = expectedSquare.minus(mean.multipliedBy(mean));
        if (variance.isNegative()) {
            variance = values.getFactory().zero();
        }
        return variance.sqrt();
    }

    /**
     * Returns the number of unstable bars required before values become reliable.
     */
    @Override
    public int getCountOfUnstableBars() {
        return reference.getCountOfUnstableBars();
    }

    /**
     * Implements to descriptor.
     */
    @Override
    public ComponentDescriptor toDescriptor() {
        return ComponentDescriptor.builder()
                .withType(getClass().getSimpleName())
                .addComponent(reference.toDescriptor())
                .build();
    }

    /**
     * Returns the JSON representation of this component.
     */
    @Override
    public String toJson() {
        return ComponentSerialization.toJson(toDescriptor());
    }

    /**
     * Returns a string representation of this component.
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + " reference: " + reference;
    }
}
