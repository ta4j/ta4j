/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.acceleration;

import org.ta4j.core.acceleration.AccelerationRuntime.KernelRequest;

/**
 * Test-only view of {@link AccelerationPlan}'s package-private state for tests
 * of plans built outside this package.
 */
public final class AccelerationPlans {

    private AccelerationPlans() {
    }

    public static boolean isPlanned(AccelerationPlan<?> plan) {
        return plan.isPlanned();
    }

    public static boolean isPermanentDecline(AccelerationPlan<?> plan) {
        return plan.isPermanentDecline();
    }

    public static KernelRequest request(AccelerationPlan<?> plan) {
        return plan.request();
    }

    public static <T> AccelerationPlan.Decoder<T> decoder(AccelerationPlan<T> plan) {
        return plan.decoder();
    }

    public static int retryFromIndex(AccelerationPlan<?> plan) {
        return plan.retryFromIndex();
    }

    public static String reason(AccelerationPlan<?> plan) {
        return plan.reason();
    }
}
