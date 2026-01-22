/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 */
package org.ta4j.core.reports;

import org.ta4j.core.num.Num;

import java.util.Objects;

public interface PerformanceReport extends Comparable<PerformanceReport> {

    public Num getPerformanceMetric();

    default int compareTo(PerformanceReport o) {
        Objects.requireNonNull(o, "Cannot compare PerformanceReport instance to null");

        return this.getPerformanceMetric().compareTo(o.getPerformanceMetric());
    }
}
