/*
 * SPDX-License-Identifier: MIT
 */
/**
 * First-class multi-asset portfolio backtesting contracts.
 *
 * <p>
 * This package models the conservative V1 portfolio foundation: asset
 * identities, strict end-time aligned bar-series inputs, explicit static target
 * weights, simple rebalance policies, and a cost-aware portfolio execution
 * result. It intentionally does not include advanced allocation optimizers such
 * as Markowitz, HRP, entropy, or universal portfolios; those algorithms can
 * target these contracts after the accounting model is stable.
 * </p>
 *
 * @since 0.22.9
 */
package org.ta4j.core.portfolio;
