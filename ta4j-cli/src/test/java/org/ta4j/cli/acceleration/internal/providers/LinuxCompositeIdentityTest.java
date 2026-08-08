/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.cli.acceleration.internal.providers;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * On Linux, {@code -Dta4j.acceleration=auto} selects a
 * {@link CompositeForecastAccelerationProvider} under the provider id
 * {@code "opencl"}. The service looks up and stores its failure quarantine
 * under the selection provider id, so the composite must advertise the same
 * identity; otherwise a natively failing member is attributed to and
 * quarantined under a different id ("cuda") that the service never consults,
 * and every request re-runs the failing native path.
 */
class LinuxCompositeIdentityTest {

    private final String originalOsName = System.getProperty("os.name");
    private final String originalCudaLibrary = System.getProperty(CudaAccelerationProviderFactory.LIBRARY_PROPERTY);
    private final String originalOpenClLibrary = System.getProperty(OpenClAccelerationProviderFactory.LIBRARY_PROPERTY);

    @AfterEach
    void restoreEnvironment() {
        restoreProperty("os.name", originalOsName);
        restoreProperty(CudaAccelerationProviderFactory.LIBRARY_PROPERTY, originalCudaLibrary);
        restoreProperty(OpenClAccelerationProviderFactory.LIBRARY_PROPERTY, originalOpenClLibrary);
        OpenClAccelerationProviderFactory.clearProbeCacheForTests();
    }

    @Test
    void linuxAutoSelectionAdvertisesTheSameProviderIdItQuarantinesUnder() throws Exception {
        System.setProperty("os.name", "linux");
        System.clearProperty(CudaAccelerationProviderFactory.LIBRARY_PROPERTY);
        System.clearProperty(OpenClAccelerationProviderFactory.LIBRARY_PROPERTY);
        OpenClAccelerationProviderFactory.clearProbeCacheForTests();

        Object selection = invokeProviderSelection();
        String selectionProviderId = recordValue(selection, "providerId");
        Supplier<ForecastAccelerationProvider> providerSupplier = recordValue(selection, "provider");
        ForecastAccelerationProvider composite = providerSupplier.get();

        assertThat(selectionProviderId).isEqualTo("opencl");
        // The quarantine store key and the FAILED-result provider id both derive
        // from capability().providerId(), while the quarantine lookup key derives
        // from the selection provider id. They must match.
        assertThat(composite.capability().providerId()).isEqualTo(selectionProviderId);
        assertThat(composite.capability().backend().name().toLowerCase()).isEqualTo(selectionProviderId);
    }

    private static Object invokeProviderSelection() throws Exception {
        Method selection = CliIndicatorAccelerationService.class.getDeclaredMethod("providerSelection");
        selection.setAccessible(true);
        try {
            return selection.invoke(null);
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof Exception checked) {
                throw checked;
            }
            throw exception;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T recordValue(Object record, String componentName) throws Exception {
        RecordComponent component = Arrays.stream(record.getClass().getRecordComponents())
                .filter(candidate -> candidate.getName().equals(componentName))
                .findFirst()
                .orElseThrow();
        return (T) component.getAccessor().invoke(record);
    }

    private static void restoreProperty(String property, String previous) {
        if (previous == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previous);
        }
    }
}
