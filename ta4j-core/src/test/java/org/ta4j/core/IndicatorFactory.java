/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core;

@FunctionalInterface
public interface IndicatorFactory<D, I> {

    /**
     * Applies parameters and data to an IndicatorFactory and returns the Indicator.
     *
     * @param data   source data for building the indicator
     * @param params indicator parameters
     * @return Indicator<I> with the indicator parameters applied
     */
    Indicator<I> getIndicator(D data, Object... params);

}
