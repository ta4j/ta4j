/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.elliott.confidence;

import java.util.List;
import java.util.Objects;

import org.ta4j.core.indicators.elliott.ElliottChannel;
import org.ta4j.core.indicators.elliott.ElliottFibonacciValidator;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.num.NumFactory;

/**
 * Context required to score confidence factors.
 *
 * @param swings     swing sequence
 * @param phase      current Elliott phase
 * @param channel    Elliott channel (nullable)
 * @param validator  Fibonacci validator
 * @param numFactory numeric factory for score creation
 * @since 0.22.2
 */
public record ElliottConfidenceContext(List<ElliottSwing> swings, ElliottPhase phase, ElliottChannel channel,
        ElliottFibonacciValidator validator, NumFactory numFactory) {

    public ElliottConfidenceContext {
        Objects.requireNonNull(swings, "swings");
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(validator, "validator");
        Objects.requireNonNull(numFactory, "numFactory");
    }
}
